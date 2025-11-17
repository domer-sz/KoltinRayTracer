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
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import rayTraceTypescript.utils.RandomSource

class Camera {
    var aspectRatio: Float = 16.0f / 9.0f
    var imageWidth: Int = 400
    var samplesPerPixel: Int = 100
    var maxReflectionDepth: Int = 10

    var vfov: Float = 90.0f
    var lookFrom: Point = Point(0.0f, 0.0f, 0.0f)
    var lookAt: Point = Point(0.0f, 0.0f, -1.0f)
    var vUp: Vector = Vector(0.0f, 1.0f, 0.0f)

    var defocusAngle: Float = 0.0f
    var focusDistance: Float = 10.0f

    private var imageHeight: Int = 0
    private var pixelSamplesScale: Float = 1.0f
    private lateinit var cameraCenter: Point
    private lateinit var pixelDeltaU: Vector
    private lateinit var pixelDeltaV: Vector
    private lateinit var pixel00: Point
    private lateinit var defocusDiscU: Vector
    private lateinit var defocusDiscV: Vector

    fun render(world: HittableList, outputPath: Path = Paths.get("image.ppm")) {
        initialize()

        val header = "P3\n${imageWidth} ${imageHeight}\n255\n"
        val sb = StringBuilder(header)

        val totalPixels = imageWidth * imageHeight
        var progress = 0
        var lastPct = -1

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                var pixelColor = Color(0.0f, 0.0f, 0.0f)
                repeat(samplesPerPixel) {
                    val ray = getRay(x.toFloat(), y.toFloat())
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

        Files.write(outputPath, sb.toString().toByteArray(StandardCharsets.UTF_8))
        println("\r[" + "#".repeat(40) + "] 100%")
    }

    private fun rayColor(ray: Ray, reflectionDepth: Int, world: HittableList): Color {
        if (reflectionDepth <= 0) return Color(0.0f, 0.0f, 0.0f)

        val hit = world.hit(ray, Interval(0.001f, infinity))
        if (hit != null) {
            val scatteredResult = hit.material.scatter(ray, hit)
            if (scatteredResult != null) {
                val rec = rayColor(scatteredResult.scattered, reflectionDepth - 1, world)
                return rec.multiply(scatteredResult.albedo)
            }
            return Color(0.0f, 0.0f, 0.0f)
        }

        val unitDirection = ray.direction.unit()
        val alpha = 0.5f * (unitDirection.y + 1.0f)
        val white = Color(1.0f, 1.0f, 1.0f).scale(1.0f - alpha)
        val blue = Color(0.5f, 0.7f, 1.0f).scale(alpha)
        return white.plus(blue)
    }

    fun getRay(x: Float, y: Float): Ray {
        val offset = sampleSquare()
        val pixelSample = Point(
            pixel00.x + pixelDeltaU.x * (x + offset.x) + pixelDeltaV.x * (y + offset.y),
            pixel00.y + pixelDeltaU.y * (x + offset.x) + pixelDeltaV.y * (y + offset.y),
            pixel00.z + pixelDeltaU.z * (x + offset.x) + pixelDeltaV.z * (y + offset.y)
        )
        val rayOrigin = if (defocusAngle <= 0.0f) cameraCenter else defocusDiskSample()
        val rayDirection = pixelSample - rayOrigin
        return Ray(rayOrigin, rayDirection)
    }

    fun sampleSquare(): Vector =
        Vector(RandomSource.nextFloat() - 0.5f, RandomSource.nextFloat() - 0.5f, 0.0f)

    private fun defocusDiskSample(): Point {
        val p = Vector.randomVectorInUnitDisc()
        return Point(
            cameraCenter.x + defocusDiscU.x * p.x + defocusDiscV.x * p.y,
            cameraCenter.y + defocusDiscU.y * p.x + defocusDiscV.y * p.y,
            cameraCenter.z + defocusDiscU.z * p.x + defocusDiscV.z * p.y
        )
    }

    private fun initialize() {
        imageHeight = floor(imageWidth.toFloat() / aspectRatio).toInt()
        if (imageHeight < 1) imageHeight = 1

        pixelSamplesScale = 1.0f / samplesPerPixel.toFloat()
        cameraCenter = lookFrom

        val theta = degreesToRadians(vfov)
        val h = tan((theta / 2.0f).toDouble()).toFloat()
        val viewportHeight = 2.0f * h * focusDistance
        val viewportWidth = viewportHeight * (imageWidth.toFloat() / imageHeight.toFloat())

        val w = Vector.unit(lookFrom - lookAt)
        val u = Vector.unit(Vector.cross(vUp, w))
        val v = Vector.cross(w, u)

        val viewportU = u * viewportWidth
        val viewportV = (-v) * viewportHeight

        pixelDeltaU = viewportU / imageWidth.toFloat()
        pixelDeltaV = viewportV / imageHeight.toFloat()

        val viewportCorner = cameraCenter - w * focusDistance
        val viewportUpperLeftShiftedU = viewportCorner - (viewportU / 2.0f)
        val viewportUpperLeft = viewportUpperLeftShiftedU - (viewportV / 2.0f)

        pixel00 = Point(
            viewportUpperLeft.x + pixelDeltaU.x * 0.5f + pixelDeltaV.x * 0.5f,
            viewportUpperLeft.y + pixelDeltaU.y * 0.5f + pixelDeltaV.y * 0.5f,
            viewportUpperLeft.z + pixelDeltaU.z * 0.5f + pixelDeltaV.z * 0.5f
        )

        val defocusRadius =
            focusDistance * tan((degreesToRadians(defocusAngle) / 2.0f).toDouble()).toFloat()
        defocusDiscU = u * defocusRadius
        defocusDiscV = v * defocusRadius
    }
}
