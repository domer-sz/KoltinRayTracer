package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit

class Metal(val albedo: Color, val fuzz: Float) : Material {
    constructor(albedo: Color, fuzz: Double) : this(albedo, fuzz.toFloat())

    override fun scatter(rayIn: Ray, hit: Hit): ScatterRecord {
        // Reflect, then blur the reflection by nudging it towards a random direction.
        val reflected = Vector.unit(Vector.reflect(rayIn.direction, hit.normal)) +
            Vector.randomUnitVector() * fuzz
        return ScatterRecord(albedo, skipPdfRay = Ray(hit.point, reflected, rayIn.time))
    }
}
