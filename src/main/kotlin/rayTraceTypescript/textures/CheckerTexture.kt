package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Point

class CheckerTexture(scale: Float, internal val even: Texture, internal val odd: Texture) : Texture {
    internal val invertedScale: Float = 1.0F / scale

    constructor(scale: Float, even: Color, odd: Color) : this(scale, SolidColorTexture(even), SolidColorTexture(odd))

    /**
     * Note that a surface lying exactly on a cell boundary (a plane at y = 0 with an even
     * scale, say) is a coin toss: the parity then depends on the last bit of the hit point,
     * and two renderers - or one renderer with different optimizations - can disagree across
     * the whole surface. Offset such geometry rather than trusting the tie.
     */
    override fun value(u: Float, v: Float, point: Point): Color {
        val xInteger = kotlin.math.floor(invertedScale * point.x).toInt()
        val yInteger = kotlin.math.floor(invertedScale * point.y).toInt()
        val zInteger = kotlin.math.floor(invertedScale * point.z).toInt()

        val isEven = (xInteger + yInteger + zInteger) % 2 == 0

        return if (isEven) {
            even.value(u, v, point)
        } else {
            odd.value(u, v, point)
        }
    }
}