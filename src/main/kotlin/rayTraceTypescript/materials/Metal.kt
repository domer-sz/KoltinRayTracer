package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit

class Metal(val albedo: Color, val fuzz: Float) : Material {
    constructor(albedo: Color, fuzz: Double) : this(albedo, fuzz.toFloat())

    private val unitDirection = Vector(0.0f, 0.0f, 0.0f)
    private val reflected = Vector(0.0f, 0.0f, 0.0f)
    private val fuzzDirection = Vector(0.0f, 0.0f, 0.0f)
    private val scatteredDirection = Vector(0.0f, 0.0f, 0.0f)

    override fun scatter(rayIn: Ray, hit: Hit, outScatter: ScatteredResult): Boolean {
        Vector.unit(rayIn.direction, unitDirection)
        Vector.reflect(unitDirection, hit.normal, reflected)
        Vector.randomInUnitSphere(fuzzDirection)

        scatteredDirection.set(
            reflected.x + fuzzDirection.x * fuzz,
            reflected.y + fuzzDirection.y * fuzz,
            reflected.z + fuzzDirection.z * fuzz,
        )

        if (Vector.dotProduct(scatteredDirection, hit.normal) <= 0.0f) {
            return false
        }

        outScatter.set(
            albedo.r,
            albedo.g,
            albedo.b,
            hit.point.x,
            hit.point.y,
            hit.point.z,
            scatteredDirection.x,
            scatteredDirection.y,
            scatteredDirection.z,
            rayIn.time,
        )
        return true
    }
}
