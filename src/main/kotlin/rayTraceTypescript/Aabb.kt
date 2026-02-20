package rayTraceTypescript

class Aabb(
    val minX: Float = Float.POSITIVE_INFINITY,
    val maxX: Float = Float.NEGATIVE_INFINITY,
    val minY: Float = Float.POSITIVE_INFINITY,
    val maxY: Float = Float.NEGATIVE_INFINITY,
    val minZ: Float = Float.POSITIVE_INFINITY,
    val maxZ: Float = Float.NEGATIVE_INFINITY,
) {

    constructor(firstBox: Aabb, secondBox: Aabb) : this(
        minX = kotlin.math.min(firstBox.minX, secondBox.minX),
        maxX = kotlin.math.max(firstBox.maxX, secondBox.maxX),
        minY = kotlin.math.min(firstBox.minY, secondBox.minY),
        maxY = kotlin.math.max(firstBox.maxY, secondBox.maxY),
        minZ = kotlin.math.min(firstBox.minZ, secondBox.minZ),
        maxZ = kotlin.math.max(firstBox.maxZ, secondBox.maxZ),
    )

    companion object {
        fun fromPoints(a: Point, b: Point): Aabb {
            val minX = if (a.x <= b.x) a.x else b.x
            val maxX = if (a.x <= b.x) b.x else a.x
            val minY = if (a.y <= b.y) a.y else b.y
            val maxY = if (a.y <= b.y) b.y else a.y
            val minZ = if (a.z <= b.z) a.z else b.z
            val maxZ = if (a.z <= b.z) b.z else a.z
            return Aabb(minX, maxX, minY, maxY, minZ, maxZ)
        }
    }

    fun minAt(axis: Int): Float = when (axis) {
        1 -> minY
        2 -> minZ
        else -> minX
    }

    fun maxAt(axis: Int): Float = when (axis) {
        1 -> maxY
        2 -> maxZ
        else -> maxX
    }

    fun hit(r: Ray, tMinInput: Float, tMaxInput: Float): Boolean {
        val rayOrig = r.origin
        val rayDir = r.direction

        var tMin = tMinInput
        var tMax = tMaxInput

        val invDx = 1.0f / rayDir.x
        var t0 = (minX - rayOrig.x) * invDx
        var t1 = (maxX - rayOrig.x) * invDx
        if (t0 > t1) {
            val tmp = t0
            t0 = t1
            t1 = tmp
        }
        if (t0 > tMin) tMin = t0
        if (t1 < tMax) tMax = t1
        if (tMax <= tMin) return false

        val invDy = 1.0f / rayDir.y
        t0 = (minY - rayOrig.y) * invDy
        t1 = (maxY - rayOrig.y) * invDy
        if (t0 > t1) {
            val tmp = t0
            t0 = t1
            t1 = tmp
        }
        if (t0 > tMin) tMin = t0
        if (t1 < tMax) tMax = t1
        if (tMax <= tMin) return false

        val invDz = 1.0f / rayDir.z
        t0 = (minZ - rayOrig.z) * invDz
        t1 = (maxZ - rayOrig.z) * invDz
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
