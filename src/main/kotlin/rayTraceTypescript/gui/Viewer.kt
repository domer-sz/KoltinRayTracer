package rayTraceTypescript.gui

import rayTraceTypescript.Camera
import rayTraceTypescript.Point
import rayTraceTypescript.gpu.GpuRenderer
import rayTraceTypescript.gpu.GpuSession
import rayTraceTypescript.gpu.SceneFlattener
import rayTraceTypescript.render.CameraSetup
import rayTraceTypescript.render.Denoiser
import rayTraceTypescript.render.PpmWriter
import rayTraceTypescript.scenes.SceneDefinition
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * An interactive window onto a scene, steered like a 3D modelling program:
 *  - left drag orbits the camera around the point it looks at,
 *  - Shift + left drag, or middle drag, pans,
 *  - the wheel, or right drag, moves the camera in and out,
 *  - R resets the view, D toggles denoising, S saves the image, + / - change the field of view.
 *
 * While the camera moves it renders a quarter-size image at one sample per pixel, so motion
 * stays fluid; once it stops it renders the full image, adding samples for as long as the view
 * holds still, and (with D on) denoises it every time the sample count quadruples.
 */
class Viewer(private val scene: SceneDefinition, private val saveFolder: Path) {
    private val base: Camera = scene.camera

    /** The camera as an orbit around [target]; everything the mouse changes. Compared by identity. */
    private data class View(
        val target: DoubleArray, val yaw: Double, val pitch: Double, val radius: Double, val vfov: Double
    ) {
        val from: DoubleArray
            get() = doubleArrayOf(
                target[0] + radius * cos(pitch) * sin(yaw),
                target[1] + radius * sin(pitch),
                target[2] + radius * cos(pitch) * cos(yaw)
            )
    }

    private val initialView: View = run {
        val from = base.lookFrom
        val at = base.lookAt
        val dx = (from.x - at.x).toDouble()
        val dy = (from.y - at.y).toDouble()
        val dz = (from.z - at.z).toDouble()
        val radius = sqrt(dx * dx + dy * dy + dz * dz)
        View(doubleArrayOf(at.x.toDouble(), at.y.toDouble(), at.z.toDouble()), atan2(dx, dz), asin(dy / radius), radius, base.vfov.toDouble())
    }

    @Volatile private var view = initialView
    @Volatile private var lastInput = 0L
    @Volatile private var dragging = false
    @Volatile private var running = true
    @Volatile private var denoiseOn = true
    @Volatile private var saveRequested = false

    @Volatile private var shown: BufferedImage? = null
    @Volatile private var status = "Przygotowuję scenę…"
    private val panel = ImagePanel()

