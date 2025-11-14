package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit

class Lambertian(val albedo: Color) : Material {
    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult? {
        var direction = Vector.Companion.randomOnHemisphere(hit.normal)
        if (direction.nearZero()) direction = hit.normal
        val scattered = Ray(hit.point, direction)
        return ScatteredResult(albedo, scattered)
    }
}