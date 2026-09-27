package rayTraceTypescript.gui

import rayTraceTypescript.Camera
import rayTraceTypescript.Point
import rayTraceTypescript.gpu.GpuRenderer
import rayTraceTypescript.gpu.GpuSession
import rayTraceTypescript.gpu.SceneFlattener
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.MeshLoader
import rayTraceTypescript.render.CameraSetup
import rayTraceTypescript.render.Denoiser
import rayTraceTypescript.render.PpmWriter
import rayTraceTypescript.scenes.SceneDefinition
import java.awt.BasicStroke
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
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
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
 *  - Ctrl + left click stands the selected object on the floor under the cursor,
 *  - R resets the view, D toggles denoising, S saves the image, + / - change the field of view.
 * The panel beside the image ([ControlPanel]) sets the resolution and the sample target, and
 * adds, places and dresses models loaded from any format [MeshLoader] reads.
 *
 * While the camera moves it renders a quarter-size image at one sample per pixel, so motion
 * stays fluid; once it stops it renders the full image up to the sample target, denoising
 * every time the sample count quadruples and once more at the end.
 */
class Viewer(private val scene: SceneDefinition, private val saveFolder: Path) {
    private val base: Camera = scene.camera

    /** The camera as an orbit around [target]; everything the mouse changes. Compared by identity. */
    private class View(val target: DoubleArray, val yaw: Double, val pitch: Double, val radius: Double, val vfov: Double) {
        val from: DoubleArray
            get() = doubleArrayOf(
                target[0] + radius * cos(pitch) * sin(yaw),
                target[1] + radius * sin(pitch),
                target[2] + radius * cos(pitch) * cos(yaw)
            )

        fun with(
            target: DoubleArray = this.target, yaw: Double = this.yaw, pitch: Double = this.pitch,
            radius: Double = this.radius, vfov: Double = this.vfov
        ) = View(target, yaw, pitch, radius, vfov)
    }

    /** Output size in pixels. */
    data class Resolution(val width: Int, val height: Int) {
        val pixels: Int get() = width * height
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

    val sceneResolution = Resolution(base.imageWidth, base.initialize().imageHeight)

    @Volatile private var view = initialView
    @Volatile var resolution = sceneResolution
        set(value) { field = value; touch() }
    @Volatile var targetSamples = maxOf(base.samplesPerPixel, DEFAULT_TARGET)
    @Volatile var denoiseOn = true
    @Volatile private var lastInput = 0L
    @Volatile private var dragging = false
    @Volatile private var running = true
    @Volatile private var saveRequested = false

    /** Models added in this window; editing one bumps [sceneVersion] so the GPU copy is rebuilt. */
    val objects = CopyOnWriteArrayList<PlacedObject>()
    private val sceneVersion = AtomicInteger(0)
    @Volatile private var lastSceneEdit = 0L
    @Volatile var selected: PlacedObject? = null

    @Volatile private var shown: BufferedImage? = null
    @Volatile private var status = "Przygotowuję scenę…"
    @Volatile private var placeMarker: java.awt.Point? = null
    private val panel = ImagePanel()
    private lateinit var controls: ControlPanel

    /** A size fitting what the camera sees at the start: a quarter of the frame's height. */
    val defaultObjectHeight: Double = 0.25 * 2.0 * initialView.radius * tan(Math.toRadians(initialView.vfov / 2))

    /** Where new objects stand: the middle of the scene's footprint, on y = 0. */
    private val defaultObjectPosition: DoubleArray = scene.world.aabbBoundingBox().let { box ->
        doubleArrayOf(((box.x.min + box.x.max) / 2).toDouble(), 0.0, ((box.z.min + box.z.max) / 2).toDouble())
    }

