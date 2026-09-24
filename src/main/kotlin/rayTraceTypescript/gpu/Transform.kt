package rayTraceTypescript.gpu

import rayTraceTypescript.Point
import rayTraceTypescript.Vector

/**
 * A rotation about y followed by a translation - everything the book's instances can express.
 *
 * The CPU renderer transforms rays and hits at traversal time, the way [rayTraceTypescript.objects.RotateY]
 * and [rayTraceTypescript.objects.Translate] describe it. The GPU has no instance nodes, so the
 * flattener folds the transforms into the primitives instead. Both give the same geometry:
 * these are rigid transforms, and composing them stays inside the same family.
 */
internal class Transform(
    val cos: Float,
    val sin: Float,
    val offset: Vector
) {
    fun point(p: Vector): Point {
        val rotated = vector(p)
        return Point(rotated.x + offset.x, rotated.y + offset.y, rotated.z + offset.z)
    }

    /** Directions only rotate; the translation does not touch them. */
    fun vector(v: Vector): Vector = Vector(
        cos * v.x + sin * v.z,
        v.y,
        -sin * v.x + cos * v.z
    )

    /** The transform that maps p to this(inner(p)). */
    fun after(inner: Transform): Transform {
        val movedInnerOffset = vector(inner.offset)
        return Transform(
            cos = cos * inner.cos - sin * inner.sin,
            sin = sin * inner.cos + cos * inner.sin,
            offset = Vector(
                movedInnerOffset.x + offset.x,
                movedInnerOffset.y + offset.y,
                movedInnerOffset.z + offset.z
            )
        )
    }

    companion object {
        val IDENTITY = Transform(1.0f, 0.0f, Vector(0.0f, 0.0f, 0.0f))

        fun translation(offset: Vector) = Transform(1.0f, 0.0f, offset)

        fun rotation(cos: Float, sin: Float) = Transform(cos, sin, Vector(0.0f, 0.0f, 0.0f))
    }
}
