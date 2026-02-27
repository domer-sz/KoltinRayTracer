package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture

class Lambertian(val texture: Texture) : Material {
    constructor(color: Color): this(SolidColorTexture(color))
    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult? {
        var direction = Vector.Companion.randomOnHemisphere(hit.normal)
        if (direction.nearZero()) direction = hit.normal
        val scattered = Ray(hit.point, direction, rayIn.time)
        return ScatteredResult(texture.value(hit.u, hit.v, hit.point), scattered)
    }
}