package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Point
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.Ray
import kotlin.math.sqrt

class Sphere private constructor(
    private val centerStartX: Float,
    private val centerStartY: Float,
    private val centerStartZ: Float,
    private val centerDeltaX: Float,
    private val centerDeltaY: Float,
    private val centerDeltaZ: Float,
    val radius: Float,
    val material: Material,
    private val bbox: Aabb,
) : Hittable {

    private val radiusSquared: Float = radius * radius
    private val invRadius: Float = 1.0f / radius
    private val materialType: Int
    private val albedoR: Float
    private val albedoG: Float
    private val albedoB: Float
    private val fuzz: Float
    private val refractiveIndex: Float

    init {
        when (material) {
            is Lambertian -> {
                materialType = Hit.MATERIAL_LAMBERTIAN
                albedoR = material.albedo.r
                albedoG = material.albedo.g
                albedoB = material.albedo.b
                fuzz = 0.0f
                refractiveIndex = 1.0f
            }
            is Metal -> {
                materialType = Hit.MATERIAL_METAL
                albedoR = material.albedo.r
                albedoG = material.albedo.g
                albedoB = material.albedo.b
                fuzz = material.fuzz
                refractiveIndex = 1.0f
            }
            is Dielectric -> {
                materialType = Hit.MATERIAL_DIELECTRIC
                albedoR = 1.0f
                albedoG = 1.0f
                albedoB = 1.0f
                fuzz = 0.0f
                refractiveIndex = material.ri
            }
            else -> {
                materialType = Hit.MATERIAL_LAMBERTIAN
                albedoR = 0.5f
                albedoG = 0.5f
                albedoB = 0.5f
                fuzz = 0.0f
                refractiveIndex = 1.0f
            }
        }
    }

    constructor(center: Point, radius: Float, material: Material) : this(
        centerStartX = center.x,
        centerStartY = center.y,
        centerStartZ = center.z,
        centerDeltaX = 0.0f,
        centerDeltaY = 0.0f,
        centerDeltaZ = 0.0f,
        radius = radius,
        material = material,
        bbox = staticSphereBox(center.x, center.y, center.z, radius),
    )

    constructor(center: Point, radius: Double, material: Material) : this(center, radius.toFloat(), material)

    constructor(center1: Point, center2: Point, radius: Float, material: Material) : this(
        centerStartX = center1.x,
        centerStartY = center1.y,
        centerStartZ = center1.z,
        centerDeltaX = center2.x - center1.x,
        centerDeltaY = center2.y - center1.y,
        centerDeltaZ = center2.z - center1.z,
        radius = radius,
        material = material,
        bbox = movingSphereBox(center1, center2, radius),
    )

    constructor(center1: Point, center2: Point, radius: Double, material: Material) : this(
        center1,
        center2,
        radius.toFloat(),
        material
    )

    override fun hit(ray: Ray, tMin: Float, tMax: Float, outHit: Hit): Boolean {
        val centerX = centerStartX + ray.time * centerDeltaX
        val centerY = centerStartY + ray.time * centerDeltaY
        val centerZ = centerStartZ + ray.time * centerDeltaZ

        val ocX = ray.origin.x - centerX
        val ocY = ray.origin.y - centerY
        val ocZ = ray.origin.z - centerZ

        val dirX = ray.direction.x
        val dirY = ray.direction.y
        val dirZ = ray.direction.z

        val a = dirX * dirX + dirY * dirY + dirZ * dirZ
        val h = -(dirX * ocX + dirY * ocY + dirZ * ocZ)
        val c = ocX * ocX + ocY * ocY + ocZ * ocZ - radiusSquared
        val discriminant = h * h - a * c
        if (discriminant < 0.0f) return false

        val sqrtd = sqrt(discriminant)
        var root = (h - sqrtd) / a
        if (root <= tMin || root >= tMax) {
            root = (h + sqrtd) / a
            if (root <= tMin || root >= tMax) return false
        }

        val pointX = ray.origin.x + root * dirX
        val pointY = ray.origin.y + root * dirY
        val pointZ = ray.origin.z + root * dirZ

        outHit.set(
            ray,
            pointX,
            pointY,
            pointZ,
            (pointX - centerX) * invRadius,
            (pointY - centerY) * invRadius,
            (pointZ - centerZ) * invRadius,
            root
        )
        outHit.setMaterialData(materialType, albedoR, albedoG, albedoB, fuzz, refractiveIndex)
        return true
    }

    override fun aabbBoundingBox(): Aabb = bbox

    companion object {
        private fun staticSphereBox(centerX: Float, centerY: Float, centerZ: Float, radius: Float): Aabb =
            Aabb(
                minX = centerX - radius,
                maxX = centerX + radius,
                minY = centerY - radius,
                maxY = centerY + radius,
                minZ = centerZ - radius,
                maxZ = centerZ + radius,
            )

        private fun movingSphereBox(center1: Point, center2: Point, radius: Float): Aabb {
            val box1 = staticSphereBox(center1.x, center1.y, center1.z, radius)
            val box2 = staticSphereBox(center2.x, center2.y, center2.z, radius)
            return Aabb(box1, box2)
        }
    }
}
