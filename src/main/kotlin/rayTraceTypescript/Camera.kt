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
import kotlin.math.min

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
    var showProgress: Boolean = false

    private var imageHeight: Int = 0
    private var pixelSamplesScale: Float = 1.0f
    private lateinit var cameraCenter: Point
    private lateinit var pixelDeltaU: Vector
    private lateinit var pixelDeltaV: Vector
    private lateinit var pixel00: Point
    private lateinit var defocusDiscU: Vector
    private lateinit var defocusDiscV: Vector
    private lateinit var pixelBaseX: FloatArray
    private lateinit var pixelBaseY: FloatArray
    private lateinit var pixelBaseZ: FloatArray
    private lateinit var rowBaseX: FloatArray
    private lateinit var rowBaseY: FloatArray
    private lateinit var rowBaseZ: FloatArray
    private lateinit var tracePool: TracePool
    private lateinit var progressFrames: Array<String>

    fun render(
        world: HittableList,
        outputPath: Path = Paths.get("image.ppm"),
        saveOutput: Boolean = true,
    ): Long {
        initialize()
        world.prepareForRender()

        val sb = if (saveOutput) {
            val header = "P3\n${imageWidth} ${imageHeight}\n255\n"
            val estimatedPixelChars = imageWidth * imageHeight * 12
            StringBuilder(header.length + estimatedPixelChars).apply { append(header) }
        } else {
            null
        }

        val totalPixels = imageWidth * imageHeight
        val rayCounter = longArrayOf(0L)
        var progress = 0
        var lastPct = -1

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                var pixelR = 0.0f
                var pixelG = 0.0f
                var pixelB = 0.0f
                var sample = 0
                while (sample < samplesPerPixel) {
                    getRay(x, y, tracePool.rays[0], tracePool.sampleOffset, tracePool.defocusOffset)
                    rayColor(tracePool.rays[0], maxReflectionDepth, world, 0, tracePool, rayCounter)
                    pixelR += tracePool.colorOut[0]
                    pixelG += tracePool.colorOut[1]
                    pixelB += tracePool.colorOut[2]
                    sample++
                }
                if (sb != null) {
                    appendColor(sb, pixelR * pixelSamplesScale, pixelG * pixelSamplesScale, pixelB * pixelSamplesScale)
                }

                if (showProgress) {
                    progress += 1
                    val pct = (progress * 100) / totalPixels
                    if (pct > lastPct) {
                        print(progressFrames[pct])
                        System.out.flush()
                        lastPct = pct
                    }
                }
            }
        }

        if (showProgress) {
            println()
        }
        if (sb != null) {
            Files.write(outputPath, sb.toString().toByteArray(StandardCharsets.UTF_8))
        }
        return rayCounter[0]
    }

    private fun rayColor(
        ray: Ray,
        reflectionDepth: Int,
        world: HittableList,
        depthIndex: Int,
        tracePool: TracePool,
        rayCounter: LongArray,
    ) {
        var attenuationR = 1.0f
        var attenuationG = 1.0f
        var attenuationB = 1.0f
        var currentRay = ray
        var remainingDepth = reflectionDepth
        var bounceIndex = depthIndex

        while (true) {
            rayCounter[0] += 1L

            if (remainingDepth <= 0) {
                setBlack(tracePool.colorOut)
                return
            }

            val hit = tracePool.hits[bounceIndex]
            if (!world.hit(currentRay, 0.001f, infinity, hit)) {
                val dirX = currentRay.direction.x
                val dirY = currentRay.direction.y
                val dirZ = currentRay.direction.z
                val invLen = 1.0f / sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
                val alpha = 0.5f * (dirY * invLen + 1.0f)
                val skyR = 1.0f - 0.5f * alpha
                val skyG = 1.0f - 0.3f * alpha
                val skyB = 1.0f
                tracePool.colorOut[0] = skyR * attenuationR
                tracePool.colorOut[1] = skyG * attenuationG
                tracePool.colorOut[2] = skyB * attenuationB
                return
            }

            val nextRay = tracePool.rays[bounceIndex + 1]
            val scatterDir = tracePool.scatterDir

            when (hit.materialType) {
                Hit.MATERIAL_LAMBERTIAN -> {
                    randomInUnitSphere(scatterDir)
                    val invScatterLen =
                        1.0f / sqrt(scatterDir.x * scatterDir.x + scatterDir.y * scatterDir.y + scatterDir.z * scatterDir.z)
                    scatterDir.set(
                        scatterDir.x * invScatterLen,
                        scatterDir.y * invScatterLen,
                        scatterDir.z * invScatterLen,
                    )
                    if (scatterDir.x * hit.normal.x + scatterDir.y * hit.normal.y + scatterDir.z * hit.normal.z < 0.0f) {
                        scatterDir.set(-scatterDir.x, -scatterDir.y, -scatterDir.z)
                    }
                    if (scatterDir.nearZero()) {
                        scatterDir.set(hit.normal)
                    }
                }
                Hit.MATERIAL_METAL -> {
                    val dirX = currentRay.direction.x
                    val dirY = currentRay.direction.y
                    val dirZ = currentRay.direction.z
                    val invLen = 1.0f / sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
                    val unitX = dirX * invLen
                    val unitY = dirY * invLen
                    val unitZ = dirZ * invLen
                    val normalX = hit.normal.x
                    val normalY = hit.normal.y
                    val normalZ = hit.normal.z
                    val scale = 2.0f * (unitX * normalX + unitY * normalY + unitZ * normalZ)
                    tracePool.reflectedDirection.set(
                        unitX - normalX * scale,
                        unitY - normalY * scale,
                        unitZ - normalZ * scale,
                    )
                    randomInUnitSphere(tracePool.randomDirection)
                    scatterDir.set(
                        tracePool.reflectedDirection.x + tracePool.randomDirection.x * hit.fuzz,
                        tracePool.reflectedDirection.y + tracePool.randomDirection.y * hit.fuzz,
                        tracePool.reflectedDirection.z + tracePool.randomDirection.z * hit.fuzz,
                    )
                    if (scatterDir.x * normalX + scatterDir.y * normalY + scatterDir.z * normalZ <= 0.0f) {
                        setBlack(tracePool.colorOut)
                        return
                    }
                }
                Hit.MATERIAL_DIELECTRIC -> {
                    val dirX = currentRay.direction.x
                    val dirY = currentRay.direction.y
                    val dirZ = currentRay.direction.z
                    val invLen = 1.0f / sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
                    tracePool.unitDirection.set(dirX * invLen, dirY * invLen, dirZ * invLen)

                    val refractionRatio = if (hit.frontFace) 1.0f / hit.refractiveIndex else hit.refractiveIndex
                    val normalX = hit.normal.x
                    val normalY = hit.normal.y
                    val normalZ = hit.normal.z
                    val cosTheta = min(
                        -(tracePool.unitDirection.x * normalX + tracePool.unitDirection.y * normalY + tracePool.unitDirection.z * normalZ),
                        1.0f
                    )
                    val sinTheta = sqrt(1.0f - cosTheta * cosTheta)
                    val cannotRefract = refractionRatio * sinTheta > 1.0f
                    val useReflect = cannotRefract || reflectance(cosTheta, refractionRatio) > RandomSource.nextFloat()

                    if (useReflect) {
                        val scale = 2.0f * (tracePool.unitDirection.x * normalX + tracePool.unitDirection.y * normalY + tracePool.unitDirection.z * normalZ)
                        scatterDir.set(
                            tracePool.unitDirection.x - normalX * scale,
                            tracePool.unitDirection.y - normalY * scale,
                            tracePool.unitDirection.z - normalZ * scale,
                        )
                    } else {
                        val rOutPerpX = (tracePool.unitDirection.x + normalX * cosTheta) * refractionRatio
                        val rOutPerpY = (tracePool.unitDirection.y + normalY * cosTheta) * refractionRatio
                        val rOutPerpZ = (tracePool.unitDirection.z + normalZ * cosTheta) * refractionRatio
                        val rOutPerpLenSquared =
                            rOutPerpX * rOutPerpX + rOutPerpY * rOutPerpY + rOutPerpZ * rOutPerpZ
                        val parallelScale = -sqrt(kotlin.math.abs(1.0f - rOutPerpLenSquared))
                        scatterDir.set(
                            rOutPerpX + normalX * parallelScale,
                            rOutPerpY + normalY * parallelScale,
                            rOutPerpZ + normalZ * parallelScale,
                        )
                    }
                }
                else -> {
                    setBlack(tracePool.colorOut)
                    return
                }
            }

            attenuationR *= hit.albedoR
            attenuationG *= hit.albedoG
            attenuationB *= hit.albedoB

            nextRay.set(
                hit.point.x,
                hit.point.y,
                hit.point.z,
                scatterDir.x,
                scatterDir.y,
                scatterDir.z,
                currentRay.time
            )

            currentRay = nextRay
            remainingDepth--
            bounceIndex++
        }
    }

    fun getRay(x: Float, y: Float): Ray {
        if (!::pixel00.isInitialized) {
            initialize()
        }
        val ray = Ray(Point(0.0f, 0.0f, 0.0f), Vector(0.0f, 0.0f, 0.0f), 0.0f)
        val sampleOffset = Vector(0.0f, 0.0f, 0.0f)
        val defocusOffset = Vector(0.0f, 0.0f, 0.0f)
        getRay(x.toInt(), y.toInt(), ray, sampleOffset, defocusOffset)
        return ray
    }

    private fun getRay(x: Int, y: Int, outRay: Ray, sampleOffset: Vector, defocusOffset: Vector) {
        sampleSquare(sampleOffset)

        val pixelSampleX =
            pixelBaseX[x] + rowBaseX[y] + pixelDeltaU.x * sampleOffset.x + pixelDeltaV.x * sampleOffset.y
        val pixelSampleY =
            pixelBaseY[x] + rowBaseY[y] + pixelDeltaU.y * sampleOffset.x + pixelDeltaV.y * sampleOffset.y
        val pixelSampleZ =
            pixelBaseZ[x] + rowBaseZ[y] + pixelDeltaU.z * sampleOffset.x + pixelDeltaV.z * sampleOffset.y

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

        pixelBaseX = FloatArray(imageWidth)
        pixelBaseY = FloatArray(imageWidth)
        pixelBaseZ = FloatArray(imageWidth)
        for (x in 0 until imageWidth) {
            pixelBaseX[x] = pixel00.x + pixelDeltaU.x * x.toFloat()
            pixelBaseY[x] = pixel00.y + pixelDeltaU.y * x.toFloat()
            pixelBaseZ[x] = pixel00.z + pixelDeltaU.z * x.toFloat()
        }

        rowBaseX = FloatArray(imageHeight)
        rowBaseY = FloatArray(imageHeight)
        rowBaseZ = FloatArray(imageHeight)
        for (y in 0 until imageHeight) {
            rowBaseX[y] = pixelDeltaV.x * y.toFloat()
            rowBaseY[y] = pixelDeltaV.y * y.toFloat()
            rowBaseZ[y] = pixelDeltaV.z * y.toFloat()
        }

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
            colorOut = FloatArray(3),
            sampleOffset = Vector(0.0f, 0.0f, 0.0f),
            defocusOffset = Vector(0.0f, 0.0f, 0.0f),
            unitDirection = Vector(0.0f, 0.0f, 0.0f),
            reflectedDirection = Vector(0.0f, 0.0f, 0.0f),
            randomDirection = Vector(0.0f, 0.0f, 0.0f),
            scatterDir = Vector(0.0f, 0.0f, 0.0f),
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

    private fun reflectance(cosine: Float, ri: Float): Float {
        var r0 = (1f - ri) / (1f + ri)
        r0 *= r0
        val oneMinusCos = 1.0f - cosine
        return r0 + (1f - r0) * oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos
    }

    private fun randomInUnitSphere(out: Vector) {
        while (true) {
            val x = RandomSource.nextFloat(-1.0f, 1.0f)
            val y = RandomSource.nextFloat(-1.0f, 1.0f)
            val z = RandomSource.nextFloat(-1.0f, 1.0f)
            if (x * x + y * y + z * z < 1.0f) {
                out.set(x, y, z)
                return
            }
        }
    }

    private data class TracePool(
        val rays: Array<Ray>,
        val hits: Array<Hit>,
        val colorOut: FloatArray,
        val sampleOffset: Vector,
        val defocusOffset: Vector,
        val unitDirection: Vector,
        val reflectedDirection: Vector,
        val randomDirection: Vector,
        val scatterDir: Vector,
    )
}
