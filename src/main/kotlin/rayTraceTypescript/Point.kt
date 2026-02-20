package rayTraceTypescript

class Point(x: Float, y: Float, z: Float): Vector(x, y, z) {
    constructor(x: Double, y: Double, z: Double) : this(x.toFloat(), y.toFloat(), z.toFloat())

    override fun set(x: Float, y: Float, z: Float): Point {
        super.set(x, y, z)
        return this
    }

    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y, z + other.z)
}
