package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material
import kotlin.math.sqrt

class Sphere constructor(val center: Ray, val radius: Float, val material: Material) : Hittable {

    private lateinit var bbox: Aabb

    //static sphere
    constructor(center: Point, radius: Float, material: Material) : this(Ray(center, Vector(0.0, 0.0, 0.0)), radius, material) {
        val rvec = Vector(radius, radius, radius)
        this.bbox = Aabb.fromPoints((center - rvec).toPoint(), (center + rvec).toPoint())
    }
    constructor(center: Point, radius: Double, material: Material) : this(Ray(center, Vector(0.0, 0.0, 0.0)), radius.toFloat(), material) {
        val rvec = Vector(radius, radius, radius)
        this.bbox = Aabb.fromPoints((center - rvec).toPoint(), (center + rvec).toPoint())
    }

    //dynamic sphere
    constructor(center1: Point, center2: Point, radius: Float, material: Material) : this(Ray(center1, center2 - center1), radius, material) {
        val rvec = Vector(radius, radius, radius)
        val box1 = Aabb.fromPoints((center.at(0F) - rvec).toPoint(), (center.at(0F) + rvec).toPoint())
        val box2 = Aabb.fromPoints((center.at(1F) - rvec).toPoint(), (center.at(1F) + rvec).toPoint())
        this.bbox = Aabb(box1, box2)
    }
    constructor(center1: Point, center2: Point, radius: Double, material: Material) : this(Ray(center1, center2 - center1), radius.toFloat(), material)

    override fun hit(ray: Ray, tMin: Float, tMax: Float, outHit: Hit): Boolean {
        val centerX = center.origin.x + ray.time * center.direction.x
        val centerY = center.origin.y + ray.time * center.direction.y
        val centerZ = center.origin.z + ray.time * center.direction.z

        val ocX = ray.origin.x - centerX
        val ocY = ray.origin.y - centerY
        val ocZ = ray.origin.z - centerZ

        val dirX = ray.direction.x
        val dirY = ray.direction.y
        val dirZ = ray.direction.z

        val a = dirX * dirX + dirY * dirY + dirZ * dirZ
        val h = -(dirX * ocX + dirY * ocY + dirZ * ocZ)
        val c = ocX * ocX + ocY * ocY + ocZ * ocZ - radius * radius
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
        val invRadius = 1.0f / radius

        outHit.set(
            ray,
            pointX,
            pointY,
            pointZ,
            (pointX - centerX) * invRadius,
            (pointY - centerY) * invRadius,
            (pointZ - centerZ) * invRadius,
            material,
            root
        )
        return true
    }

    override fun aabbBoundingBox(): Aabb = this.bbox
}
