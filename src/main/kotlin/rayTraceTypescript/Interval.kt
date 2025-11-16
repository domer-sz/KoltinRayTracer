package rayTraceTypescript

class Interval(val min: Float, val max: Float) {
    fun contains(x: Float): Boolean = x in min..max
    fun surrounds(x: Float): Boolean = x > min && x < max
    fun clamp(x: Float): Float = when {
        x < min -> min
        x > max -> max
        else -> x
    }
}
