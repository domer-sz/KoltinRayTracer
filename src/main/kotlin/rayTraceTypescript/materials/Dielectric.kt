package rayTraceTypescript.materials

import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import rayTraceTypescript.utils.RandomSource

class Dielectric(val ri: Float) : Material {
    constructor(ri: Double) : this(ri.toFloat())

    private val unitDirection = Vector(0.0f, 0.0f, 0.0f)
    private val direction = Vector(0.0f, 0.0f, 0.0f)

    override fun scatter(rayIn: Ray, hit: Hit, outScatter: ScatteredResult): Boolean {
        val refractionRatio = if (hit.frontFace) 1.0f / ri else ri
        Vector.unit(rayIn.direction, unitDirection)
        val cosTheta = min(-Vector.dotProduct(unitDirection, hit.normal), 1.0f)
        val sinTheta = sqrt(1.0f - cosTheta * cosTheta)

        val cannotRefract = refractionRatio * sinTheta > 1.0f
        val useReflect = cannotRefract || reflectance(cosTheta, refractionRatio) > RandomSource.nextFloat()
        if (useReflect) {
            Vector.reflect(unitDirection, hit.normal, direction)
        } else {
            Vector.refract(unitDirection, hit.normal, refractionRatio, direction)
        }

        outScatter.set(
            1.0f,
            1.0f,
            1.0f,
            hit.point.x,
            hit.point.y,
            hit.point.z,
            direction.x,
            direction.y,
            direction.z,
            rayIn.time,
        )
        return true
    }

    private fun reflectance(cosine: Float, ri: Float): Float {
        var r0 = (1f - ri) / (1f + ri)
        r0 *= r0
        return r0 + (1f - r0) * (1f - cosine).toDouble().pow(5.0).toFloat()
    }
}
