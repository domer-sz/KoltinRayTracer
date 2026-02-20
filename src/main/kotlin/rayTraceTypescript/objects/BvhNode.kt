package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Ray
import kotlin.random.Random

class BvhNode : Hittable {

    private val left: Hittable
    private val right: Hittable
    private val bbox: Aabb
    private val rightHitCandidate = Hit()

    constructor(list: HittableList) : this(list.objects, 0, list.objects.size)


    constructor(objects: MutableList<Hittable>, start: Int, end: Int) {
        val axis = Random.nextInt(0, 3) // 0..2

        val comparator: Comparator<Hittable> = when (axis) {
            0 -> compareBy { it.aabbBoundingBox().x.min } // equivalent to box_x_compare
            1 -> compareBy { it.aabbBoundingBox().y.min } // equivalent to box_y_compare
            else -> compareBy { it.aabbBoundingBox().z.min } // equivalent to box_z_compare
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

        bbox = Aabb(left.aabbBoundingBox(), right.aabbBoundingBox())
    }

    override fun hit(ray: Ray, tMin: Float, tMax: Float, outHit: Hit): Boolean {
        if (!bbox.hit(ray, tMin, tMax)) return false

        val hitLeft = left.hit(ray, tMin, tMax, outHit)
        val rightMax = if (hitLeft) outHit.t else tMax
        val hitRight = right.hit(ray, tMin, rightMax, rightHitCandidate)
        if (hitRight) {
            outHit.copyFrom(rightHitCandidate)
        }

        return hitLeft || hitRight
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
