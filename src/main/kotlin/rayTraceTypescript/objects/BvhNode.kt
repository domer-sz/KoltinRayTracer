package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Ray
import kotlin.random.Random

class BvhNode : Hittable {

    private val left: Hittable
    private val right: Hittable
    private val bbox: Aabb
    private val bboxMinX: Float
    private val bboxMaxX: Float
    private val bboxMinY: Float
    private val bboxMaxY: Float
    private val bboxMinZ: Float
    private val bboxMaxZ: Float
    private val candidateHit = Hit()

    private var packedReady: Boolean = false
    private lateinit var packedMinX: FloatArray
    private lateinit var packedMaxX: FloatArray
    private lateinit var packedMinY: FloatArray
    private lateinit var packedMaxY: FloatArray
    private lateinit var packedMinZ: FloatArray
    private lateinit var packedMaxZ: FloatArray
    private lateinit var packedLeft: IntArray
    private lateinit var packedRight: IntArray
    private lateinit var packedLeafA: Array<Sphere?>
    private lateinit var packedLeafB: Array<Sphere?>
    private lateinit var traversalStack: IntArray

    constructor(list: HittableList) : this(list.objects, 0, list.objects.size)

    constructor(objects: MutableList<Hittable>, start: Int, end: Int) {
        val axis = Random.nextInt(0, 3)
        val comparator: Comparator<Hittable> = when (axis) {
            0 -> compareBy { it.aabbBoundingBox().minX }
            1 -> compareBy { it.aabbBoundingBox().minY }
            else -> compareBy { it.aabbBoundingBox().minZ }
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
            objects.subList(start, end).sortWith(comparator)
            val mid = start + objectSpan / 2
            left = BvhNode(objects, start, mid)
            right = BvhNode(objects, mid, end)
        }

        bbox = Aabb(left.aabbBoundingBox(), right.aabbBoundingBox())
        bboxMinX = bbox.minX
        bboxMaxX = bbox.maxX
        bboxMinY = bbox.minY
        bboxMaxY = bbox.maxY
        bboxMinZ = bbox.minZ
        bboxMaxZ = bbox.maxZ
    }

    override fun hit(ray: Ray, tMin: Float, tMax: Float, outHit: Hit): Boolean {
        if (packedReady) {
            return hitPacked(ray, tMin, tMax, outHit)
        }

        if (!hitBbox(ray, tMin, tMax, bboxMinX, bboxMaxX, bboxMinY, bboxMaxY, bboxMinZ, bboxMaxZ)) return false

        val hitLeft = left.hit(ray, tMin, tMax, outHit)
        val rightMax = if (hitLeft) outHit.t else tMax
        val hitRight = right.hit(ray, tMin, rightMax, candidateHit)
        if (hitRight) {
            outHit.copyFrom(candidateHit)
        }
        return hitLeft || hitRight
    }

    override fun prepareForRender() {
        if (packedReady) return

        val minXList = ArrayList<Float>()
        val maxXList = ArrayList<Float>()
        val minYList = ArrayList<Float>()
        val maxYList = ArrayList<Float>()
        val minZList = ArrayList<Float>()
        val maxZList = ArrayList<Float>()
        val leftList = ArrayList<Int>()
        val rightList = ArrayList<Int>()
        val leafAList = ArrayList<Sphere?>()
        val leafBList = ArrayList<Sphere?>()

        packNode(
            this,
            minXList,
            maxXList,
            minYList,
            maxYList,
            minZList,
            maxZList,
            leftList,
            rightList,
            leafAList,
            leafBList
        )

        val nodeCount = minXList.size
        packedMinX = FloatArray(nodeCount) { idx -> minXList[idx] }
        packedMaxX = FloatArray(nodeCount) { idx -> maxXList[idx] }
        packedMinY = FloatArray(nodeCount) { idx -> minYList[idx] }
        packedMaxY = FloatArray(nodeCount) { idx -> maxYList[idx] }
        packedMinZ = FloatArray(nodeCount) { idx -> minZList[idx] }
        packedMaxZ = FloatArray(nodeCount) { idx -> maxZList[idx] }
        packedLeft = IntArray(nodeCount) { idx -> leftList[idx] }
        packedRight = IntArray(nodeCount) { idx -> rightList[idx] }
        packedLeafA = Array(nodeCount) { idx -> leafAList[idx] }
        packedLeafB = Array(nodeCount) { idx -> leafBList[idx] }
        traversalStack = IntArray(nodeCount * 2 + 4)
        packedReady = true
    }

    override fun aabbBoundingBox(): Aabb = bbox

