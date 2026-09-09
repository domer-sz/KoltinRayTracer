package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.gpu.GpuRenderer
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.render.CpuRenderer
import rayTraceTypescript.render.Renderer
import rayTraceTypescript.textures.CheckerTexture
import rayTraceTypescript.textures.ImageTexture
import rayTraceTypescript.textures.RtwImage
import rayTraceTypescript.utils.RandomSource
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * The GPU render must look like the CPU render. Both trace the same scene with the same
 * sample count; only the random streams differ, so the images are compared statistically.
 */
class GpuCpuComparisonTest {

    @Test
    fun `gpu matches the cpu render for every material`() {
        assumeOpenCl()
        assertLooksTheSame(materialsScene())
    }

    @Test
    fun `gpu matches the cpu render for an image texture`() {
        assumeOpenCl()
        assertLooksTheSame(imageTextureScene())
    }

    /**
     * Two path traced images of one scene never match pixel for pixel - the sampling noise
     * differs. The yardstick is therefore the noise itself: how far the GPU image sits from a
     * CPU image must be no worse than how far two CPU images with different seeds sit apart.
     */
    private fun assertLooksTheSame(world: Hittable) {
        val camera = testCamera()
        val cpu = render(CpuRenderer(), world, camera, SEED)
        val cpuOtherSeed = render(CpuRenderer(), world, camera, OTHER_SEED)
        val gpu = render(GpuRenderer(), world, camera, SEED)

        val noise = PpmSupport.diff(cpu, cpuOtherSeed, HIGH_DIFF_THRESHOLD)
        val actual = PpmSupport.diff(cpu, gpu, HIGH_DIFF_THRESHOLD)
        val allowedMean = noise.meanAbsDiff * NOISE_TOLERANCE

        assertTrue(
            actual.meanAbsDiff <= allowedMean,
            "GPU differs from the CPU render by ${actual.meanAbsDiff} on average (max ${actual.maxDiff}); " +
                "two CPU renders differ by ${noise.meanAbsDiff}, so anything above $allowedMean is a real difference"
        )
        assertTrue(
            actual.highDiffCount <= maxOf(noise.highDiffCount * 2, MIN_HIGH_DIFF_CHANNELS),
            "${actual.highDiffCount} channels differ by >= $HIGH_DIFF_THRESHOLD " +
                "(CPU-to-CPU noise produces ${noise.highDiffCount})"
        )
    }

    private fun render(renderer: Renderer, world: Hittable, camera: Camera, seed: Long): PpmSupport.PpmImage {
        val target: Path = Files.createTempFile("raytracer-backend-test", ".ppm")
        RandomSource.withSeed(seed)
        return try {
            renderer.render(world, camera.initialize(), target)
            PpmSupport.parse(Files.readAllBytes(target))
        } finally {
            Files.deleteIfExists(target)
            RandomSource.reset()
        }
    }

    private fun testCamera() = Camera().apply {
        aspectRatio = 16.0f / 9.0f
        imageWidth = 96
        samplesPerPixel = 64
        maxReflectionDepth = 12
        vfov = 25.0f
        lookFrom = Point(6.0f, 2.0f, 6.0f)
        lookAt = Point(0.0f, 0.5f, 0.0f)
        vUp = Vector(0.0f, 1.0f, 0.0f)
        defocusAngle = 0.4f
        focusDistance = 8.0f
    }

    /** Checker ground, a moving Lambertian sphere, a fuzzy Metal and a Dielectric, behind a BVH. */
    private fun materialsScene(): Hittable {
        val ground = Sphere(
            Point(0.0f, -1000.0f, 0.0f),
            1000.0f,
            Lambertian(CheckerTexture(0.32f, Color(0.2f, 0.3f, 0.1f), Color(0.9f, 0.9f, 0.9f)))
        )
        val moving = Sphere(
            Point(-1.6f, 0.5f, 0.0f),
            Point(-1.6f, 0.9f, 0.0f),
            0.5f,
            Lambertian(Color(0.8f, 0.3f, 0.3f))
        )
        val metal = Sphere(Point(0.0f, 0.6f, 0.0f), 0.6f, Metal(Color(0.7f, 0.6f, 0.5f), 0.15f))
        val glass = Sphere(Point(1.6f, 0.5f, 0.4f), 0.5f, Dielectric(1.5f))
        return HittableList(mutableListOf(BvhNode(HittableList(mutableListOf(ground, moving, metal, glass)))))
    }

    private fun imageTextureScene(): Hittable {
        val globe = Sphere(Point(0.0f, 0.5f, 0.0f), 1.2f, Lambertian(ImageTexture(syntheticImage())))
        return HittableList(mutableListOf(globe))
    }

    /** A tiny generated texture; the repository ships no image assets. */
    private fun syntheticImage(): RtwImage {
        val file = Files.createTempFile("raytracer-texture", ".png")
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val rgb = if ((x + y) % 2 == 0) 0xC03040 else 0x2050B0
                image.setRGB(x, y, rgb)
            }
        }
        ImageIO.write(image, "png", file.toFile())
        return try {
            RtwImage().apply { check(load(file.toString())) { "Could not load the generated texture" } }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun assumeOpenCl() {
        val reason = GpuRenderer.unavailabilityReason()
        assumeTrue(reason == null, "No OpenCL device available: $reason")
    }

    companion object {
        private const val SEED = 4242L
        private const val OTHER_SEED = 1337L

        private const val HIGH_DIFF_THRESHOLD = 64
        /** Headroom over the CPU-to-CPU noise floor, for float differences between devices. */
        private const val NOISE_TOLERANCE = 1.5f
        private const val MIN_HIGH_DIFF_CHANNELS = 8
    }
}
