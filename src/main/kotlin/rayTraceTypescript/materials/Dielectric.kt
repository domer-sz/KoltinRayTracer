package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import rayTraceTypescript.utils.RandomSource

class Dielectric(val ri: Float) : Material {
    constructor(ri: Double) : this(ri.toFloat())
    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult? {
        val attenuation = Color(1.0f, 1.0f, 1.0f)
        val refractionRatio = if (hit.frontFace) 1.0f / ri else ri
        val unitDirection = rayIn.direction.unit()
        val cosTheta = min(Vector.Companion.dotProduct(-unitDirection, hit.normal), 1.0f)
        val sinTheta = sqrt(1.0f - cosTheta * cosTheta)

        val cannotRefract = refractionRatio * sinTheta > 1.0f
        val useReflect = cannotRefract || reflectance(cosTheta, refractionRatio) > RandomSource.nextFloat()
        val direction = if (useReflect)
            Vector.Companion.reflect(unitDirection, hit.normal)
        else
            Vector.Companion.refract(unitDirection, hit.normal, refractionRatio)

        val scattered = Ray(hit.point, direction)
        return ScatteredResult(attenuation, scattered)
    }

    private fun reflectance(cosine: Float, ri: Float): Float {
        var r0 = (1f - ri) / (1f + ri)
        r0 *= r0
        return r0 + (1f - r0) * (1f - cosine).toDouble().pow(5.0).toFloat()
    }
}
