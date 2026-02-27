package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Point

class CheckerTexture(scale: Float, private val even: Texture, private val odd: Texture) : Texture {
    private val invertedScale: Float = 1.0F / scale

    constructor(scale: Float, even: Color, odd: Color) : this(scale, SolidColorTexture(even), SolidColorTexture(odd))

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