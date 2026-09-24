package rayTraceTypescript.objects

import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material

/**
 * Where a ray met a surface. [normal] always points against the ray, and [frontFace] records
 * which side that was, so materials can tell inside from outside.
 */
class Hit(
    val point: Point,
    val normal: Vector,
    val frontFace: Boolean,
    val material: Material,
    val t: Float,
    val u: Float,
    val v: Float
) {
    /** The same surface hit, moved by an instance transform. */
    fun placedAt(point: Point, normal: Vector = this.normal): Hit =
        Hit(point, normal, frontFace, material, t, u, v)

    companion object {
        /** Builds a hit with the outward normal turned to face the ray, as the books do. */
        fun facing(
            ray: Ray,
            point: Point,
            outwardNormal: Vector,
            material: Material,
            t: Float,
            u: Float,
            v: Float
        ): Hit {
            val frontFace = Vector.dotProduct(ray.direction, outwardNormal) < 0.0f
            return Hit(point, if (frontFace) outwardNormal else -outwardNormal, frontFace, material, t, u, v)
        }
    }
}
