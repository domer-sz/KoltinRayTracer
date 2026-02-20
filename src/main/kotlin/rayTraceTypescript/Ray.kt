package rayTraceTypescript

class Ray(var origin: Point, var direction: Vector, var time: Float) {

    constructor(origin: Point, direction: Vector) : this(origin, direction, 0F)

    fun set(origin: Point, direction: Vector, time: Float): Ray {
        this.origin.set(origin.x, origin.y, origin.z)
        this.direction.set(direction.x, direction.y, direction.z)
        this.time = time
        return this
    }

    fun set(
        originX: Float,
        originY: Float,
        originZ: Float,
        directionX: Float,
        directionY: Float,
        directionZ: Float,
        time: Float,
    ): Ray {
        origin.set(originX, originY, originZ)
        direction.set(directionX, directionY, directionZ)
        this.time = time
        return this
    }

    fun copyFrom(other: Ray): Ray =
        set(
            other.origin.x,
            other.origin.y,
            other.origin.z,
            other.direction.x,
            other.direction.y,
            other.direction.z,
            other.time,
        )

    fun at(t: Float): Point =
        Point(origin.x + t * direction.x, origin.y + t * direction.y, origin.z + t * direction.z)
}
