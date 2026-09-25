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

/** Directions aimed at something worth hitting - a light - rather than at the room. */
class HittablePdf(private val objects: rayTraceTypescript.objects.Hittable, private val origin: rayTraceTypescript.Point) : Pdf {
    override fun value(direction: Vector): Float = objects.pdfValue(origin, direction)
    override fun generate(): Vector = objects.random(origin)
}

/**
 * Half the samples from each density. Aiming only at the lights misses everything they do not
 * light directly, and aiming only by the surface misses the lights; mixing keeps both.
 */
class MixturePdf(private val first: Pdf, private val second: Pdf) : Pdf {
    override fun value(direction: Vector): Float =
        0.5f * first.value(direction) + 0.5f * second.value(direction)

    override fun generate(): Vector =
        if (RandomSource.nextFloat() < 0.5f) first.generate() else second.generate()
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
