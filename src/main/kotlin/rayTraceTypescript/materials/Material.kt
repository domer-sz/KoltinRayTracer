package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.objects.Hit

interface Material {
    fun scatter(rayIn: Ray, hit: Hit): ScatteredResult?

    /** Light leaving the surface on its own; only emissive materials return anything. */
    fun emitted(u: Float, v: Float, point: Point): Color = Color(0.0f, 0.0f, 0.0f)
}