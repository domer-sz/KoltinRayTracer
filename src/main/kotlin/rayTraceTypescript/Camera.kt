package rayTraceTypescript

import kotlin.math.*
import rayTraceTypescript.Color
import rayTraceTypescript.Interval
import rayTraceTypescript.utils.degreesToRadians
import rayTraceTypescript.utils.infinity
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

class Camera {
    var aspectRatio: Double = 16.0 / 9.0
    var imageWidth: Int = 400
    var samplesPerPixel: Int = 100
    var maxReflectionDepth: Int = 10

    var vfov: Double = 90.0
    var lookFrom: Point = Point(0.0, 0.0, 0.0)
    var lookAt: Point = Point(0.0, 0.0, -1.0)
    var vUp: Vector = Vector(0.0, 1.0, 0.0)

    var defocusAngle: Double = 0.0
    var focusDistance: Double = 10.0

    private var imageHeight: Int = 0
    private var pixelSamplesScale: Double = 1.0
    private lateinit var cameraCenter: Point
    private lateinit var pixelDeltaU: Vector
    private lateinit var pixelDeltaV: Vector
    private lateinit var pixel00: Point
    private lateinit var defocusDiscU: Vector
    private lateinit var defocusDiscV: Vector

    fun render(world: HittableList) {
        initialize()

        val header = "P3\n${imageWidth} ${imageHeight}\n255\n"
        val sb = StringBuilder(header)

        val totalPixels = imageWidth * imageHeight
        var progress = 0
        var lastPct = -1

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                var pixelColor = Color(0.0, 0.0, 0.0)
                repeat(samplesPerPixel) {
                    val ray = getRay(x.toDouble(), y.toDouble())
                    val sampleColor = rayColor(ray, maxReflectionDepth, world)
                    pixelColor = pixelColor.plus(sampleColor)
                }
                pixelColor = pixelColor.scale(pixelSamplesScale)
                sb.append("${pixelColor.colorR()} ${pixelColor.colorG()} ${pixelColor.colorB()}\n")

                // Progress like in TS: integer percent, print only when it increases
                progress += 1
                val pct = (progress * 100) / totalPixels
                if (pct > lastPct) {
                    val barWidth = 40
                    val filled = (pct * barWidth) / 100
                    val bar = buildString {
                        append('[')
                        repeat(filled) { append('#') }
                        repeat(barWidth - filled) { append('.') }
                        append(']')
                    }
                    print("\r$bar $pct%")
                    System.out.flush()
                    lastPct = if (pct < 100) pct else 100
                }
            }
        }

        Files.write(Paths.get("image.ppm"), sb.toString().toByteArray(StandardCharsets.UTF_8))
        println("\r[" + "#".repeat(40) + "] 100%")
    }

    private fun rayColor(ray: Ray, reflectionDepth: Int, world: HittableList): Color {
        if (reflectionDepth <= 0) return Color(0.0, 0.0, 0.0)

        val hit = world.hit(ray, Interval(0.001, infinity))
        if (hit != null) {
            val scatteredResult = hit.material.scatter(ray, hit)
            if (scatteredResult != null) {
                val rec = rayColor(scatteredResult.scattered, reflectionDepth - 1, world)
                return rec.multiply(scatteredResult.albedo)
            }
            return Color(0.0, 0.0, 0.0)
        }

        val unitDirection = ray.direction.unit()
        val alpha = 0.5 * (unitDirection.y + 1.0)
        val white = Color(1.0, 1.0, 1.0).scale(1.0 - alpha)
        val blue = Color(0.5, 0.7, 1.0).scale(alpha)
        return white.plus(blue)
    }

    fun getRay(x: Double, y: Double): Ray {
        val offset = sampleSquare()
        val pixelSample = Point(
            pixel00.x + pixelDeltaU.x * (x + offset.x) + pixelDeltaV.x * (y + offset.y),
            pixel00.y + pixelDeltaU.y * (x + offset.x) + pixelDeltaV.y * (y + offset.y),
            pixel00.z + pixelDeltaU.z * (x + offset.x) + pixelDeltaV.z * (y + offset.y)
        )
        val rayOrigin = if (defocusAngle <= 0.0) cameraCenter else defocusDiskSample()
        val rayDirection = pixelSample.minus(rayOrigin)
        return Ray(rayOrigin, rayDirection)
    }

    fun sampleSquare(): Vector =
        Vector(kotlin.random.Random.nextDouble() - 0.5, kotlin.random.Random.nextDouble() - 0.5, 0.0)

    private fun defocusDiskSample(): Point {
        val p = Vector.randomVectorInUnitDisc()
        return Point(
            cameraCenter.x + defocusDiscU.x * p.x + defocusDiscV.x * p.y,
            cameraCenter.y + defocusDiscU.y * p.x + defocusDiscV.y * p.y,
            cameraCenter.z + defocusDiscU.z * p.x + defocusDiscV.z * p.y
        )
    }

    private fun initialize() {
        imageHeight = floor(imageWidth.toDouble() / aspectRatio).toInt()
        if (imageHeight < 1) imageHeight = 1

        pixelSamplesScale = 1.0 / samplesPerPixel.toDouble()
        cameraCenter = lookFrom

        val theta = degreesToRadians(vfov)
        val h = tan(theta / 2.0)
        val viewportHeight = 2.0 * h * focusDistance
        val viewportWidth = viewportHeight * (imageWidth.toDouble() / imageHeight.toDouble())

        val w = Vector.unit(lookFrom.minus(lookAt))
        val u = Vector.unit(Vector.cross(vUp, w))
        val v = Vector.cross(w, u)

        val viewportU = u.scale(viewportWidth)
        val viewportV = v.negate().scale(viewportHeight)

        pixelDeltaU = viewportU.divide(imageWidth.toDouble())
        pixelDeltaV = viewportV.divide(imageHeight.toDouble())

        val viewportUpperLeft = cameraCenter.minus(w.scale(focusDistance))
            .minus(viewportU.divide(2.0))
            .minus(viewportV.divide(2.0))

        pixel00 = Point(
            viewportUpperLeft.x + pixelDeltaU.x * 0.5 + pixelDeltaV.x * 0.5,
            viewportUpperLeft.y + pixelDeltaU.y * 0.5 + pixelDeltaV.y * 0.5,
            viewportUpperLeft.z + pixelDeltaU.z * 0.5 + pixelDeltaV.z * 0.5
        )

        val defocusRadius = focusDistance * tan(degreesToRadians(defocusAngle) / 2.0)
        defocusDiscU = u.scale(defocusRadius)
        defocusDiscV = v.scale(defocusRadius)
    }
}
