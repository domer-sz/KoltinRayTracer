package rayTraceTypescript

import kotlin.math.*
import rayTraceTypescript.Interval

class Color(val r: Float, val g: Float, val b: Float) {
    constructor(r: Double, g: Double, b: Double) : this(r.toFloat(), g.toFloat(), b.toFloat())

    fun colorR(): Int {
        return toChannel(r)
    }
    fun colorG(): Int {
        return toChannel(g)
    }
    fun colorB(): Int {
        return toChannel(b)
    }

    operator fun plus(other: Color): Color = Color(r + other.r, g + other.g, b + other.b)
    operator fun minus(other: Color): Color = Color(r - other.r, g - other.g, b - other.b)
    fun scale(s: Float): Color = Color(r * s, g * s, b * s)
    operator fun times(s: Float): Color = scale(s)
    fun divide(s: Float): Color = Color(r / s, g / s, b / s)
    operator fun div(s: Float): Color = divide(s)
    fun multiply(other: Color): Color = Color(r * other.r, g * other.g, b * other.b)
    operator fun times(other: Color): Color = multiply(other)

    fun length(): Float = sqrt(r*r + g*g + b*b)
    fun unit(): Color = divide(length())

    override fun toString(): String = "$"+"r $"+"g $"+"b"
    fun toStringColor(): String = "${colorR()} ${colorG()} ${colorB()}"

    companion object {
        private val INTENSITY = Interval(0.0f, 0.999f)

        private fun linearToGammaColor(linear: Float): Float =
            if (linear > 0.0f) sqrt(linear) else 0.0f

        @JvmStatic
        fun toChannel(linear: Float): Int =
            floor(256.0f * INTENSITY.clamp(linearToGammaColor(linear))).toInt()

        @JvmStatic fun dotProduct(u: Color, v: Color): Float = u.r*v.r + u.g*v.g + u.b*v.b
    }
}
