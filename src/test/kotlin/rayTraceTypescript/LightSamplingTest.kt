package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.utils.RandomSource
import rayTraceTypescript.utils.pi

/**
 * A light's density has to describe the directions its own sampler produces: integrating it
 * over every direction has to give one, and the directions it draws have to be the ones with a
 * density above zero. Both are checked by Monte Carlo from a fixed seed.
 */
class LightSamplingTest {

    private val white = Lambertian(Color(1f, 1f, 1f))
    private val origin = Point(0f, 0f, 0f)

    private val quad = Quad(Point(-1f, 2f, -1f), Vector(2f, 0f, 0f), Vector(0f, 0f, 2f), white)
    private val sphere = Sphere(Point(0f, 3f, 0f), 1f, white)

    private fun <T> seeded(block: () -> T): T {
        RandomSource.withSeed(4242L)
        return try {
            block()
        } finally {
            RandomSource.reset()
        }
    }

    private fun integrate(target: Hittable): Double = seeded {
        var sum = 0.0
        val samples = 400_000
        repeat(samples) { sum += target.pdfValue(origin, Vector.randomUnitVector()) * 4.0 * pi }
        sum / samples
    }

    @Test
    fun `a quad light's density integrates to one`() {
        assertEquals(1.0, integrate(quad), 0.05)
    }

    @Test
    fun `a sphere light's density integrates to one`() {
        assertEquals(1.0, integrate(sphere), 0.05)
    }

    @Test
    fun `a list of lights integrates to one as well`() {
        assertEquals(1.0, integrate(HittableList(mutableListOf(quad, sphere))), 0.05)
    }

    @Test
    fun `sampled directions are directions the density knows about`() {
        seeded {
            repeat(500) {
                listOf(quad, sphere).forEach { light ->
                    val direction = light.random(origin)
                    assertTrue(
                        light.pdfValue(origin, direction) > 0f,
                        "${light::class.java.simpleName} drew a direction its density calls impossible"
                    )
                }
            }
        }
    }

    @Test
    fun `the density falls off with distance and with a glancing angle`() {
        val close = quad.pdfValue(origin, Vector(0f, 1f, 0f))
        val far = Quad(Point(-1f, 8f, -1f), Vector(2f, 0f, 0f), Vector(0f, 0f, 2f), white)
            .pdfValue(origin, Vector(0f, 1f, 0f))
        assertTrue(far > close, "a light four times further away covers less of the sky, so aiming at it is rarer")
    }
}
