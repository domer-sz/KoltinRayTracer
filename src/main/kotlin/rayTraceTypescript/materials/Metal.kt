package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit

class Metal(val albedo: Color, val fuzz: Double) : Material {
    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult? {
        val unitDirection = rayIn.direction.unit()
        val reflected = Vector.reflect(unitDirection, hit.normal)
        val scattered = Ray(hit.point, reflected.plus(Vector.randomInUnitSphere().scale(fuzz)))
        return if (Vector.dotProduct(scattered.direction, hit.normal) > 0.0) ScatteredResult(
            albedo,
            scattered
        ) else null
    }
}