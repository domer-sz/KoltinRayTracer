package rayTraceTypescript.objects

import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material

class Hit(ray: Ray, val point: Point, outwardNormal: Vector, val material: Material, val t: Float) {
    var normal: Vector = outwardNormal
    var frontFace: Boolean = true

    init {
        setFrontFace(ray, outwardNormal)
    }

    fun setFrontFace(ray: Ray, outwardNormal: Vector) {
        frontFace = Vector.dotProduct(ray.direction, outwardNormal) < 0.0f
        normal = if (frontFace) outwardNormal else -outwardNormal
    }
}
