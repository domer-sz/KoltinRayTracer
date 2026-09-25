package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector

interface Hittable {
    fun hit(ray: Ray, rayT: Interval): Hit?

    /**
     * How likely [direction] is when sampling this object from [origin], measured against the
     * solid angle it covers. Only shapes worth aiming at - the lights - implement it.
     */
    fun pdfValue(origin: Point, direction: Vector): Float = 0.0f

    /** A direction from [origin] towards a random point on this object. */
    fun random(origin: Point): Vector = Vector(1.0f, 0.0f, 0.0f)
    fun aabbBoundingBox(): Aabb
}
