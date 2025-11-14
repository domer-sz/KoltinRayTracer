package rayTraceTypescript

import kotlin.math.*
import rayTraceTypescript.Interval

class Color(val r: Double, val g: Double, val b: Double) {
    private val intensity = Interval(0.0, 0.999)

    fun colorR(): Int {
        val rGamma = lineatToGammaColor(r)
        return floor(256.0 * intensity.clamp(rGamma)).toInt()
    }
    fun colorG(): Int {
        val gGamma = lineatToGammaColor(g)
        return floor(256.0 * intensity.clamp(gGamma)).toInt()
    }
    fun colorB(): Int {
        val bGamma = lineatToGammaColor(b)
        return floor(256.0 * intensity.clamp(bGamma)).toInt()
    }

    private fun lineatToGammaColor(linear: Double): Double =
        if (linear > 0.0) sqrt(linear) else 0.0

    fun plus(other: Color): Color = Color(r + other.r, g + other.g, b + other.b)
    fun minus(other: Color): Color = Color(r - other.r, g - other.g, b - other.b)
    fun scale(s: Double): Color = Color(r*s, g*s, b*s)
    fun divide(s: Double): Color = Color(r/s, g/s, b/s)
    fun multiply(other: Color): Color = Color(r*other.r, g*other.g, b*other.b)

    fun length(): Double = sqrt(r*r + g*g + b*b)
    fun unit(): Color = divide(length())

    override fun toString(): String = "$"+"r $"+"g $"+"b"
    fun toStringColor(): String = "${colorR()} ${colorG()} ${colorB()}"

    companion object {
        @JvmStatic fun dotProduct(u: Color, v: Color): Double = u.r*v.r + u.g*v.g + u.b*v.b
    }
}
