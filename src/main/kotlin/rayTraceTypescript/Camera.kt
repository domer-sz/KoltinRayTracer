package rayTraceTypescript

import kotlin.math.floor
import kotlin.math.tan
import kotlin.math.sqrt
import rayTraceTypescript.utils.degreesToRadians
import rayTraceTypescript.utils.infinity
import rayTraceTypescript.objects.HittableList
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import rayTraceTypescript.utils.RandomSource
import rayTraceTypescript.utils.randomFloat
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.materials.ScatteredResult

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
    private lateinit var tracePool: TracePool
    private lateinit var progressFrames: Array<String>

    fun render(world: HittableList, outputPath: Path = Paths.get("image.ppm")): Long {
        initialize()

        val header = "P3\n${imageWidth} ${imageHeight}\n255\n"
        val estimatedPixelChars = imageWidth * imageHeight * 12
        val sb = StringBuilder(header.length + estimatedPixelChars)
        sb.append(header)

        val totalPixels = imageWidth * imageHeight
        var numberOfRays: Long = 0
        var progress = 0
        var lastPct = -1

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                var pixelR = 0.0f
                var pixelG = 0.0f
                var pixelB = 0.0f
                repeat(samplesPerPixel) {
                    numberOfRays++
                    getRay(x.toFloat(), y.toFloat(), tracePool.rays[0], tracePool.sampleOffset, tracePool.defocusOffset)
                    rayColor(tracePool.rays[0], maxReflectionDepth, world, 0, tracePool)
                    pixelR += tracePool.colorOut[0]
                    pixelG += tracePool.colorOut[1]
                    pixelB += tracePool.colorOut[2]
                }
                appendColor(sb, pixelR * pixelSamplesScale, pixelG * pixelSamplesScale, pixelB * pixelSamplesScale)

                progress += 1
                val pct = (progress * 100) / totalPixels
                if (pct > lastPct) {
                    print(progressFrames[pct])
                    System.out.flush()
                    lastPct = pct
                }
            }
        }

        println()
        Files.write(outputPath, sb.toString().toByteArray(StandardCharsets.UTF_8))
        return numberOfRays
    }

    private fun rayColor(
        ray: Ray,
        reflectionDepth: Int,
        world: HittableList,
        depthIndex: Int,
        tracePool: TracePool,
    ) {
        if (reflectionDepth <= 0) {
            setBlack(tracePool.colorOut)
            return
        }

        val hit = tracePool.hits[depthIndex]
        if (world.hit(ray, 0.001f, infinity, hit)) {
            val scatter = tracePool.scatters[depthIndex]
            if (hit.material.scatter(ray, hit, scatter)) {
                val nextRay = tracePool.rays[depthIndex + 1]
                nextRay.copyFrom(scatter.scattered)
                rayColor(nextRay, reflectionDepth - 1, world, depthIndex + 1, tracePool)
                tracePool.colorOut[0] *= scatter.albedoR
                tracePool.colorOut[1] *= scatter.albedoG
                tracePool.colorOut[2] *= scatter.albedoB
                return
            }
            setBlack(tracePool.colorOut)
            return
        }

        val dirX = ray.direction.x
        val dirY = ray.direction.y
        val dirZ = ray.direction.z
        val invLen = 1.0f / sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        val alpha = 0.5f * (dirY * invLen + 1.0f)
        tracePool.colorOut[0] = 1.0f - 0.5f * alpha
        tracePool.colorOut[1] = 1.0f - 0.3f * alpha
        tracePool.colorOut[2] = 1.0f
    }

    fun getRay(x: Float, y: Float): Ray {
        if (!::pixel00.isInitialized) {
            initialize()
        }
        val ray = Ray(Point(0.0f, 0.0f, 0.0f), Vector(0.0f, 0.0f, 0.0f), 0.0f)
        val sampleOffset = Vector(0.0f, 0.0f, 0.0f)
        val defocusOffset = Vector(0.0f, 0.0f, 0.0f)
        getRay(x, y, ray, sampleOffset, defocusOffset)
        return ray
    }

    private fun getRay(x: Float, y: Float, outRay: Ray, sampleOffset: Vector, defocusOffset: Vector) {
        sampleSquare(sampleOffset)
        val sampleX = x + sampleOffset.x
        val sampleY = y + sampleOffset.y

        val pixelSampleX = pixel00.x + pixelDeltaU.x * sampleX + pixelDeltaV.x * sampleY
        val pixelSampleY = pixel00.y + pixelDeltaU.y * sampleX + pixelDeltaV.y * sampleY
        val pixelSampleZ = pixel00.z + pixelDeltaU.z * sampleX + pixelDeltaV.z * sampleY

        val rayOrigin = outRay.origin
        if (defocusAngle <= 0.0f) {
            rayOrigin.set(cameraCenter.x, cameraCenter.y, cameraCenter.z)
        } else {
            defocusDiskSample(rayOrigin, defocusOffset)
        }

        outRay.direction.set(
            pixelSampleX - rayOrigin.x,
            pixelSampleY - rayOrigin.y,
            pixelSampleZ - rayOrigin.z
        )
        outRay.time = randomFloat(0.0F, 1.0F)
    }

    fun sampleSquare(): Vector {
        val out = Vector(0.0f, 0.0f, 0.0f)
        sampleSquare(out)
        return out
    }

    private fun sampleSquare(out: Vector) {
        out.set(RandomSource.nextFloat() - 0.5f, RandomSource.nextFloat() - 0.5f, 0.0f)
    }

    private fun defocusDiskSample(outOrigin: Point, discRandom: Vector) {
        Vector.randomVectorInUnitDisc(discRandom)
        outOrigin.set(
            cameraCenter.x + defocusDiscU.x * discRandom.x + defocusDiscV.x * discRandom.y,
            cameraCenter.y + defocusDiscU.y * discRandom.x + defocusDiscV.y * discRandom.y,
            cameraCenter.z + defocusDiscU.z * discRandom.x + defocusDiscV.z * discRandom.y
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

        ensureTracePool(maxReflectionDepth + 2)
        progressFrames = buildProgressFrames()
    }

    private fun ensureTracePool(requiredDepth: Int) {
        if (::tracePool.isInitialized && tracePool.rays.size >= requiredDepth) {
            return
        }

        tracePool = TracePool(
            rays = Array(requiredDepth) { Ray(Point(0.0f, 0.0f, 0.0f), Vector(0.0f, 0.0f, 0.0f), 0.0f) },
            hits = Array(requiredDepth) { Hit() },
            scatters = Array(requiredDepth) { ScatteredResult() },
            colorOut = FloatArray(3),
            sampleOffset = Vector(0.0f, 0.0f, 0.0f),
            defocusOffset = Vector(0.0f, 0.0f, 0.0f),
        )
    }

    private fun buildProgressFrames(): Array<String> = Array(101) { pct ->
        val barWidth = 40
        val filled = (pct * barWidth) / 100
        "\r[" + "#".repeat(filled) + ".".repeat(barWidth - filled) + "] $pct%"
    }

    private fun setBlack(out: FloatArray) {
        out[0] = 0.0f
        out[1] = 0.0f
        out[2] = 0.0f
    }

    private fun appendColor(sb: StringBuilder, r: Float, g: Float, b: Float) {
        sb.append(Color.toChannel(r))
            .append(' ')
            .append(Color.toChannel(g))
            .append(' ')
            .append(Color.toChannel(b))
            .append('\n')
    }

    private data class TracePool(
        val rays: Array<Ray>,
        val hits: Array<Hit>,
        val scatters: Array<ScatteredResult>,
        val colorOut: FloatArray,
        val sampleOffset: Vector,
        val defocusOffset: Vector,
    )
}
