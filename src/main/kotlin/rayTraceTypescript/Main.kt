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

    camera.aspectRatio = 16.0 / 9.0
    camera.imageWidth = 300
    camera.samplesPerPixel = 50
    camera.maxReflectionDepth = 40

    camera.vfov = 20.0
    camera.lookFrom = Point(13.0, 2.0, 3.0)
    camera.lookAt = Point(0.0, 0.0, 0.0)
    camera.vUp = Vector(0.0, 1.0, 0.0)

    camera.defocusAngle = 0.6
    camera.focusDistance = 10.0

    camera.render(world)
}

fun prepareWorld(): HittableList {
    val worldObjects: MutableList<Hittable> = mutableListOf()

    val groundMaterial = Lambertian(Color(0.5, 0.5, 0.5))
    val groundSphere = Sphere(Point(0.0, -1000.0, 0.0), 1000.0, groundMaterial)
    worldObjects.add(groundSphere)

    fun rand() = randomFloat(0.0, 1.0)
    fun randRange(min: Double = 0.0, max: Double = 1.0) = randomFloat(min, max)
    fun randomColor(min: Double = 0.0, max: Double = 1.0) =
        Color(randRange(min, max), randRange(min, max), randRange(min, max))

    for (a in -11..10) {
        for (b in -11..10) {
            val chooseMat = rand()
            val center = Point(a + 0.9 * rand(), 0.2, b + 0.9 * rand())
            if ((center.minus(Point(4.0, 0.2, 0.0))).length() > 0.9) {
                if (chooseMat < 0.8) {
                    val albedo = randomColor().multiply(randomColor())
                    worldObjects.add(Sphere(center, 0.2, Lambertian(albedo)))
                } else if (chooseMat < 0.95) {
                    val albedo = randomColor(0.5, 1.0)
                    val fuzz = randRange(0.0, 0.5)
                    worldObjects.add(Sphere(center, 0.2, Metal(albedo, fuzz)))
                } else {
                    worldObjects.add(Sphere(center, 0.2, Dielectric(1.5)))
                }
            }
        }
    }

    worldObjects.add(Sphere(Point(0.0, 1.0, 0.0), 1.0, Dielectric(1.5)))
    worldObjects.add(Sphere(Point(-4.0, 1.0, 0.0), 1.0, Lambertian(Color(0.4, 0.2, 0.1))))
    worldObjects.add(Sphere(Point(4.0, 1.0, 0.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.0)))
    return HittableList(worldObjects)
}
