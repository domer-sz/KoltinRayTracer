package rayTraceTypescript

class Interval(val min: Float, val max: Float) {
    fun contains(x: Float): Boolean = x in min..max
    fun surrounds(x: Float): Boolean = x > min && x < max
    fun clamp(x: Float): Float = when {
        x < min -> min
        x > max -> max
        else -> x
    }

    fun expand(delta: Float): Interval {
        val padding = delta/2
        return Interval(min - padding, max + padding)
    }
}
