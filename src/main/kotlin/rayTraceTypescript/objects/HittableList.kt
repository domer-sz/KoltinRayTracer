package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.utils.RandomSource

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

    /** Sampling the list means picking one member at random, so the densities average. */
    override fun pdfValue(origin: Point, direction: Vector): Float {
        if (objects.isEmpty()) return 0.0f
        val weight = 1.0f / objects.size
        return objects.sumOf { (it.pdfValue(origin, direction) * weight).toDouble() }.toFloat()
    }

    override fun random(origin: Point): Vector {
        if (objects.isEmpty()) return Vector(1.0f, 0.0f, 0.0f)
        return objects[RandomSource.nextInt(0, objects.size - 1)].random(origin)
    }
}
