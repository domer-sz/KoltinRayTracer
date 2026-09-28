package rayTraceTypescript.gui

import rayTraceTypescript.Camera
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
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
 * It opens in the quick look for setting the scene up: one ray per pixel, shaded by the
 * surface's colour, at full size and sharp at once. T switches to ray tracing: while the camera
 * moves it renders a quarter-size image at one sample per pixel, so motion stays fluid; once it
 * stops it renders the full image up to the sample target, denoising every time the sample
 * count quadruples and once more at the end.
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
    /**
     * Ray tracing, or (the default, for setting the scene up) the quick look: one ray per pixel
     * shaded by the surface's colour, sharp at once. -Drt.gui.raytrace opens in ray tracing.
     */
    @Volatile var raytrace = System.getProperty("rt.gui.raytrace") != null
    @Volatile private var lastInput = 0L
    @Volatile private var dragging = false
    @Volatile private var running = true
    @Volatile private var saveRequested = false

    /** Models added in this window; editing one bumps [sceneVersion] so the GPU copy is rebuilt. */
    val objects = CopyOnWriteArrayList<PlacedObject>()
    private val sceneVersion = AtomicInteger(0)
    @Volatile private var lastSceneEdit = 0L
    /** The scene's own switchable lights and removable objects. */
    val builtIns: List<BuiltInObject> = scene.parts.map { BuiltInObject(it) }
    val lights: List<BuiltInObject> get() = builtIns.filter { it.part.isLight }
    @Volatile var selected: SceneEntry? = null
    /** The selection when it is an added model - the only kind that can be moved. */
    val selectedModel: PlacedObject? get() = selected as? PlacedObject

    /** What the object list shows: the scene's objects still in it, then the added models. */
    fun entries(): List<SceneEntry> = builtIns.filter { !it.part.isLight && it.enabled } + objects

    @Volatile private var shown: BufferedImage? = null
    @Volatile private var status = "Przygotowuję scenę…"
    @Volatile private var placeMarker: java.awt.Point? = null
    /** Where a Ctrl-dragged model would stand, while the button is held. */
    @Volatile private var ghost: DoubleArray? = null
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
            // The panel scrolls when the window is shorter than its fields.
            frame.contentPane.add(javax.swing.JScrollPane(controls).apply {
                horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                border = null
                verticalScrollBar.unitIncrement = 16
            }, BorderLayout.EAST)
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
                val placed = PlacedObject(file.nameWithoutExtension, Shape.Model(mesh)).apply {
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

    /** Takes [entry] out: an added model for good, one of the scene's own until [restoreBuiltIns]. */
    /**
     * Adds a sphere (glass, like the scene's) or a block (white, shaped like the column: twice
     * as tall as wide) in the middle of the scene, sized like a newly loaded model.
     */
    fun addShape(shape: Shape): PlacedObject {
        val ball = shape == Shape.Ball
        val number = objects.count { it.shape === shape } + 1
        val placed = PlacedObject(if (ball) "Kula $number" else "Graniastosłup $number", shape).apply {
            x = defaultObjectPosition[0]
            y = defaultObjectPosition[1]
            z = defaultObjectPosition[2]
            height = defaultObjectHeight
            width = defaultObjectHeight / 2
            depth = defaultObjectHeight / 2
            material = if (ball) MaterialPreset.GLASS.spec else MaterialPreset.WHITE.spec
        }
        objects += placed
        selected = placed
        sceneChanged()
        return placed
    }

    fun removeObject(entry: SceneEntry) {
        when (entry) {
            is PlacedObject -> objects -= entry
            is BuiltInObject -> entry.enabled = false
        }
        if (selected === entry) selected = null
        sceneChanged()
    }

    /** Puts every removed object of the scene back (lights keep their switches). */
    fun restoreBuiltIns() {
        builtIns.filter { !it.part.isLight }.forEach { it.enabled = true }
        sceneChanged()
    }

    fun setLight(light: BuiltInObject, on: Boolean) {
        light.enabled = on
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

    /**
     * The scene as it now stands: the room without any of its parts, the parts that are
     * switched on, and the added models.
     */
    private fun buildWorld(): Hittable {
        val parts = mutableListOf<Hittable>(sceneWithoutModels())
        for (o in objects) parts += o.hittable()
        return HittableList(parts)
    }

    /** The room and the parts of the scene switched on - everything but the added models. */
    private fun sceneWithoutModels(): Hittable {
        val world = scene.world
        val all = builtIns.map { it.part.hittable }
        val room = if (world is HittableList && all.isNotEmpty()) {
            HittableList(world.objects.filter { o -> all.none { it === o } }.toMutableList())
        } else world
        val parts = mutableListOf<Hittable>(room)
        builtIns.filter { it.enabled }.forEach { parts += it.part.hittable }
        return HittableList(parts)
    }

    /**
     * The lights to aim samples at: the camera's list without the stand-ins of any part, plus
     * those of the parts switched on. Null when nothing is left to aim at.
     */
    private fun activeLights(): Hittable? {
        val proxies = builtIns.mapNotNull { it.part.lightProxy }
        fun strip(h: Hittable): Hittable? = when {
            proxies.any { it === h } -> null
            h is HittableList -> h.objects.mapNotNull { strip(it) }.takeIf { it.isNotEmpty() }?.let { HittableList(it.toMutableList()) }
            else -> h
        }
        val kept = listOfNotNull(base.lights?.let { strip(it) }) +
            builtIns.filter { it.enabled }.mapNotNull { it.part.lightProxy }
        return kept.takeIf { it.isNotEmpty() }?.let { HittableList(it.toMutableList()) }
    }

    private fun renderLoop() {
        var session: GpuSession? = null
        var capacity = 0
        try {
            GpuRenderer.requestedDenoiser().use { denoiser ->
                loop(denoiser) { pixels ->
                    val start = System.nanoTime()
                    val buffers = SceneFlattener.flatten(buildWorld(), activeLights())
                    val flattened = System.nanoTime()
                    val current = session
                    // An edited scene goes into the session there is; a bigger image needs a new one.
                    val next = if (current != null && pixels <= capacity) {
                        current.also { it.replaceScene(buffers) }
                    } else {
                        current?.close()
                        session = null
                        GpuSession(buffers, pixels).also {
                            session = it
                            capacity = pixels
                        }
                    }
                    System.err.println("[viewer] scene rebuilt: flatten ${(flattened - start) / 1_000_000} ms, upload ${(System.nanoTime() - flattened) / 1_000_000} ms")
                    next
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
        var accumulatedRaytrace = false
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
            if (!raytrace) {
                // The quick look: full size from the first frame, a few jittered rays per pixel
                // added while the view holds still to smooth the edges, then idle.
                if (current !== accumulated || size != accumulatedSize || accumulatedRaytrace) {
                    setup = setup(current, size)
                    session.setCamera(setup)
                    pixels = FloatArray(setup.totalPixels * 3)
                    accumulated = current
                    accumulatedSize = size
                    accumulatedRaytrace = false
                    samples = 0
                }
                val s = setup!!
                if (samples < QUICK_SAMPLES || saveRequested) {
                    val frameStart = System.nanoTime()
                    if (samples < QUICK_SAMPLES) {
                        session.launch(GpuSession.MODE_SHADED, s, samples, 1, 1.0f)
                        samples++
                    }
                    session.read(GpuSession.IMAGE, pixels)
                    show(
                        toImage(pixels, s.imageWidth, s.imageHeight, 1.0f / samples),
                        "szybki podgląd ${s.imageWidth}×${s.imageHeight} · T: ray tracing"
                    )
                    val shown = samples
                    SwingUtilities.invokeLater { if (::controls.isInitialized) controls.showProgress(shown, QUICK_SAMPLES) }
                    if (saveRequested) save(s, pixels, samples, null)
                    val frameMs = (System.nanoTime() - frameStart) / 1_000_000
                    if (frameMs < FRAME_MS) Thread.sleep(FRAME_MS - frameMs)
                } else {
                    Thread.sleep(FRAME_MS)
                }
                continue
            }

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

            if (current !== accumulated || size != accumulatedSize || !accumulatedRaytrace) {
                setup = setup(current, size)
                session.setCamera(setup)
                pixels = FloatArray(setup.totalPixels * 3)
                accumulated = current
                accumulatedSize = size
                accumulatedRaytrace = true
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
        val (origin, direction) = cursorRay(v, fx, fy)
        if (kotlin.math.abs(direction[1]) < 1e-9) return null
        val t = (floorY - origin[1]) / direction[1]
        if (t <= 0) return null
        return DoubleArray(3) { origin[it] + t * direction[it] }
    }

    /** The camera's ray through the image point ([fx], [fy], as fractions of its width and height). */
    private fun cursorRay(v: View, fx: Double, fy: Double): Pair<DoubleArray, DoubleArray> {
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
        return origin to DoubleArray(3) { point[it] - origin[it] }
    }

    /**
     * Where [model] would stand if dropped under the panel point ([mouseX], [mouseY]): on the
     * first surface the ray meets - the scene's own geometry, or the box around another added
     * model - when that surface faces up (a floor, a block's top, a sphere's crown); on the floor
     * (y = 0) under the cursor when it is a wall or a side. Null outside the image.
     */
    private fun dropPoint(model: PlacedObject, mouseX: Int, mouseY: Int): DoubleArray? {
        val r = panel.imageBounds()
        if (r.width <= 0 || r.height <= 0) return null
        val fx = (mouseX - r.x).toDouble() / r.width
        val fy = (mouseY - r.y).toDouble() / r.height
        if (fx !in 0.0..1.0 || fy !in 0.0..1.0) return null
        val v = view
        val (origin, direction) = cursorRay(v, fx, fy)

        var bestT = Double.POSITIVE_INFINITY
        // The hit surface's normal, which way it faces.
        var normal = doubleArrayOf(0.0, 1.0, 0.0)
        val ray = Ray(
            Point(origin[0].toFloat(), origin[1].toFloat(), origin[2].toFloat()),
            Vector(direction[0].toFloat(), direction[1].toFloat(), direction[2].toFloat())
        )
        sceneWithoutModels().hit(ray, Interval(0.001f, Float.POSITIVE_INFINITY))?.let { hit ->
            bestT = hit.t.toDouble()
            val length = sqrt(hit.normal.x * hit.normal.x + hit.normal.y * hit.normal.y + hit.normal.z * hit.normal.z)
            if (length > 0) normal = doubleArrayOf((hit.normal.x / length).toDouble(), (hit.normal.y / length).toDouble(), (hit.normal.z / length).toDouble())
        }
        for (other in objects) {
            if (other === model) continue
            val b = other.localBounds()
            val min = doubleArrayOf(other.x + b[0], other.y + b[1], other.z + b[2])
            val max = doubleArrayOf(other.x + b[3], other.y + b[4], other.z + b[5])
            boxHit(origin, direction, min, max)?.let { (t, faceNormal) ->
                if (t < bestT) {
                    bestT = t
                    normal = faceNormal
                }
            }
        }

        if (!bestT.isFinite()) return floorPoint(v, fx, fy, 0.0)
        val hit = DoubleArray(3) { origin[it] + bestT * direction[it] }
        if (normal[1] > UPWARD) return hit

        // A wall or a side: stand at its foot on the floor, moved out along it by half the
        // model's footprint so the two do not overlap.
        val b = model.localBounds()
        val halfFootprint = maxOf(-b[0], b[3], -b[2], b[5])
        val flat = sqrt(normal[0] * normal[0] + normal[2] * normal[2]).coerceAtLeast(1e-9)
        return doubleArrayOf(hit[0] + normal[0] / flat * halfFootprint, 0.0, hit[2] + normal[2] / flat * halfFootprint)
    }

    /** Entry distance of a ray into a box and the normal of the face it enters by; null on a miss. */
    private fun boxHit(origin: DoubleArray, direction: DoubleArray, min: DoubleArray, max: DoubleArray): Pair<Double, DoubleArray>? {
        var near = 0.0
        var far = Double.POSITIVE_INFINITY
        var nearAxis = -1
        for (a in 0 until 3) {
            if (kotlin.math.abs(direction[a]) < 1e-12) {
                if (origin[a] < min[a] || origin[a] > max[a]) return null
                continue
            }
            var t0 = (min[a] - origin[a]) / direction[a]
            var t1 = (max[a] - origin[a]) / direction[a]
            if (t0 > t1) t0 = t1.also { t1 = t0 }
            if (t0 > near) {
                near = t0
                nearAxis = a
            }
            if (t1 < far) far = t1
            if (far < near) return null
        }
        // The entry face is across the axis entered last, facing back towards the ray.
        val normal = DoubleArray(3)
        if (nearAxis >= 0) normal[nearAxis] = if (direction[nearAxis] > 0) -1.0 else 1.0
        return near to normal
    }

    /** Where a world point lands in the image, in image pixels; null when it is behind the camera. */
    private fun project(s: CameraSetup, p: DoubleArray): DoubleArray? {
        val c = doubleArrayOf(s.cameraCenter.x.toDouble(), s.cameraCenter.y.toDouble(), s.cameraCenter.z.toDouble())
        val du = doubleArrayOf(s.pixelDeltaU.x.toDouble(), s.pixelDeltaU.y.toDouble(), s.pixelDeltaU.z.toDouble())
        val dv = doubleArrayOf(s.pixelDeltaV.x.toDouble(), s.pixelDeltaV.y.toDouble(), s.pixelDeltaV.z.toDouble())
        val p00 = doubleArrayOf(s.pixel00.x.toDouble(), s.pixel00.y.toDouble(), s.pixel00.z.toDouble())
        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        // The image plane holds pixel00 and is spanned by the pixel steps; meet it on the way to p.
        val normal = doubleArrayOf(du[1] * dv[2] - du[2] * dv[1], du[2] * dv[0] - du[0] * dv[2], du[0] * dv[1] - du[1] * dv[0])
        val d = DoubleArray(3) { p[it] - c[it] }
        val denominator = dot(normal, d)
        if (kotlin.math.abs(denominator) < 1e-12) return null
        val t = dot(normal, DoubleArray(3) { p00[it] - c[it] }) / denominator
        if (t <= 0) return null
        val onPlane = DoubleArray(3) { c[it] + t * d[it] - p00[it] }
        // pixel00 is the centre of the first pixel, half a pixel in from the corner.
        return doubleArrayOf(dot(onPlane, du) / dot(du, du) + 0.5, dot(onPlane, dv) / dot(dv, dv) + 0.5)
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
                placing = SwingUtilities.isLeftMouseButton(e) && e.isControlDown && selectedModel != null
                if (placing) {
                    updateGhost(e.x, e.y)
                    return
                }
                dragging = true
                touch()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (placing) {
                    placing = false
                    updateGhost(e.x, e.y)
                    dropGhost()
                    return
                }
                dragging = false
                touch()
            }

            override fun mouseDragged(e: MouseEvent) {
                if (placing) {
                    updateGhost(e.x, e.y)
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
                    KeyEvent.VK_T -> { raytrace = !raytrace; controls.syncMode() }
                    KeyEvent.VK_S -> requestSave()
                    KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> { view = view.with(vfov = (view.vfov - 2).coerceAtLeast(5.0)); touch() }
                    KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> { view = view.with(vfov = (view.vfov + 2).coerceAtMost(120.0)); touch() }
                    KeyEvent.VK_DELETE -> selected?.let { removeObject(it); controls.refreshObjects() }
                    KeyEvent.VK_F1, KeyEvent.VK_H -> HelpDialog.show(frame)
                    KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                    KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_Q, KeyEvent.VK_E,
                    KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET -> nudge(e.keyCode, e.isShiftDown)
                    KeyEvent.VK_ESCAPE -> frame.dispose()
                }
            }
        })
    }

    /** Ctrl + drag: where the selected model would land, shown as its box until the button is let go. */
    private fun updateGhost(mouseX: Int, mouseY: Int) {
        placeMarker = java.awt.Point(mouseX, mouseY)
        ghost = selectedModel?.let { dropPoint(it, mouseX, mouseY) }
        panel.repaint()
    }

    /** Ctrl + release: the selected model moves to where its box was shown. */
    private fun dropGhost() {
        val o = selectedModel
        val target = ghost
        ghost = null
        placeMarker = null
        panel.repaint()
        if (o == null || target == null) return
        o.x = target[0]
        o.y = target[1]
        o.z = target[2]
        sceneChanged()
        controls.refreshSelection()
    }

    /**
     * Arrow keys move the selected model across the floor as the camera sees it - left, right,
     * away, closer - PageUp / PageDown up and down, Q / E or [ / ] turn it; Shift takes five
     * times the step.
     */
    private fun nudge(key: Int, big: Boolean) {
        val o = selectedModel ?: return
        val step = defaultObjectHeight / 20 * (if (big) 5 else 1)
        val v = view
        // The camera's right and its forward direction, both flattened onto the floor.
        val w = doubleArrayOf(cos(v.pitch) * sin(v.yaw), cos(v.pitch) * cos(v.yaw))
        val length = sqrt(w[0] * w[0] + w[1] * w[1]).coerceAtLeast(1e-9)
        val rightX = w[1] / length
        val rightZ = -w[0] / length
        val forwardX = -w[0] / length
        val forwardZ = -w[1] / length
        when (key) {
            KeyEvent.VK_LEFT -> { o.x -= rightX * step; o.z -= rightZ * step }
            KeyEvent.VK_RIGHT -> { o.x += rightX * step; o.z += rightZ * step }
            KeyEvent.VK_UP -> { o.x += forwardX * step; o.z += forwardZ * step }
            KeyEvent.VK_DOWN -> { o.x -= forwardX * step; o.z -= forwardZ * step }
            KeyEvent.VK_PAGE_UP -> o.y += step
            KeyEvent.VK_PAGE_DOWN -> o.y -= step
            KeyEvent.VK_Q, KeyEvent.VK_OPEN_BRACKET -> o.rotation = ((o.rotation - TURN_STEP + 540) % 360) - 180
            KeyEvent.VK_E, KeyEvent.VK_CLOSE_BRACKET -> o.rotation = ((o.rotation + TURN_STEP + 540) % 360) - 180
        }
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

        /** The dragged model's box, where it would land, drawn over the image in perspective. */
        private fun drawGhost(g2: Graphics2D) {
            val at = ghost ?: return
            val model = selectedModel ?: return
            val b = model.localBounds()
            val s = setup(view, resolution)
            val r = imageBounds()
            val corners = (0 until 8).map { i ->
                val p = doubleArrayOf(
                    at[0] + if (i and 1 == 0) b[0] else b[3],
                    at[1] + if (i and 2 == 0) b[1] else b[4],
                    at[2] + if (i and 4 == 0) b[2] else b[5]
                )
                project(s, p)?.let { px ->
                    java.awt.Point(
                        (r.x + px[0] / resolution.width * r.width).toInt(),
                        (r.y + px[1] / resolution.height * r.height).toInt()
                    )
                }
            }
            g2.color = Color(0xE8C547)
            g2.stroke = BasicStroke(1.6f)
            // The 12 edges: corners that differ in exactly one of the three bits.
            for (i in 0 until 8) for (bit in intArrayOf(1, 2, 4)) {
                val j = i or bit
                if (j == i) continue
                val a = corners[i] ?: continue
                val c = corners[j] ?: continue
                g2.drawLine(a.x, a.y, c.x, c.y)
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            shown?.let { image ->
                val r = imageBounds()
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.drawImage(image, r.x, r.y, r.width, r.height, null)
            }
            drawGhost(g2)
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
        private const val REBUILD_DELAY_MS = 80L
        private const val FRAME_MS = 16L
        private const val LAUNCH_MS = 40.0
        private const val DISPLAY_INTERVAL_NS = 100_000_000L
        private const val DEFAULT_TARGET = 1024
        private const val FIRST_DENOISE = 16
        /** Rays per pixel the quick look gathers once the view holds still, for smooth edges. */
        private const val QUICK_SAMPLES = 16
        private const val GUIDE_SAMPLES = 16
        private const val ORBIT_SPEED = 0.005
        private const val MAX_PITCH = 1.55
        private const val ZOOM_STEP = 1.1
        private const val DRAG_ZOOM = 0.02
        /** How much a model turns per Q / E press, in degrees. */
        private const val TURN_STEP = 15.0
        /** A surface counts as one to stand on when its normal is within ~45 degrees of up. */
        private const val UPWARD = 0.7
        private const val STATUS_HEIGHT = 44
        private const val HELP =
            "F1 / H: wszystkie skróty · T szybki podgląd / ray tracing · LPM obrót · Shift+LPM przesuwanie · kółko zoom · Ctrl+LPM postaw obiekt · S zapis"
    }
}