    /** Opens the window and blocks until it is closed. */
    fun run() {
        val closed = CountDownLatch(1)
        val fullWidth = base.imageWidth
        val fullSetup = setup(initialView, fullWidth)
        SwingUtilities.invokeLater {
            val frame = JFrame("Ray tracer - ${scene.name}")
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            panel.preferredSize = Dimension(fullSetup.imageWidth, fullSetup.imageHeight + STATUS_HEIGHT)
            frame.contentPane.add(panel, BorderLayout.CENTER)
            frame.pack()
            frame.setLocationRelativeTo(null)
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    running = false
                    closed.countDown()
                }
            })
            installControls(frame)
            frame.isVisible = true
            // Windows does not let a new window take the foreground on its own; a moment on top does.
            frame.isAlwaysOnTop = true
            frame.toFront()
            frame.isAlwaysOnTop = false
            panel.requestFocusInWindow()
        }
        val renderThread = Thread({ renderLoop(fullWidth) }, "viewer-render").apply { start() }
        closed.await()
        renderThread.join()
    }

    private fun renderLoop(fullWidth: Int) {
        try {
            val flattened = SceneFlattener.flatten(scene.world, base.lights)
            val denoiser = GpuRenderer.requestedDenoiser()
            val full = setup(view, fullWidth)
            GpuSession(flattened, full.totalPixels).use { session ->
                denoiser.use { loop(session, it, fullWidth) }
            }
        } catch (t: Throwable) {
            status = "Błąd: ${t.message}"
            panel.repaint()
            t.printStackTrace()
        }
    }

    private fun loop(session: GpuSession, denoiser: Denoiser?, fullWidth: Int) {
        val previewWidth = maxOf(fullWidth / PREVIEW_DIVISOR, 64)
        var accumulated: View? = null
        var fullSetup: CameraSetup? = null
        var pixels = FloatArray(0)
        var samples = 0
        var slice = 1
        var lastShown = 0L
        var nextDenoise = FIRST_DENOISE
        var guides: Pair<FloatArray, FloatArray>? = null
        var denoised: FloatArray? = null
        var rateSamples = 0L
        var rateStart = System.nanoTime()

        while (running) {
            val current = view
            val moving = dragging || System.currentTimeMillis() - lastInput < SETTLE_MS
            if (moving) {
                // Quarter size, one fresh sample: rough, but it follows the mouse.
                val setup = setup(current, previewWidth)
                val frameStart = System.nanoTime()
                session.setCamera(setup)
                // Always from sample 0: a launch starting later adds to the buffer instead of
                // replacing it. The noise then holds still between frames, which reads better.
                session.launch(GpuSession.MODE_IMAGE, setup, 0, PREVIEW_SAMPLES, 1.0f / PREVIEW_SAMPLES)
                val preview = FloatArray(setup.totalPixels * 3).also { session.read(GpuSession.IMAGE, it) }
                show(toImage(preview, setup.imageWidth, setup.imageHeight, 1.0f), "podgląd ${setup.imageWidth}×${setup.imageHeight}, $PREVIEW_SAMPLES spp")
                accumulated = null
                val frameMs = (System.nanoTime() - frameStart) / 1_000_000
                if (frameMs < FRAME_MS) Thread.sleep(FRAME_MS - frameMs)
                continue
            }

            if (current !== accumulated) {
                fullSetup = setup(current, fullWidth)
                session.setCamera(fullSetup)
                pixels = FloatArray(fullSetup.totalPixels * 3)
                accumulated = current
                samples = 0
                slice = 1
                nextDenoise = FIRST_DENOISE
                guides = null
                denoised = null
                rateSamples = 0
                rateStart = System.nanoTime()
            }
            val setup = fullSetup!!
            if (samples >= MAX_SAMPLES) {
                if (saveRequested) save(setup, pixels, samples, denoised)
                Thread.sleep(FRAME_MS)
                continue
            }

            // The sum of all samples so far; the display divides by their count.
            val launchStart = System.nanoTime()
            session.launch(GpuSession.MODE_IMAGE, setup, samples, slice, 1.0f)
            val launchMs = maxOf((System.nanoTime() - launchStart) / 1_000_000.0, 1.0)
            samples += slice
            rateSamples += slice.toLong() * setup.totalPixels
            slice = (slice * LAUNCH_MS / launchMs).toInt().coerceIn(1, slice * 2)

            val now = System.nanoTime()
            if (denoiser != null && denoiseOn && samples >= nextDenoise) {
                session.read(GpuSession.IMAGE, pixels)
                val guidePair = guides ?: renderGuides(session, setup).also { guides = it }
                val average = FloatArray(pixels.size) { pixels[it] / samples }
                denoised = denoiser.denoise(setup.imageWidth, setup.imageHeight, average, guidePair.first, guidePair.second)
                nextDenoise *= 4
                lastShown = 0
            }
            if (now - lastShown > DISPLAY_INTERVAL_NS || saveRequested) {
                session.read(GpuSession.IMAGE, pixels)
                val seconds = (now - rateStart) / 1e9
                val rate = if (seconds > 0) rateSamples / seconds / 1e6 else 0.0
                val cleanShown = denoiseOn && denoised != null
                val image = if (cleanShown) toImage(denoised!!, setup.imageWidth, setup.imageHeight, 1.0f)
                else toImage(pixels, setup.imageWidth, setup.imageHeight, 1.0f / samples)
                val denoiseNote = when {
                    denoiser == null -> "bez odszumiania (brak OIDN)"
                    !denoiseOn -> "odszumianie wył."
                    cleanShown -> "odszumione"
                    else -> "odszumię przy $nextDenoise spp"
                }
                show(image, "$samples spp · %.1f M próbek/s · $denoiseNote".format(rate))
                lastShown = now
                if (saveRequested) save(setup, pixels, samples, if (cleanShown) denoised else null)
            }
        }
    }

    /** The denoiser's albedo and normal guides for the current view, averaged over a few samples. */
    private fun renderGuides(session: GpuSession, setup: CameraSetup): Pair<FloatArray, FloatArray> {
        fun guide(mode: Int): FloatArray {
            session.launch(mode, setup, 0, GUIDE_SAMPLES, 1.0f / GUIDE_SAMPLES, GpuSession.GUIDE)
            return FloatArray(setup.totalPixels * 3).also { session.read(GpuSession.GUIDE, it) }
        }
        return guide(GpuSession.MODE_ALBEDO) to guide(GpuSession.MODE_NORMAL)
    }

    private fun save(setup: CameraSetup, sums: FloatArray, samples: Int, denoised: FloatArray?) {
        saveRequested = false
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val path = saveFolder.resolve("viewer-${scene.name}-$stamp.ppm")
        PpmWriter.write(path, setup.imageWidth, setup.imageHeight, FloatArray(sums.size) { sums[it] / samples })
        if (denoised != null) {
            PpmWriter.write(path.resolveSibling("viewer-${scene.name}-$stamp-denoised.ppm"), setup.imageWidth, setup.imageHeight, denoised)
        }
        System.err.println("[viewer] saved $path ($samples spp)")
    }

    /** The scene's own camera, moved to [view] and sized [width] pixels across. */
    private fun setup(view: View, width: Int): CameraSetup {
        val from = view.from
        val camera = Camera().apply {
            aspectRatio = base.aspectRatio
            imageWidth = width
            samplesPerPixel = base.samplesPerPixel
            maxReflectionDepth = base.maxReflectionDepth
            vfov = view.vfov.toFloat()
            lookFrom = Point(from[0].toFloat(), from[1].toFloat(), from[2].toFloat())
            lookAt = Point(view.target[0].toFloat(), view.target[1].toFloat(), view.target[2].toFloat())
            vUp = base.vUp
            defocusAngle = base.defocusAngle
            // Keep what the camera looks at in focus when there is depth of field.
            focusDistance = if (base.defocusAngle > 0f) view.radius.toFloat() else base.focusDistance
            background = base.background
            lights = base.lights
        }
        return camera.initialize()
    }

    private fun show(image: BufferedImage, text: String) {
        shown = image
        status = text
        panel.repaint()
    }

    /** Linear values times [scale] -> sRGB-ish 8 bit, the same sqrt gamma and clamp as the files. */
    private fun toImage(linear: FloatArray, width: Int, height: Int, scale: Float): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = (image.raster.dataBuffer as DataBufferInt).data
        for (pixel in 0 until width * height) {
            var rgb = 0
            for (channel in 0 until 3) {
                val value = linear[pixel * 3 + channel] * scale
                val gamma = if (value > 0f) sqrt(value).coerceAtMost(0.999f) else 0f
                rgb = (rgb shl 8) or (256f * gamma).toInt()
            }
            out[pixel] = rgb
        }
        return image
    }

    private fun installControls(frame: JFrame) {
        val mouse = object : MouseAdapter() {
            private var lastX = 0
            private var lastY = 0

            override fun mousePressed(e: MouseEvent) {
                lastX = e.x
                lastY = e.y
                dragging = true
                panel.requestFocusInWindow()
                touch()
            }

            override fun mouseReleased(e: MouseEvent) {
                dragging = false
                touch()
            }

            override fun mouseDragged(e: MouseEvent) {
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x
                lastY = e.y
                val v = view
                view = when {
                    SwingUtilities.isMiddleMouseButton(e) || (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown) -> pan(v, dx, dy)
                    SwingUtilities.isRightMouseButton(e) -> v.copy(radius = dolly(v.radius, dy * DRAG_ZOOM))
                    else -> v.copy(
                        yaw = v.yaw - dx * ORBIT_SPEED,
                        pitch = (v.pitch + dy * ORBIT_SPEED).coerceIn(-MAX_PITCH, MAX_PITCH)
                    )
                }
                touch()
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                view = view.copy(radius = dolly(view.radius, e.preciseWheelRotation))
                touch()
            }
        }
        panel.addMouseListener(mouse)
        panel.addMouseMotionListener(mouse)
        panel.addMouseWheelListener(mouse)

        panel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_R -> { view = initialView; touch() }
                    KeyEvent.VK_D -> { denoiseOn = !denoiseOn; lastInput = 0 }
                    KeyEvent.VK_S -> saveRequested = true
                    KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> { view = view.copy(vfov = (view.vfov - 2).coerceAtLeast(5.0)); touch() }
                    KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> { view = view.copy(vfov = (view.vfov + 2).coerceAtMost(120.0)); touch() }
                    KeyEvent.VK_ESCAPE -> frame.dispose()
                }
            }
        })
    }

    private fun touch() {
        lastInput = System.currentTimeMillis()
    }

    /** Moves target and camera together so the point under the cursor follows it. */
    private fun pan(v: View, dx: Int, dy: Int): View {
        val w = doubleArrayOf(cos(v.pitch) * sin(v.yaw), sin(v.pitch), cos(v.pitch) * cos(v.yaw))
        // u = up x w, v = w x u: the screen's right and up directions in the world.
        val ux = w[2]
        val uz = -w[0]
        val uLength = sqrt(ux * ux + uz * uz).coerceAtLeast(1e-9)
        val u = doubleArrayOf(ux / uLength, 0.0, uz / uLength)
        val up = doubleArrayOf(w[1] * u[2] - w[2] * u[1], w[2] * u[0] - w[0] * u[2], w[0] * u[1] - w[1] * u[0])
        val shownHeight = panel.imageBounds().height.coerceAtLeast(1)
        val unitsPerPixel = 2.0 * v.radius * tan(Math.toRadians(v.vfov / 2)) / shownHeight
        val target = DoubleArray(3) { v.target[it] + (-dx * u[it] + dy * up[it]) * unitsPerPixel }
        return v.copy(target = target)
    }

    private fun dolly(radius: Double, steps: Double): Double = (radius * Math.pow(ZOOM_STEP, steps)).coerceIn(1.0, 1e5)

    /** Draws the latest image, scaled to fit, and the status line under it. */
    private inner class ImagePanel : JPanel() {
        init {
            background = Color(0x131211)
            isFocusable = true
        }

        /** Where the image goes: as large as fits above the status line, proportions kept. */
        fun imageBounds(): java.awt.Rectangle {
            val available = (height - STATUS_HEIGHT).coerceAtLeast(1)
            val image = shown ?: return java.awt.Rectangle(0, 0, width, available)
            // The preview is smaller than the full image but has its proportions.
            val aspect = image.width.toDouble() / image.height
            val w = minOf(width.toDouble(), available * aspect)
            val h = w / aspect
            return java.awt.Rectangle(((width - w) / 2).toInt(), ((available - h) / 2).toInt(), w.toInt(), h.toInt())
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            shown?.let { image ->
                val r = imageBounds()
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.drawImage(image, r.x, r.y, r.width, r.height, null)
            }
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g2.color = Color(0xECE8E3)
            g2.drawString(status, 10, height - 28)
            g2.color = Color(0x9B948C)
            g2.drawString(HELP, 10, height - 10)
        }
    }

    companion object {
        private const val PREVIEW_DIVISOR = 4
        private const val PREVIEW_SAMPLES = 1
        /** How long after the last mouse or key move the full-size render takes over. */
        private const val SETTLE_MS = 150L
        private const val FRAME_MS = 16L
        private const val LAUNCH_MS = 40.0
        private const val DISPLAY_INTERVAL_NS = 100_000_000L
        private const val MAX_SAMPLES = 1 shl 16
        private const val FIRST_DENOISE = 16
        private const val GUIDE_SAMPLES = 16
        private const val ORBIT_SPEED = 0.005
        private const val MAX_PITCH = 1.55
        private const val ZOOM_STEP = 1.1
        private const val DRAG_ZOOM = 0.02
        private const val STATUS_HEIGHT = 44
        private const val HELP =
            "LPM obrót · Shift+LPM / ŚPM przesuwanie · kółko / PPM zoom · R reset · D odszumianie · S zapis · +/- kąt · Esc"
    }
}
