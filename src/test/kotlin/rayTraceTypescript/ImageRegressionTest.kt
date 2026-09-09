package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import rayTraceTypescript.render.CpuRenderer
import rayTraceTypescript.utils.RandomSource

class ImageRegressionTest {
    @Test
    fun `rendered ppm stays close to golden image`() {
        val reference = PpmSupport.parse(loadReferenceImage())
        val actual = renderCurrentScene()
        assertEquals(reference.width, actual.width, "reference width")
        assertEquals(reference.height, actual.height, "reference height")

        val diff = PpmSupport.diff(reference, actual, HIGH_DIFF_THRESHOLD)
        // Ziarno RNG ustawiamy w teście, więc oczekujemy zerowych różnic między renderami.
        assertTrue(
            diff.meanAbsDiff <= MEAN_ABS_DIFF_THRESHOLD,
            "Mean absolute difference ${diff.meanAbsDiff} exceeds $MEAN_ABS_DIFF_THRESHOLD"
        )
        assertTrue(
            diff.highDiffCount <= MAX_HIGH_DIFF_CHANNELS,
            "${diff.highDiffCount} channels differ by >= $HIGH_DIFF_THRESHOLD (max ${diff.maxDiff})"
        )
    }

    private fun renderCurrentScene(): PpmSupport.PpmImage {
        val camera = Camera().apply {
            aspectRatio = 16.0f / 9.0f
            imageWidth = 300
            samplesPerPixel = 50
            maxReflectionDepth = 45
            vfov = 20.0f
            lookFrom = Point(13.0f, 2.0f, 3.0f)
            lookAt = Point(0.0f, 0.0f, 0.0f)
            vUp = Vector(0.0f, 1.0f, 0.0f)
            defocusAngle = 0.6f
            focusDistance = 10.0f
        }
        val world = prepareWorld()
        val tempFile = Files.createTempFile("raytracer-image-test", ".ppm")
        RandomSource.withSeed(4242L)
        return try {
            // The golden image is the CPU reference: pinned seed, sequential sampling.
            CpuRenderer().render(world, camera.initialize(), tempFile)
            val bytes = Files.readAllBytes(tempFile)
            maybeUpdateReference(bytes)
            PpmSupport.parse(bytes)
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

    private fun maybeUpdateReference(bytes: ByteArray) {
        if (System.getenv("UPDATE_REFERENCE_IMAGE") == "1") {
            val target = Paths.get("src/test/resources/reference-image.ppm.gz")
            Files.newOutputStream(target).use { fileStream ->
                GZIPOutputStream(fileStream).use { it.write(bytes) }
            }
        }
    }

    companion object {
        private const val MEAN_ABS_DIFF_THRESHOLD = 0.0f
        private const val HIGH_DIFF_THRESHOLD = 55
        private const val MAX_HIGH_DIFF_CHANNELS = 0
    }
}