    /** Opens the window and blocks until it is closed. */
    fun run() {
        val closed = CountDownLatch(1)
        SwingUtilities.invokeLater {
            val frame = JFrame("Ray tracer - ${scene.name}")
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            controls = ControlPanel(this, frame)
            val display = fit(sceneResolution)
            panel.preferredSize = Dimension(display.width, display.height + STATUS_HEIGHT)
            frame.contentPane.add(panel, BorderLayout.CENTER)
            frame.contentPane.add(controls, BorderLayout.EAST)
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
            // -Drt.gui.models=a.stl;b.obj opens the window with those models already placed.
            System.getProperty("rt.gui.models")?.split(';')?.filter { it.isNotBlank() }?.forEach { path ->
                addModel(File(path.trim())) { _, error ->
                    if (error != null) System.err.println("[viewer] could not load $path: $error")
                    controls.refreshObjects()
                }
            }
        }
        val renderThread = Thread({ renderLoop() }, "viewer-render").apply { start() }
        closed.await()
        renderThread.join()
    }

    /** The window's first size: the image at most 900 pixels tall and 1200 wide on screen. */
    private fun fit(r: Resolution): Dimension {
        val scale = minOf(1.0, 900.0 / r.height, 1200.0 / r.width)
        return Dimension((r.width * scale).toInt(), (r.height * scale).toInt())
    }

    // ------------------------------------------------------------------ scene edits (UI thread)

    /** Loads [file] off the UI thread, then adds it standing in the middle of the scene. */
    fun addModel(file: File, onDone: (PlacedObject?, String?) -> Unit) {
        status = "Wczytuję ${file.name}…"
        panel.repaint()
        Thread({
            try {
                val mesh = MeshLoader.load(file.toPath())
                val extension = file.extension.lowercase()
                val placed = PlacedObject(file.nameWithoutExtension, mesh).apply {
                    // Files made for 3D printing stand on z; the rest mostly use y as up.
                    zUp = extension == "stl" || extension == "3mf"
                    x = defaultObjectPosition[0]
                    y = defaultObjectPosition[1]
                    z = defaultObjectPosition[2]
                    height = defaultObjectHeight
                }
                objects += placed
                selected = placed
                sceneChanged()
                SwingUtilities.invokeLater { onDone(placed, null) }
            } catch (t: Throwable) {
                SwingUtilities.invokeLater { onDone(null, t.message ?: t.toString()) }
            }
        }, "viewer-load").apply { isDaemon = true; start() }
    }

    fun removeObject(o: PlacedObject) {
        objects -= o
        if (selected === o) selected = null
        sceneChanged()
    }

    /** Stands [o] where the middle of the view meets its floor level, or under the view's target. */
    fun centreInView(o: PlacedObject) {
        val v = view
        val hit = floorPoint(v, 0.5, 0.5, o.y) ?: doubleArrayOf(v.target[0], o.y, v.target[2])
        o.x = hit[0]
        o.z = hit[2]
        sceneChanged()
    }

    fun sceneChanged() {
        lastSceneEdit = System.currentTimeMillis()
        sceneVersion.incrementAndGet()
    }

    fun requestSave() {
        saveRequested = true
    }

    // ------------------------------------------------------------------ rendering (render thread)

    private fun buildWorld(): Hittable {
        val parts = mutableListOf<Hittable>(scene.world)
        for (o in objects) parts += HittableList(o.placedMesh().toHittables(o.finish.material()).toMutableList())
        return HittableList(parts)
    }

    private fun renderLoop() {
        var session: GpuSession? = null
        try {
            GpuRenderer.requestedDenoiser().use { denoiser ->
                loop(denoiser) { pixels ->
                    session?.close()
                    session = null
                    status = "Przygotowuję scenę (dodane obiekty: ${objects.size})…"
                    panel.repaint()
                    GpuSession(SceneFlattener.flatten(buildWorld(), base.lights), pixels).also { session = it }
                }
            }
        } catch (t: Throwable) {
            status = "Błąd: ${t.message}"
            panel.repaint()
            t.printStackTrace()
        } finally {
            session?.close()
        }
    }

