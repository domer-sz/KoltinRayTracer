package rayTraceTypescript.pdf

import rayTraceTypescript.Vector
import rayTraceTypescript.utils.Onb
import rayTraceTypescript.utils.RandomSource
import rayTraceTypescript.utils.pi
import kotlin.math.max

/**
 * A probability density over directions: [value] says how likely a direction is, [generate]
 * draws one. The renderer samples from a density and divides by it, which is what turns a
 * scattering rule into an unbiased estimate.
 */
interface Pdf {
    fun value(direction: Vector): Float
    fun generate(): Vector
}

/** Every direction equally likely. */
class SpherePdf : Pdf {
    override fun value(direction: Vector): Float = 1.0f / (4.0f * pi)
    override fun generate(): Vector = Vector.randomUnitVector()
}

/** Directions around a normal, weighted by the cosine the surface actually reflects with. */
class CosinePdf(normal: Vector) : Pdf {
    private val basis = Onb(normal)

    override fun value(direction: Vector): Float {
        val cosine = Vector.dotProduct(Vector.unit(direction), basis.w)
        return max(0.0f, cosine / pi)
    }

    override fun generate(): Vector = basis.transform(Vector.randomCosineDirection())
}
