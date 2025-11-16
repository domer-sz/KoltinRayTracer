package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import rayTraceTypescript.utils.RandomSource

class ImageRegressionTest {
    @Test
    fun `rendered ppm stays close to golden image`() {
        val reference = parsePpm(loadReferenceImage())
        val actual = renderCurrentScene()
        assertEquals(reference.width, actual.width, "reference width")
        assertEquals(reference.height, actual.height, "reference height")

        val diff = diffStats(reference, actual)
        // Camera.sampleSquare, defocusDiskSample, and the material scatter routines all rely on Random.Default.
        // The thresholds below allow for that stochastic noise while still flagging real regressions.
        assertTrue(
            diff.meanAbsDiff <= MEAN_ABS_DIFF_THRESHOLD,
            "Mean absolute difference ${diff.meanAbsDiff} exceeds $MEAN_ABS_DIFF_THRESHOLD"
        )
        assertTrue(
            diff.highDiffCount <= MAX_HIGH_DIFF_CHANNELS,
            "${diff.highDiffCount} channels differ by >= $HIGH_DIFF_THRESHOLD (max ${diff.maxDiff})"
        )
    }

    private fun renderCurrentScene(): PpmImage {
        val camera = Camera().apply {
            aspectRatio = 16.0 / 9.0
            imageWidth = 300
            samplesPerPixel = 50
            maxReflectionDepth = 45
            vfov = 20.0
            lookFrom = Point(13.0, 2.0, 3.0)
            lookAt = Point(0.0, 0.0, 0.0)
            vUp = Vector(0.0, 1.0, 0.0)
            defocusAngle = 0.6
            focusDistance = 10.0
        }
        val world = prepareWorld()
        val tempFile = Files.createTempFile("raytracer-image-test", ".ppm")
        RandomSource.withSeed(4242L)
        return try {
            camera.render(world, tempFile)
            val bytes = Files.readAllBytes(tempFile)
            maybeUpdateReference(bytes)
            parsePpm(bytes)
        } finally {
            Files.deleteIfExists(tempFile)
            RandomSource.reset()
        }
    }

    private fun loadReferenceImage(): ByteArray =
        GZIPInputStream(
            requireNotNull(javaClass.getResourceAsStream("/reference-image.ppm.gz")) {
                "Missing reference image resource"
            }
        ).use { it.readBytes() }

    private fun parsePpm(bytes: ByteArray): PpmImage {
        val content = bytes.toString(StandardCharsets.UTF_8).trim()
        val tokens = content.split(Regex("\\s+"))
        require(tokens.size >= 4) { "Malformed PPM header" }
        require(tokens[0] == "P3") { "Only ASCII PPM supported" }
        val width = tokens[1].toInt()
        val height = tokens[2].toInt()
        val maxVal = tokens[3].toInt()
        require(maxVal == 255) { "Unexpected max color value ${'$'}maxVal" }
        val expectedValues = width * height * 3
        val remaining = tokens.drop(4)
        require(remaining.size >= expectedValues) { "Not enough pixel data" }
        val pixels = IntArray(expectedValues) { idx -> remaining[idx].toInt() }
        return PpmImage(width, height, pixels)
    }

    private fun diffStats(expected: PpmImage, actual: PpmImage): DiffStats {
        require(expected.pixels.size == actual.pixels.size) { "Pixel counts differ" }
        var sum = 0.0
        var maxDiff = 0
        var highDiffCount = 0
        expected.pixels.indices.forEach { idx ->
            val delta = abs(expected.pixels[idx] - actual.pixels[idx])
            sum += delta
            if (delta > maxDiff) maxDiff = delta
            if (delta >= HIGH_DIFF_THRESHOLD) highDiffCount += 1
        }
        val mean = sum / expected.pixels.size
        return DiffStats(mean, maxDiff, highDiffCount)
    }

    data class PpmImage(val width: Int, val height: Int, val pixels: IntArray)

    data class DiffStats(val meanAbsDiff: Double, val maxDiff: Int, val highDiffCount: Int)

    private fun maybeUpdateReference(bytes: ByteArray) {
        if (System.getenv("UPDATE_REFERENCE_IMAGE") == "1") {
            val target = Paths.get("src/test/resources/reference-image.ppm.gz")
            Files.newOutputStream(target).use { fileStream ->
                GZIPOutputStream(fileStream).use { it.write(bytes) }
            }
        }
    }

    companion object {
        private const val MEAN_ABS_DIFF_THRESHOLD = 0.0
        private const val HIGH_DIFF_THRESHOLD = 55
        private const val MAX_HIGH_DIFF_CHANNELS = 0
    }
}
