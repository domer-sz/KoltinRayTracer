package rayTraceTypescript.objects

import rayTraceTypescript.Interval
import rayTraceTypescript.Ray

class HittableList(val objects: MutableList<Hittable>) : Hittable {
    override fun hit(ray: Ray, rayT: Interval): Hit? {
        var hit: Hit? = null
        var closestSoFar = rayT.max
        for (obj in objects) {
            val tmpHit = obj.hit(ray, Interval(rayT.min, closestSoFar))
            if (tmpHit != null) {
                closestSoFar = tmpHit.t
                hit = tmpHit
            }
        }
        return hit
    }
}