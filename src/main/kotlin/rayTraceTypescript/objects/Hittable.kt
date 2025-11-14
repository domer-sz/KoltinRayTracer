package rayTraceTypescript.objects

import rayTraceTypescript.Interval
import rayTraceTypescript.Ray

interface Hittable {
    fun hit(ray: Ray, rayT: Interval): Hit?
}
