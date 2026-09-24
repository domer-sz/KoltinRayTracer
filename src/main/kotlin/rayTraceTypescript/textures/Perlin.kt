package rayTraceTypescript.textures

import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.utils.RandomSource
import kotlin.math.abs
import kotlin.math.floor

/**
 * Perlin noise: a lattice of random unit vectors, hashed by three permutation tables and
 * interpolated with Hermite smoothing, which keeps the result free of the blocky artefacts
 * plain trilinear interpolation leaves behind.
 *
 * The tables are drawn once, when the object is built - the GPU backend uploads exactly these
 * numbers, so both renderers see the same noise field.
 */
class Perlin {

    val randomVectors: Array<Vector> = Array(POINT_COUNT) { Vector.unit(Vector.random(-1.0f, 1.0f)) }
    val permX: IntArray = generatePermutation()
    val permY: IntArray = generatePermutation()
    val permZ: IntArray = generatePermutation()

    fun noise(point: Point): Float {
        val u = point.x - floor(point.x)
        val v = point.y - floor(point.y)
        val w = point.z - floor(point.z)

        val i = floor(point.x).toInt()
        val j = floor(point.y).toInt()
        val k = floor(point.z).toInt()

        val corners = Array(2) { di -> Array(2) { dj -> Array(2) { dk ->
            randomVectors[permX[(i + di) and 255] xor permY[(j + dj) and 255] xor permZ[(k + dk) and 255]]
        } } }

        return interpolate(corners, u, v, w)
    }

    /** Sums progressively finer, weaker octaves of noise; the marble veins come from this. */
    fun turbulence(point: Point, depth: Int = DEFAULT_TURBULENCE_DEPTH): Float {
        var accumulated = 0.0f
        var sample = point
        var weight = 1.0f
        repeat(depth) {
            accumulated += weight * noise(sample)
            weight *= 0.5f
            sample = Point(sample.x * 2.0f, sample.y * 2.0f, sample.z * 2.0f)
        }
        return abs(accumulated)
    }

    private fun interpolate(corners: Array<Array<Array<Vector>>>, u: Float, v: Float, w: Float): Float {
        val uu = u * u * (3.0f - 2.0f * u)
        val vv = v * v * (3.0f - 2.0f * v)
        val ww = w * w * (3.0f - 2.0f * w)
        var accumulated = 0.0f

        for (i in 0..1) {
            for (j in 0..1) {
                for (k in 0..1) {
                    val weight = Vector(u - i, v - j, w - k)
                    accumulated +=
                        (i * uu + (1 - i) * (1.0f - uu)) *
                        (j * vv + (1 - j) * (1.0f - vv)) *
                        (k * ww + (1 - k) * (1.0f - ww)) *
                        Vector.dotProduct(corners[i][j][k], weight)
                }
            }
        }
        return accumulated
    }

    private fun generatePermutation(): IntArray {
        val permutation = IntArray(POINT_COUNT) { it }
        for (i in POINT_COUNT - 1 downTo 1) {
            val target = RandomSource.nextInt(0, i)
            val tmp = permutation[i]
            permutation[i] = permutation[target]
            permutation[target] = tmp
        }
        return permutation
    }

    companion object {
        const val POINT_COUNT = 256
        const val DEFAULT_TURBULENCE_DEPTH = 7
    }
}
