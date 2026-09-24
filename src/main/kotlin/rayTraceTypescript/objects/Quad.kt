package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material
import kotlin.math.abs

/**
 * A parallelogram spanned by [u] and [v] from corner [q]. A ray is first intersected with the
 * quad's infinite plane, and the hit is then expressed in the (u, v) basis: coordinates inside
 * the unit square are on the quad, and they double as texture coordinates.
 */
class Quad(val q: Point, val u: Vector, val v: Vector, val material: Material) : Hittable {

    private val cross: Vector = Vector.cross(u, v)

    val normal: Vector = Vector.unit(cross)
    val d: Float = Vector.dotProduct(normal, q)

    /** Turns a point on the plane into (alpha, beta) coordinates of the u/v basis. */
    val w: Vector = cross / Vector.dotProduct(cross, cross)

    private val bbox: Aabb = Aabb(
        Aabb.fromPoints(q, (q + u + v).toPoint()),
        Aabb.fromPoints((q + u).toPoint(), (q + v).toPoint())
    ).padded()

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val denominator = Vector.dotProduct(normal, ray.direction)
        if (abs(denominator) < PARALLEL_EPSILON) return null    // ray runs along the plane

        val t = (d - Vector.dotProduct(normal, ray.origin)) / denominator
        if (!rayT.contains(t)) return null

        val intersection = ray.at(t)
        val planarHit = intersection - q
        val alpha = Vector.dotProduct(w, Vector.cross(planarHit, v))
        val beta = Vector.dotProduct(w, Vector.cross(u, planarHit))
        if (!UNIT.contains(alpha) || !UNIT.contains(beta)) return null

        return Hit.facing(ray, intersection, normal, material, t, alpha, beta)
    }

    override fun aabbBoundingBox(): Aabb = bbox

    companion object {
        const val PARALLEL_EPSILON = 1e-8f
        private val UNIT = Interval(0.0f, 1.0f)
    }
}
