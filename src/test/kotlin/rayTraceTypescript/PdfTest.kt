package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.pdf.CosinePdf
import rayTraceTypescript.pdf.SpherePdf
import rayTraceTypescript.utils.Onb
import rayTraceTypescript.utils.RandomSource
import rayTraceTypescript.utils.pi
import kotlin.math.abs

/**
 * The books make two promises about a density: it integrates to one over the sphere of
 * directions, and the samples it draws are distributed the way its value() says. Both are
 * checked here by Monte Carlo, with a fixed seed so the numbers do not wander.
 */
class PdfTest {

    private val samples = 200_000

    private fun <T> seeded(block: () -> T): T {
        RandomSource.withSeed(20260924L)
        return try {
            block()
        } finally {
            RandomSource.reset()
        }
    }

    @Test
    fun `the sphere density is uniform and integrates to one`() {
        val pdf = SpherePdf()
        assertEquals(1.0f / (4.0f * pi), pdf.value(Vector(1f, 0f, 0f)), 1e-7f)

        val integral = seeded {
            var sum = 0.0
            repeat(samples) { sum += pdf.value(Vector.randomUnitVector()) * 4.0 * pi }
            sum / samples
        }
        assertEquals(1.0, integral, 0.01, "integral over the sphere")
    }

    @Test
    fun `the cosine density integrates to one over the sphere`() {
        val pdf = CosinePdf(Vector(0f, 0f, 1f))
        val integral = seeded {
            var sum = 0.0
            // Uniform directions, weighted by the area of the sphere of directions.
            repeat(samples) { sum += pdf.value(Vector.randomUnitVector()) * 4.0 * pi }
            sum / samples
        }
        assertEquals(1.0, integral, 0.02, "integral over the sphere")
    }

    @Test
    fun `cosine samples follow the density they report`() {
        val axis = Vector(0f, 1f, 0f)
        val pdf = CosinePdf(axis)

        // Integrating cos squared over the hemisphere has the answer 2*pi/3; drawing from the
        // cosine density and dividing by it has to land there too, which only works if
        // generate() and value() describe the same distribution.
        val estimate = seeded {
            var sum = 0.0
            repeat(samples) {
                val direction = Vector.unit(pdf.generate())
                val cosine = Vector.dotProduct(direction, axis)
                sum += (cosine * cosine) / pdf.value(direction)
            }
            sum / samples
        }
        assertEquals(2.0 * pi / 3.0, estimate, 0.02, "integral of cos^2 over the hemisphere")
    }

    @Test
    fun `cosine samples stay on the lit side`() {
        val axis = Vector(0f, 0f, 1f)
        val pdf = CosinePdf(axis)
        seeded {
            repeat(1000) {
                assertTrue(Vector.dotProduct(pdf.generate(), axis) >= -1e-6f, "sample went below the surface")
            }
        }
    }

    @Test
    fun `the basis is orthonormal and keeps the normal as its third axis`() {
        val normal = Vector(0.3f, -0.6f, 0.74f)
        val basis = Onb(normal)

        assertEquals(1f, basis.u.length(), 1e-4f)
        assertEquals(1f, basis.v.length(), 1e-4f)
        assertEquals(1f, basis.w.length(), 1e-4f)
        assertTrue(abs(Vector.dotProduct(basis.u, basis.v)) < 1e-4f)
        assertTrue(abs(Vector.dotProduct(basis.u, basis.w)) < 1e-4f)
        assertTrue(abs(Vector.dotProduct(basis.v, basis.w)) < 1e-4f)

        val transformed = basis.transform(Vector(0f, 0f, 1f))
        assertEquals(Vector.unit(normal).x, transformed.x, 1e-4f)
        assertEquals(Vector.unit(normal).y, transformed.y, 1e-4f)
        assertEquals(Vector.unit(normal).z, transformed.z, 1e-4f)
    }
}
