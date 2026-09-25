package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture

/** A surface that only emits: rays end here, carrying the light away. */
class DiffuseLight(val texture: Texture) : Material {
    constructor(color: Color) : this(SolidColorTexture(color))

    /**
     * Light leaves only through the front. A lamp lit from behind would otherwise send light
     * out of the back of the ceiling, and sampling it from there would be wasted work.
     */
    override fun emitted(rayIn: Ray, hit: Hit): Color =
        if (!hit.frontFace) Color(0.0f, 0.0f, 0.0f) else texture.value(hit.u, hit.v, hit.point)
}
