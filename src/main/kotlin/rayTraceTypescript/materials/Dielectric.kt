package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class Dielectric(val ri: Double) : Material {
    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult? {
        val attenuation = Color(1.0, 1.0, 1.0)
        val refractionRatio = if (hit.frontFace) 1.0 / ri else ri
        val unitDirection = rayIn.direction.unit()
        val cosTheta = min(Vector.Companion.dotProduct(unitDirection.negate(), hit.normal), 1.0)
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

        val cannotRefract = refractionRatio * sinTheta > 1.0
        val useReflect = cannotRefract || reflectance(cosTheta, refractionRatio) > Random.nextDouble()
        val direction = if (useReflect)
            Vector.Companion.reflect(unitDirection, hit.normal)
        else
            Vector.Companion.refract(unitDirection, hit.normal, refractionRatio)

        val scattered = Ray(hit.point, direction)
        return ScatteredResult(attenuation, scattered)
    }

    private fun reflectance(cosine: Double, ri: Double): Double {
        var r0 = (1 - ri) / (1 + ri)
        r0 *= r0
        return r0 + (1 - r0) * Math.pow((1 - cosine), 5.0)
    }
}