package rayTraceTypescript.render

import rayTraceTypescript.Color
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.utils.infinity
import rayTraceTypescript.utils.RandomSource
import rayTraceTypescript.utils.randomFloat
import java.nio.file.Path

/** The original single-threaded path tracer; reference implementation and GPU fallback. */
class CpuRenderer : Renderer {

    override fun render(world: Hittable, setup: CameraSetup, outputPath: Path): Long {
        val pixels = FloatArray(setup.totalPixels * 3)
        val progress = ProgressBar(setup.totalPixels)
        var numberOfRays: Long = 0
        var done = 0

        for (y in 0 until setup.imageHeight) {
            for (x in 0 until setup.imageWidth) {
                var pixelColor = Color(0.0f, 0.0f, 0.0f)
                repeat(setup.samplesPerPixel) {
                    numberOfRays++
                    val ray = getRay(x.toFloat(), y.toFloat(), setup)
                    val sampleColor = rayColor(ray, setup.maxReflectionDepth, world)
                    pixelColor += sampleColor
                }
                pixelColor *= setup.pixelSamplesScale

                val base = (y * setup.imageWidth + x) * 3
                pixels[base] = pixelColor.r
                pixels[base + 1] = pixelColor.g
                pixels[base + 2] = pixelColor.b

                done += 1
                progress.report(done)
            }
        }

        PpmWriter.write(outputPath, setup.imageWidth, setup.imageHeight, pixels)
        progress.finish()
        return numberOfRays
    }

    private fun rayColor(ray: Ray, reflectionDepth: Int, world: Hittable): Color {
        if (reflectionDepth <= 0) return Color(0.0f, 0.0f, 0.0f)

        val hit = world.hit(ray, Interval(0.001f, infinity))
        if (hit != null) {
            val scatteredResult = hit.material.scatter(ray, hit)
            if (scatteredResult != null) {
                val rec = rayColor(scatteredResult.scattered, reflectionDepth - 1, world)
                return rec * scatteredResult.albedo
            }
            return Color(0.0f, 0.0f, 0.0f)
        }

        val unitDirection = ray.direction.unit()
        val alpha = 0.5f * (unitDirection.y + 1.0f)
        val white = Color(1.0f, 1.0f, 1.0f) * (1.0f - alpha)
        val blue = Color(0.5f, 0.7f, 1.0f) * alpha
        return white + blue
    }

    companion object {
        fun getRay(x: Float, y: Float, setup: CameraSetup): Ray {
            val offset = sampleSquare()
            val pixelSample = Point(
                setup.pixel00.x + setup.pixelDeltaU.x * (x + offset.x) + setup.pixelDeltaV.x * (y + offset.y),
                setup.pixel00.y + setup.pixelDeltaU.y * (x + offset.x) + setup.pixelDeltaV.y * (y + offset.y),
                setup.pixel00.z + setup.pixelDeltaU.z * (x + offset.x) + setup.pixelDeltaV.z * (y + offset.y)
            )
            val rayOrigin = if (setup.defocusAngle <= 0.0f) setup.cameraCenter else defocusDiskSample(setup)
            val rayDirection = pixelSample - rayOrigin
            val rayTime = randomFloat(0.0F, 1.0F) //random ray time: TODO: simulate real time
            return Ray(rayOrigin, rayDirection, rayTime)
        }

        private fun sampleSquare(): Vector =
            Vector(RandomSource.nextFloat() - 0.5f, RandomSource.nextFloat() - 0.5f, 0.0f)

        private fun defocusDiskSample(setup: CameraSetup): Point {
            val p = Vector.randomVectorInUnitDisc()
            return Point(
                setup.cameraCenter.x + setup.defocusDiscU.x * p.x + setup.defocusDiscV.x * p.y,
                setup.cameraCenter.y + setup.defocusDiscU.y * p.x + setup.defocusDiscV.y * p.y,
                setup.cameraCenter.z + setup.defocusDiscU.z * p.x + setup.defocusDiscV.z * p.y
            )
        }
    }
}
