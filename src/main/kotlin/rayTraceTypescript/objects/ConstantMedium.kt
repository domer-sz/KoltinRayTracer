package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Color
import rayTraceTypescript.Interval
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Isotropic
import rayTraceTypescript.materials.Material
import rayTraceTypescript.textures.Texture
import rayTraceTypescript.utils.RandomSource
import kotlin.math.ln

/**
 * Fog of uniform density filling a boundary volume. A ray crossing the volume is stopped at a
 * random depth drawn from the density; if that depth runs past the far wall the ray simply
 * passes through. The boundary must be convex, which is what the books assume.
 */
class ConstantMedium(
    val boundary: Hittable,
    density: Float,
    val phaseFunction: Material
) : Hittable {

    constructor(boundary: Hittable, density: Float, texture: Texture) :
        this(boundary, density, Isotropic(texture))

    constructor(boundary: Hittable, density: Float, albedo: Color) :
        this(boundary, density, Isotropic(albedo))

    val negativeInverseDensity: Float = -1.0f / density

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val entry = boundary.hit(ray, Interval.UNIVERSE) ?: return null
        val exit = boundary.hit(ray, Interval(entry.t + 0.0001f, Float.POSITIVE_INFINITY)) ?: return null

        var start = if (entry.t < rayT.min) rayT.min else entry.t
        val end = if (exit.t > rayT.max) rayT.max else exit.t
        if (start >= end) return null
        if (start < 0.0f) start = 0.0f

        val rayLength = ray.direction.length()
        val distanceInside = (end - start) * rayLength
        val hitDistance = negativeInverseDensity * ln(RandomSource.nextFloat().toDouble()).toFloat()
        if (hitDistance > distanceInside) return null

        val t = start + hitDistance / rayLength
        // The normal and the side are arbitrary here: a scatter inside fog has no surface.
        return Hit(ray.at(t), Vector(1.0f, 0.0f, 0.0f), true, phaseFunction, t, 0.0f, 0.0f)
    }

    override fun aabbBoundingBox(): Aabb = boundary.aabbBoundingBox()
}
