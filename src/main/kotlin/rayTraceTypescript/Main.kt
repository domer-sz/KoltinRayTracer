package rayTraceTypescript

import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.utils.randomFloat

fun main() {
    val world: HittableList = prepareWorld()
    val camera = Camera()

    camera.aspectRatio = 16.0f / 9.0f
    camera.imageWidth = 300
    camera.samplesPerPixel = 50
    camera.maxReflectionDepth = 45

    camera.vfov = 20.0f
    camera.lookFrom = Point(13.0f, 2.0f, 3.0f)
    camera.lookAt = Point(0.0f, 0.0f, 0.0f)
    camera.vUp = Vector(0.0f, 1.0f, 0.0f)

    camera.defocusAngle = 0.6f
    camera.focusDistance = 10.0f

    camera.render(world)
}

fun prepareWorld(): HittableList = WorldData.hardcodedWorld()

fun randomWorld(): HittableList {
    val worldObjects: MutableList<Hittable> = mutableListOf()

    val groundMaterial = Lambertian(Color(0.5f, 0.5f, 0.5f))
    val groundSphere = Sphere(Point(0.0f, -1000.0f, 0.0f), 1000.0, groundMaterial)
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
                    worldObjects.add(Sphere(center, 0.2f, Lambertian(albedo)))
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
