package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector

/**
 * Moves an object without touching its geometry: the ray is shifted the other way, the object
 * is intersected where it was modelled, and the hit is shifted back.
 */
class Translate(val obj: Hittable, val offset: Vector) : Hittable {

    private val bbox: Aabb = obj.aabbBoundingBox() + offset

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val movedRay = Ray((ray.origin - offset).toPoint(), ray.direction, ray.time)
        val hit = obj.hit(movedRay, rayT) ?: return null
        return hit.placedAt((hit.point + offset).toPoint())
    }

    override fun aabbBoundingBox(): Aabb = bbox
}
