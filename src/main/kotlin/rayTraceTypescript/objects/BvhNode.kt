package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Ray
import kotlin.random.Random

class BvhNode : Hittable {

    private val left: Hittable
    private val right: Hittable
    private lateinit var bbox: Aabb

    constructor(list: HittableList) : this(list.objects, 0, list.objects.size)


    constructor(objects: MutableList<Hittable>, start: Int, end: Int) {
        // Build the bounding box of the span of source objects.
        var bbox = Aabb()
        for (i in start until end) {
            bbox = Aabb(bbox, objects[i].aabbBoundingBox())
        }

        val axis = bbox.longestAxis()


        val comparator: Comparator<Hittable> = when (axis) {
            0 -> compareBy { it.aabbBoundingBox().x.min }
            1 -> compareBy { it.aabbBoundingBox().y.min }
            else -> compareBy { it.aabbBoundingBox().z.min }
        }

        val objectSpan = end - start
        require(objectSpan > 0) { "BVH node must contain at least one object" }

        if (objectSpan == 1) {
            left = objects[start]
            right = objects[start]
        } else if (objectSpan == 2) {
            left = objects[start]
            right = objects[start + 1]
        } else {
            // std::sort(objects.begin()+start, objects.begin()+end, comparator)
            objects.subList(start, end).sortWith(comparator)

            val mid = start + objectSpan / 2
            left = BvhNode(objects, start, mid)
            right = BvhNode(objects, mid, end)
        }

        this.bbox = Aabb(left.aabbBoundingBox(), right.aabbBoundingBox())
    }

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        // not hitted
        if (!bbox.hit(ray, rayT)) return null

        val leftHit = left.hit(ray, rayT)

        val rightInterval = Interval(
            rayT.min,
            leftHit?.t ?: rayT.max
        )
        val rightHit = right.hit(ray, rightInterval)

        // return hit_left || hit_right;
        return rightHit ?: leftHit
    }

    private fun boxCompare(a: Hittable, b: Hittable, axisIndex: Int): Boolean {
        val aAxis = a.aabbBoundingBox().axisInterval(axisIndex)
        val bAxis = b.aabbBoundingBox().axisInterval(axisIndex)
        return aAxis.min < bAxis.min
    }

    private fun boxXCompare(a: Hittable, b: Hittable): Boolean = boxCompare(a, b, 0)
    private fun boxYCompare(a: Hittable, b: Hittable): Boolean = boxCompare(a, b, 1)
    private fun boxZCompare(a: Hittable, b: Hittable): Boolean = boxCompare(a, b, 2)


    override fun aabbBoundingBox(): Aabb = bbox
}
