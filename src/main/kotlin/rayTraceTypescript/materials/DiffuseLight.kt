package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture

/** A surface that only emits: rays end here, carrying the light away. */
class DiffuseLight(val texture: Texture) : Material {
    constructor(color: Color) : this(SolidColorTexture(color))

    override fun scatter(rayIn: Ray, hit: Hit): ScatteredResult? = null

    override fun emitted(u: Float, v: Float, point: Point): Color = texture.value(u, v, point)
}
