package rayTraceTypescript

data class Aabb(
    val x: Interval = Interval.EMPTY,
    val y: Interval = Interval.EMPTY,
    val z: Interval = Interval.EMPTY
) {

    constructor(firstBox: Aabb, secondBox: Aabb) : this(
        x = Interval(firstBox.x, secondBox.x),
        y = Interval(firstBox.y, secondBox.y),
        z = Interval(firstBox.z, secondBox.z),
    )

    companion object {
        fun fromPoints(a: Point, b: Point): Aabb {
            val ix = if (a[0] <= b[0]) Interval(a[0], b[0]) else Interval(b[0], a[0])
            val iy = if (a[1] <= b[1]) Interval(a[1], b[1]) else Interval(b[1], a[1])
            val iz = if (a[2] <= b[2]) Interval(a[2], b[2]) else Interval(b[2], a[2])
            return Aabb(ix, iy, iz)
        }
    }

    fun axisInterval(axis: Int): Interval = when (axis) {
        1 -> y
        2 -> z
        else -> x
    }

    fun longestAxis(): Int {
        val xSize = x.size()
        val ySize = y.size()
        val zSize = z.size()

        return if (xSize > ySize) {
            if (xSize > zSize) 0 else 2
        } else {
            if (ySize > zSize) 1 else 2
        }
    }

    fun hit(r: Ray, rayT: Interval): Boolean {
        val rayOrig = r.origin
        val rayDir = r.direction

        var tMin = rayT.min
        var tMax = rayT.max

        for (axis in 0 until 3) {
            val ax = axisInterval(axis)
            val invD = 1.0F / rayDir[axis]

            var t0 = (ax.min - rayOrig[axis]) * invD
            var t1 = (ax.max - rayOrig[axis]) * invD

            if (t0 > t1) {
                val tmp = t0
                t0 = t1
                t1 = tmp
            }

            if (t0 > tMin) tMin = t0
            if (t1 < tMax) tMax = t1

            if (tMax <= tMin) return false
        }

        return true
    }
}