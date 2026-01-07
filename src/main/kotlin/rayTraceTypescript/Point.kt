package rayTraceTypescript

class Point(x: Float, y: Float, z: Float): Vector(x, y, z) {
    constructor(x: Double, y: Double, z: Double) : this(x.toFloat(), y.toFloat(), z.toFloat())

    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y, z + other.z)
}
