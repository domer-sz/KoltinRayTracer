package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.utils.degreesToRadians
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spins an object around the y axis. Like [Translate] this works on the ray rather than on the
 * geometry: rotate the ray into the object's frame, intersect, rotate the hit back out.
 */
class RotateY(val obj: Hittable, angleDegrees: Float) : Hittable {

    private val radians = degreesToRadians(angleDegrees)
    val sinTheta: Float = sin(radians.toDouble()).toFloat()
    val cosTheta: Float = cos(radians.toDouble()).toFloat()

    private val bbox: Aabb = rotatedBoundingBox(obj.aabbBoundingBox())

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val origin = intoObjectSpace(ray.origin).toPoint()
        val direction = intoObjectSpace(ray.direction)

        val hit = obj.hit(Ray(origin, direction, ray.time), rayT) ?: return null

        return hit.placedAt(intoWorldSpace(hit.point).toPoint(), intoWorldSpace(hit.normal))
    }

    override fun aabbBoundingBox(): Aabb = bbox

    private fun intoObjectSpace(v: Vector) = Vector(
        cosTheta * v.x - sinTheta * v.z,
        v.y,
        sinTheta * v.x + cosTheta * v.z
    )

    private fun intoWorldSpace(v: Vector) = Vector(
        cosTheta * v.x + sinTheta * v.z,
        v.y,
        -sinTheta * v.x + cosTheta * v.z
    )

    /** The box around the eight rotated corners of the original box. */
    private fun rotatedBoundingBox(box: Aabb): Aabb {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY

        for (i in 0..1) {
            for (j in 0..1) {
                for (k in 0..1) {
                    val corner = Vector(
                        i * box.x.max + (1 - i) * box.x.min,
                        j * box.y.max + (1 - j) * box.y.min,
                        k * box.z.max + (1 - k) * box.z.min
                    )
                    val rotated = intoWorldSpace(corner)
                    if (rotated.x < minX) minX = rotated.x
                    if (rotated.x > maxX) maxX = rotated.x
                    if (rotated.y < minY) minY = rotated.y
                    if (rotated.y > maxY) maxY = rotated.y
                    if (rotated.z < minZ) minZ = rotated.z
                    if (rotated.z > maxZ) maxZ = rotated.z
                }
            }
        }
        return Aabb.fromPoints(Point(minX, minY, minZ), Point(maxX, maxY, maxZ))
    }
}
