package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit

class Lambertian(val albedo: Color) : Material {
    private val scatterDirection = Vector(0.0f, 0.0f, 0.0f)

    override fun scatter(rayIn: Ray, hit: Hit, outScatter: ScatteredResult): Boolean {
        Vector.randomOnHemisphere(hit.normal, scatterDirection)
        if (scatterDirection.nearZero()) {
            scatterDirection.set(hit.normal)
        }
        outScatter.set(
            albedo.r,
            albedo.g,
            albedo.b,
            hit.point.x,
            hit.point.y,
            hit.point.z,
            scatterDirection.x,
            scatterDirection.y,
            scatterDirection.z,
            rayIn.time,
        )
        return true
    }
}
