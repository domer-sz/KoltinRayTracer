package rayTraceTypescript

class Interval(val min: Float, val max: Float) {

    constructor(a: Interval, b: Interval) : this(
        min = kotlin.math.min(a.min, b.min),
        max = kotlin.math.max(a.max, b.max)
    )

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

    fun size(): Float = max - min

    companion object {
        val EMPTY = Interval(
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        )
        val UNIVERSE = Interval(
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY
        )
    }
}