    private fun loop(denoiser: Denoiser?, open: (Int) -> GpuSession) {
        var session: GpuSession? = null
        var builtVersion = -1
        var sessionPixels = 0

        var accumulated: View? = null
        var accumulatedSize: Resolution? = null
        var setup: CameraSetup? = null
        var pixels = FloatArray(0)
        var samples = 0
        var slice = 1
        var lastShown = 0L
        var nextDenoise = FIRST_DENOISE
        var guides: Pair<FloatArray, FloatArray>? = null
        var denoised: FloatArray? = null
        var finalDenoiseDone = false
        var rateSamples = 0L
        var rateStart = System.nanoTime()

        while (running) {
            val size = resolution
            val version = sceneVersion.get()
            // Rebuild the GPU copy when the objects changed (once edits pause) or the image outgrew it.
            val sceneStale = version != builtVersion && System.currentTimeMillis() - lastSceneEdit > REBUILD_DELAY_MS
            if (session == null || sceneStale || size.pixels > sessionPixels) {
                sessionPixels = maxOf(size.pixels, sceneResolution.pixels)
                session = open(sessionPixels)
                builtVersion = version
                accumulated = null
            }

            val current = view
            val moving = dragging || System.currentTimeMillis() - lastInput < SETTLE_MS
            if (moving) {
                // Quarter size, one fresh sample: rough, but it follows the mouse.
                val preview = Resolution(maxOf(size.width / PREVIEW_DIVISOR, 32), maxOf(size.height / PREVIEW_DIVISOR, 32))
                val previewSetup = setup(current, preview)
                val frameStart = System.nanoTime()
                session.setCamera(previewSetup)
                // Always from sample 0: a launch starting later adds to the buffer instead of
                // replacing it. The noise then holds still between frames, which reads better.
                session.launch(GpuSession.MODE_IMAGE, previewSetup, 0, PREVIEW_SAMPLES, 1.0f / PREVIEW_SAMPLES)
                val image = FloatArray(previewSetup.totalPixels * 3).also { session.read(GpuSession.IMAGE, it) }
                show(
                    toImage(image, previewSetup.imageWidth, previewSetup.imageHeight, 1.0f),
                    "podgląd ${previewSetup.imageWidth}×${previewSetup.imageHeight}, $PREVIEW_SAMPLES spp"
                )
                accumulated = null
                val frameMs = (System.nanoTime() - frameStart) / 1_000_000
                if (frameMs < FRAME_MS) Thread.sleep(FRAME_MS - frameMs)
                continue
            }

            if (current !== accumulated || size != accumulatedSize) {
                setup = setup(current, size)
                session.setCamera(setup)
                pixels = FloatArray(setup.totalPixels * 3)
                accumulated = current
                accumulatedSize = size
                samples = 0
                slice = 1
                nextDenoise = FIRST_DENOISE
                guides = null
                denoised = null
                finalDenoiseDone = false
                rateSamples = 0
                rateStart = System.nanoTime()
            }
            val s = setup!!
            val target = targetSamples

            if (samples < target) {
                // The sum of all samples so far; the display divides by their count.
                val count = minOf(slice, target - samples)
                val launchStart = System.nanoTime()
                session.launch(GpuSession.MODE_IMAGE, s, samples, count, 1.0f)
                val launchMs = maxOf((System.nanoTime() - launchStart) / 1_000_000.0, 1.0)
                samples += count
                rateSamples += count.toLong() * s.totalPixels
                slice = (count * LAUNCH_MS / launchMs).toInt().coerceIn(1, count * 2)
            }
            val done = samples >= target

            val now = System.nanoTime()
            val denoiseNow = denoiser != null && denoiseOn &&
                ((samples >= nextDenoise && !done) || (done && !finalDenoiseDone))
            if (denoiseNow) {
                session.read(GpuSession.IMAGE, pixels)
                val guidePair = guides ?: renderGuides(session, s).also { guides = it }
                val average = FloatArray(pixels.size) { pixels[it] / samples }
                denoised = denoiser!!.denoise(s.imageWidth, s.imageHeight, average, guidePair.first, guidePair.second)
                while (nextDenoise <= samples) nextDenoise *= 4
                if (done) finalDenoiseDone = true
                lastShown = 0
            }
            if (now - lastShown > DISPLAY_INTERVAL_NS || saveRequested) {
                session.read(GpuSession.IMAGE, pixels)
                val seconds = (now - rateStart) / 1e9
                val rate = if (seconds > 0) rateSamples / seconds / 1e6 else 0.0
                val cleanShown = denoiseOn && denoised != null
                val image = if (cleanShown) toImage(denoised!!, s.imageWidth, s.imageHeight, 1.0f)
                else toImage(pixels, s.imageWidth, s.imageHeight, 1.0f / samples)
                val denoiseNote = when {
                    denoiser == null -> "bez odszumiania (brak OIDN)"
                    !denoiseOn -> "odszumianie wył."
                    cleanShown && done -> "odszumione, gotowe"
                    cleanShown -> "odszumione"
                    else -> "odszumię przy ${minOf(nextDenoise, target)} spp"
                }
                val speed = if (done) "" else " · %.1f M próbek/s".format(rate)
                show(image, "${s.imageWidth}×${s.imageHeight} · $samples / $target spp$speed · $denoiseNote")
                SwingUtilities.invokeLater { if (::controls.isInitialized) controls.showProgress(samples, target) }
                lastShown = now
                if (saveRequested) save(s, pixels, samples, if (cleanShown) denoised else null)
            }
            if (done) Thread.sleep(FRAME_MS)
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
        status = "Zapisano ${path.fileName} ($samples spp)"
        System.err.println("[viewer] saved $path ($samples spp)")
    }

    /** The scene's own camera, moved to [view] and sized to [size]. */
    private fun setup(view: View, size: Resolution): CameraSetup {
        val from = view.from
        val camera = Camera().apply {
            // A quarter pixel over the height keeps the camera's floor(width / aspect) at size.height.
            aspectRatio = size.width / (size.height + 0.25f)
            imageWidth = size.width
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

    /**
     * Where the ray through the image point ([fx], [fy], as fractions of its width and height)
     * meets the horizontal plane y = [floorY]; null when it points away from it.
     */
    private fun floorPoint(v: View, fx: Double, fy: Double, floorY: Double): DoubleArray? {
        val size = resolution
        val s = setup(v, size)
        val px = fx * size.width
        val py = fy * size.height
        val point = doubleArrayOf(
            (s.pixel00.x + s.pixelDeltaU.x * px + s.pixelDeltaV.x * py),
            (s.pixel00.y + s.pixelDeltaU.y * px + s.pixelDeltaV.y * py),
            (s.pixel00.z + s.pixelDeltaU.z * px + s.pixelDeltaV.z * py)
        )
        val origin = doubleArrayOf(s.cameraCenter.x.toDouble(), s.cameraCenter.y.toDouble(), s.cameraCenter.z.toDouble())
        val direction = DoubleArray(3) { point[it] - origin[it] }
        if (kotlin.math.abs(direction[1]) < 1e-9) return null
        val t = (floorY - origin[1]) / direction[1]
        if (t <= 0) return null
        return DoubleArray(3) { origin[it] + t * direction[it] }
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

    // ------------------------------------------------------------------ mouse and keys

    private fun installControls(frame: JFrame) {
        val mouse = object : MouseAdapter() {
            private var lastX = 0
            private var lastY = 0
            private var placing = false

            override fun mousePressed(e: MouseEvent) {
                panel.requestFocusInWindow()
                lastX = e.x
                lastY = e.y
                placing = SwingUtilities.isLeftMouseButton(e) && e.isControlDown && selected != null
                if (placing) {
                    placeMarker = e.point
                    panel.repaint()
                    return
                }
                dragging = true
                touch()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (placing) {
                    placing = false
                    placeMarker = null
                    place(e.x, e.y)
                    return
                }
                dragging = false
                touch()
            }

            override fun mouseDragged(e: MouseEvent) {
                if (placing) {
                    placeMarker = e.point
                    panel.repaint()
                    return
                }
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x
                lastY = e.y
                val v = view
                view = when {
                    SwingUtilities.isMiddleMouseButton(e) || (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown) -> pan(v, dx, dy)
                    SwingUtilities.isRightMouseButton(e) -> v.with(radius = dolly(v.radius, dy * DRAG_ZOOM))
                    else -> v.with(
                        yaw = v.yaw - dx * ORBIT_SPEED,
                        pitch = (v.pitch + dy * ORBIT_SPEED).coerceIn(-MAX_PITCH, MAX_PITCH)
                    )
                }
                touch()
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                view = view.with(radius = dolly(view.radius, e.preciseWheelRotation))
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
                    KeyEvent.VK_D -> { denoiseOn = !denoiseOn; controls.syncDenoise() }
                    KeyEvent.VK_S -> requestSave()
                    KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> { view = view.with(vfov = (view.vfov - 2).coerceAtLeast(5.0)); touch() }
                    KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> { view = view.with(vfov = (view.vfov + 2).coerceAtMost(120.0)); touch() }
                    KeyEvent.VK_DELETE -> selected?.let { removeObject(it); controls.refreshObjects() }
                    KeyEvent.VK_ESCAPE -> frame.dispose()
                }
            }
        })
    }

    /** Ctrl + click: the selected object moves to the floor under the cursor. */
    private fun place(mouseX: Int, mouseY: Int) {
        val o = selected ?: return
        val r = panel.imageBounds()
        if (r.width <= 0 || r.height <= 0) return
        val fx = (mouseX - r.x).toDouble() / r.width
        val fy = (mouseY - r.y).toDouble() / r.height
        if (fx !in 0.0..1.0 || fy !in 0.0..1.0) return
        val hit = floorPoint(view, fx, fy, o.y) ?: return
        o.x = hit[0]
        o.z = hit[2]
        sceneChanged()
        controls.refreshSelection()
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
        return v.with(target = target)
    }

    private fun dolly(radius: Double, steps: Double): Double = (radius * Math.pow(ZOOM_STEP, steps)).coerceIn(1.0, 1e5)

    /** Draws the latest image, scaled to fit, the placement marker, and the status line. */
    private inner class ImagePanel : JPanel() {
        init {
            background = Color(0x131211)
            isFocusable = true
        }

        /** Where the image goes: as large as fits above the status line, proportions kept. */
        fun imageBounds(): java.awt.Rectangle {
            val available = (height - STATUS_HEIGHT).coerceAtLeast(1)
            val aspect = resolution.width.toDouble() / resolution.height
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
            placeMarker?.let { p ->
                g2.color = Color(0xE8C547)
                g2.stroke = BasicStroke(2f)
                g2.drawOval(p.x - 10, p.y - 10, 20, 20)
                g2.drawLine(p.x - 16, p.y, p.x + 16, p.y)
                g2.drawLine(p.x, p.y - 16, p.x, p.y + 16)
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
        /** How long after an object edit the scene is rebuilt, so a run of edits costs one rebuild. */
        private const val REBUILD_DELAY_MS = 250L
        private const val FRAME_MS = 16L
        private const val LAUNCH_MS = 40.0
        private const val DISPLAY_INTERVAL_NS = 100_000_000L
        private const val DEFAULT_TARGET = 1024
        private const val FIRST_DENOISE = 16
        private const val GUIDE_SAMPLES = 16
        private const val ORBIT_SPEED = 0.005
        private const val MAX_PITCH = 1.55
        private const val ZOOM_STEP = 1.1
        private const val DRAG_ZOOM = 0.02
        private const val STATUS_HEIGHT = 44
        private const val HELP =
            "LPM obrót · Shift+LPM / ŚPM przesuwanie · kółko / PPM zoom · Ctrl+LPM postaw obiekt · R reset · D odszum. · S zapis · Del usuń"
    }
}
