package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Ray

interface Hittable {
    fun hit(ray: Ray, tMin: Float, tMax: Float, outHit: Hit): Boolean
    fun aabbBoundingBox(): Aabb
}