    private fun hitPacked(ray: Ray, tMin: Float, tMax: Float, outHit: Hit): Boolean {
        var hitAnything = false
        var closestSoFar = tMax
        var stackSize = 0
        traversalStack[stackSize++] = 0

        while (stackSize > 0) {
            val nodeIndex = traversalStack[--stackSize]
            if (!hitBbox(
                    ray,
                    tMin,
                    closestSoFar,
                    packedMinX[nodeIndex],
                    packedMaxX[nodeIndex],
                    packedMinY[nodeIndex],
                    packedMaxY[nodeIndex],
                    packedMinZ[nodeIndex],
                    packedMaxZ[nodeIndex]
                )
            ) {
                continue
            }

            val leftIndex = packedLeft[nodeIndex]
            if (leftIndex >= 0) {
                traversalStack[stackSize++] = leftIndex
                traversalStack[stackSize++] = packedRight[nodeIndex]
                continue
            }

            val sphereA = packedLeafA[nodeIndex]
            if (sphereA != null && sphereA.hit(ray, tMin, closestSoFar, outHit)) {
                hitAnything = true
                closestSoFar = outHit.t
            }

            val sphereB = packedLeafB[nodeIndex]
            if (sphereB != null && sphereB.hit(ray, tMin, closestSoFar, outHit)) {
                hitAnything = true
                closestSoFar = outHit.t
            }
        }

        return hitAnything
    }

    private fun packNode(
        node: BvhNode,
        minXList: MutableList<Float>,
        maxXList: MutableList<Float>,
        minYList: MutableList<Float>,
        maxYList: MutableList<Float>,
        minZList: MutableList<Float>,
        maxZList: MutableList<Float>,
        leftList: MutableList<Int>,
        rightList: MutableList<Int>,
        leafAList: MutableList<Sphere?>,
        leafBList: MutableList<Sphere?>,
    ): Int {
        val index = minXList.size
        minXList.add(node.bboxMinX)
        maxXList.add(node.bboxMaxX)
        minYList.add(node.bboxMinY)
        maxYList.add(node.bboxMaxY)
        minZList.add(node.bboxMinZ)
        maxZList.add(node.bboxMaxZ)
        leftList.add(-1)
        rightList.add(-1)
        leafAList.add(null)
        leafBList.add(null)

        val leftObj = node.left
        val rightObj = node.right
        if (leftObj is BvhNode && rightObj is BvhNode) {
            val leftIndex = packNode(
                leftObj,
                minXList,
                maxXList,
                minYList,
                maxYList,
                minZList,
                maxZList,
                leftList,
                rightList,
                leafAList,
                leafBList
            )
            val rightIndex = packNode(
                rightObj,
                minXList,
                maxXList,
                minYList,
                maxYList,
                minZList,
                maxZList,
                leftList,
                rightList,
                leafAList,
                leafBList
            )
            leftList[index] = leftIndex
            rightList[index] = rightIndex
        } else {
            val sphereA = leftObj as Sphere
            val sphereB = if (rightObj === leftObj) null else rightObj as Sphere
            leafAList[index] = sphereA
            leafBList[index] = sphereB
        }

        return index
    }

    private fun hitBbox(
        ray: Ray,
        tMinInput: Float,
        tMaxInput: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        minZ: Float,
        maxZ: Float,
    ): Boolean {
        var tMin = tMinInput
        var tMax = tMaxInput

        val origin = ray.origin
        val direction = ray.direction

        var invD = 1.0f / direction.x
        var t0 = (minX - origin.x) * invD
        var t1 = (maxX - origin.x) * invD
        if (t0 > t1) {
            val tmp = t0
            t0 = t1
            t1 = tmp
        }
        if (t0 > tMin) tMin = t0
        if (t1 < tMax) tMax = t1
        if (tMax <= tMin) return false

        invD = 1.0f / direction.y
        t0 = (minY - origin.y) * invD
        t1 = (maxY - origin.y) * invD
        if (t0 > t1) {
            val tmp = t0
            t0 = t1
            t1 = tmp
        }
        if (t0 > tMin) tMin = t0
        if (t1 < tMax) tMax = t1
        if (tMax <= tMin) return false

        invD = 1.0f / direction.z
        t0 = (minZ - origin.z) * invD
        t1 = (maxZ - origin.z) * invD
        if (t0 > t1) {
            val tmp = t0
            t0 = t1
            t1 = tmp
        }
        if (t0 > tMin) tMin = t0
        if (t1 < tMax) tMax = t1
        if (tMax <= tMin) return false

        return true
    }
}
