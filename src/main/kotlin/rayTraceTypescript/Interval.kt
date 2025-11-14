package rayTraceTypescript

class Interval(val min: Double, val max: Double) {
    fun contains(x: Double): Boolean = x in min..max
    fun surrounds(x: Double): Boolean = x > min && x < max
    fun clamp(x: Double): Double = when {
        x < min -> min
        x > max -> max
        else -> x
    }
}
