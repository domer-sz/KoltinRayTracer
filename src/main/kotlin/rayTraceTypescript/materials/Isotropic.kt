package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture

/** The phase function inside a volume: a scattered ray leaves in any direction at all. */
class Isotropic(val texture: Texture) : Material {
    constructor(color: Color) : this(SolidColorTexture(color))

    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult =
        ScatteredResult(
            texture.value(hit.u, hit.v, hit.point),
            Ray(hit.point, Vector.randomUnitVector(), rayIn.time)
        )
}
