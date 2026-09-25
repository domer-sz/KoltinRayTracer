package rayTraceTypescript.utils

import rayTraceTypescript.Vector

/**
 * An orthonormal basis built around a normal, so directions can be drawn in a convenient
 * frame (z up) and then turned into world space.
 */
class Onb(normal: Vector) {

    val w: Vector = Vector.unit(normal)
    val v: Vector
    val u: Vector

    init {
        // Any axis not nearly parallel to w will do as a starting point.
        val a = if (kotlin.math.abs(w.x) > 0.9f) Vector(0.0f, 1.0f, 0.0f) else Vector(1.0f, 0.0f, 0.0f)
        v = Vector.unit(Vector.cross(w, a))
        u = Vector.cross(w, v)
    }

    fun transform(local: Vector): Vector = u * local.x + v * local.y + w * local.z
}
