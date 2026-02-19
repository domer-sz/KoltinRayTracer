package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Ray

//class HittableList(val objects: MutableList<Hittable>) : Hittable { //before bbox
//
//    override fun hit(ray: Ray, rayT: Interval): Hit? {
//        var hit: Hit? = null
//        var closestSoFar = rayT.max
//        for (obj in objects) {
//            val tmpHit = obj.hit(ray, Interval(rayT.min, closestSoFar))
//            if (tmpHit != null) {
//                closestSoFar = tmpHit.t
//                hit = tmpHit
//            }
//        }
//        return hit
//    }
//}

class HittableList(objects: MutableList<Hittable> = mutableListOf()) : Hittable {

    val objects: MutableList<Hittable> = mutableListOf()
    private var bbox: Aabb = Aabb() // or whatever “empty/invalid” box you use

    init {
        // Important: use add() so bbox is computed exactly like in C++
        for (obj in objects) add(obj)
    }

    fun add(obj: Hittable) {
        objects.add(obj)
        bbox = if (objects.size == 1) {
            // First object: bbox becomes that object's box
            obj.aabbBoundingBox()
        } else {
            // Subsequent objects: surrounding box of old + new
            Aabb(bbox, obj.aabbBoundingBox())
        }
    }

    fun clear() {
        objects.clear()
        bbox = Aabb()
    }

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        var hit: Hit? = null
        var closestSoFar = rayT.max

        for (obj in objects) {
            val tmp = obj.hit(ray, Interval(rayT.min, closestSoFar))
            if (tmp != null) {
                closestSoFar = tmp.t
                hit = tmp
            }
        }
        return hit
    }

    override fun aabbBoundingBox(): Aabb = bbox
}
