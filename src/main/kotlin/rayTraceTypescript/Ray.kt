package rayTraceTypescript

class Ray(val origin: Point, val direction: Vector, val time: Float) {

    constructor(origin: Point, direction: Vector) :this(origin, direction, 0F)
    fun at(t: Float): Point = Point(origin.x + t*direction.x, origin.y + t*direction.y, origin.z + t*direction.z)
}
