package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import kotlin.math.sin

/**
 * The marble texture from The Next Week: a sine wave along z, with the phase pushed around by
 * turbulence, so the bands fold into veins instead of running straight.
 */
class NoiseTexture(val scale: Float, val perlin: Perlin = Perlin()) : Texture {
    override fun value(u: Float, v: Float, point: Point): Color =
        Color(0.5f, 0.5f, 0.5f) * (1.0f + sin(scale * point.z + 10.0f * perlin.turbulence(point)))
}
