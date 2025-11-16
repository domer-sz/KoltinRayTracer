package rayTraceTypescript

class Ray(val origin: Point, val direction: Vector) {
    fun at(t: Float): Point = Point(origin.x + t*direction.x, origin.y + t*direction.y, origin.z + t*direction.z)
}
