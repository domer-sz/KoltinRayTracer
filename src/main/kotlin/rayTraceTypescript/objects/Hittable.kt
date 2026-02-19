package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Ray

interface Hittable {
    fun hit(ray: Ray, rayT: Interval): Hit?
    fun aabbBoundingBox(): Aabb
}
