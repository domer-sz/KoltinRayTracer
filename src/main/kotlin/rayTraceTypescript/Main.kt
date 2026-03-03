package rayTraceTypescript

import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.textures.CheckerTexture
import rayTraceTypescript.textures.ImageTexture
import rayTraceTypescript.utils.randomFloat
import kotlin.system.measureTimeMillis
import kotlin.time.Duration

fun main() {
    when(2) {
        1 -> spheresWorld()
        2 -> earth()
    }
}

private fun spheresWorld() {
    val bvhWorld = HittableList(mutableListOf(BvhNode(randomWorld())))
    val camera = Camera()

    camera.aspectRatio = 16.0f / 9.0f
    camera.imageWidth = 500
    camera.maxReflectionDepth = 20
    camera.samplesPerPixel = 90

    camera.vfov = 20.0f
    camera.lookFrom = Point(13.0f, 2.0f, 3.0f)
    camera.lookAt = Point(0.0f, 0.0f, 0.0f)
    camera.vUp = Vector(0.0f, 1.0f, 0.0f)

    camera.defocusAngle = 0.6f
    camera.focusDistance = 10.0f

    var numberOfRays: Long = 0
    val time = measureTimeMillis {
        numberOfRays = camera.render(bvhWorld)
    }
    printRenderReport(time, numberOfRays)
}

private fun printRenderReport(time: Long, numberOfRays: Long) {
    println("Execution time: ${formatDuration(time)}")
    println("Number of rays: ${numberOfRays.toHumanReadable()}")
    println(
        "Number of rays per second: ${
            (numberOfRays / java.time.Duration.ofMillis(time).toSeconds()).toHumanReadable()
        }"
    )
}

fun prepareWorld(): HittableList = WorldData.hardcodedWorld()

fun randomWorld(): HittableList {
    val worldObjects: MutableList<Hittable> = mutableListOf()

    val checker = CheckerTexture(.32F, Color(.2, .3, .1), Color(.9, .9, .9))
    val groundMaterial = Lambertian(checker)
    val groundSphere = Sphere(Point(0.0f, -1000.0f, 0.0f), 1000.0F, groundMaterial)
    worldObjects.add(groundSphere)

    fun rand() = randomFloat(0.0f, 1.0f)
    fun randRange(min: Float = 0.0f, max: Float = 1.0f) = randomFloat(min, max)
    fun randomColor(min: Float = 0.0f, max: Float = 1.0f) =
        Color(randRange(min, max), randRange(min, max), randRange(min, max))

    for (a in -11..10) {
        for (b in -11..10) {
            val chooseMat = rand()
            val center = Point(a + 0.9f * rand(), 0.2f, b + 0.9f * rand())
            if ((center - Point(4.0f, 0.2f, 0.0f)).length() > 0.9f) {
                if (chooseMat < 0.8f) {
                    val albedo = randomColor() * randomColor()
                    val center2: Point = center + Point(0f, randRange(0f, 0.5f), 0f)
                    worldObjects.add(Sphere(center, center2, 0.2f, Lambertian(albedo)))
                } else if (chooseMat < 0.95f) {
                    val albedo = randomColor(0.5f, 1.0f)
                    val fuzz = randRange(0.0f, 0.5f)
                    worldObjects.add(Sphere(center, 0.2f, Metal(albedo, fuzz)))
                } else {
                    worldObjects.add(Sphere(center, 0.2f, Dielectric(1.5f)))
                }
            }
        }
    }

    worldObjects.add(Sphere(Point(0.0f, 1.0f, 0.0f), 1.0f, Dielectric(1.5f)))
    worldObjects.add(Sphere(Point(-4.0f, 1.0f, 0.0f), 1.0f, Lambertian(Color(0.4f, 0.2f, 0.1f))))
    worldObjects.add(Sphere(Point(4.0f, 1.0f, 0.0f), 1.0f, Metal(Color(0.7f, 0.6f, 0.5f), 0.0f)))
    return HittableList(worldObjects)
}

fun formatDuration(ms: Long): String {
    val minutes = ms / 60000
    val seconds = (ms % 60000) / 1000
    val millis = ms % 1000

    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}


fun Long.toHumanReadable(): String {
    val abs = kotlin.math.abs(this)

    return when {
        abs >= 1_000_000_000 -> "%.1fB".format(this / 1_000_000_000.0)
        abs >= 1_000_000     -> "%.1fM".format(this / 1_000_000.0)
        abs >= 1_000         -> "%.1fK".format(this / 1_000.0)
        else -> this.toString()
    }
}

fun earth() {
        val earthTexture = ImageTexture("earthmap.jpg");
        val earthSurface = Lambertian(earthTexture);
        val globe = Sphere(Point(0f,0f,0f), 2f, earthSurface);

        val cam = Camera()

        cam.aspectRatio      = 16.0f / 9.0f
        cam.imageWidth       = 400
        cam.samplesPerPixel = 100
        cam.maxReflectionDepth         = 50

        cam.vfov     = 20f
        cam.lookFrom = Point(0f,0f,12f)
        cam.lookAt   = Point(0f,0f,0f)
        cam.vUp      = Vector(0f,1f,0f)

        cam.defocusAngle = 0f

        cam.render(HittableList(mutableListOf(globe)));
    }