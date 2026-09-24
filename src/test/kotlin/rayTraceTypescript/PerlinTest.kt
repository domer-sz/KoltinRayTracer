package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.textures.NoiseTexture
import rayTraceTypescript.textures.Perlin
import rayTraceTypescript.utils.RandomSource
import kotlin.math.abs

class PerlinTest {

    private fun seeded(seed: Long): Perlin {
        RandomSource.withSeed(seed)
        return try {
            Perlin()
        } finally {
            RandomSource.reset()
        }
    }

    private val samples = listOf(
        Point(0f, 0f, 0f), Point(0.5f, 1.25f, -3.75f), Point(12.3f, -4.6f, 7.9f), Point(-0.1f, -0.1f, -0.1f)
    )

    @Test
    fun `noise stays within the lattice range`() {
        val perlin = seeded(7L)
        samples.forEach { point ->
            val value = perlin.noise(point)
            assertTrue(abs(value) <= 1.0f, "noise at $point was $value")
        }
    }

    @Test
    fun `the same seed builds the same noise field`() {
        val first = seeded(4242L)
        val second = seeded(4242L)
        samples.forEach { point ->
            assertEquals(first.noise(point), second.noise(point), 0f, "noise at $point")
            assertEquals(first.turbulence(point), second.turbulence(point), 0f, "turbulence at $point")
        }
    }

    @Test
    fun `turbulence sums octaves into a positive value`() {
        val perlin = seeded(11L)
        samples.forEach { point ->
            val turbulence = perlin.turbulence(point)
            assertTrue(turbulence >= 0f, "turbulence at $point was $turbulence")
            assertTrue(turbulence <= 2.0f, "turbulence at $point was $turbulence")
        }
    }

    @Test
    fun `the marble texture is grey and inside the unit range`() {
        val texture = NoiseTexture(4.0f, seeded(3L))
        samples.forEach { point ->
            val color = texture.value(0f, 0f, point)
            assertEquals(color.r, color.g, 0f, "grey at $point")
            assertEquals(color.r, color.b, 0f, "grey at $point")
            assertTrue(color.r in 0f..1f, "value at $point was ${color.r}")
        }
    }
}
