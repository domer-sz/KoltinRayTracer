package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.UV
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material
import kotlin.math.PI
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

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val currentCenter = center.at(ray.time)
        val oc =  ray.origin - currentCenter
        val a = ray.direction.lengthSquared()
        val h = Vector.dotProduct(ray.direction, -oc)
        val c = oc.lengthSquared() - radius * radius
        val discriminant = h * h - a * c
        if (discriminant < 0) return null
        val sqrtd = sqrt(discriminant)
        var root = (h - sqrtd) / a
        if (!rayT.surrounds(root)) {
            root = (h + sqrtd) / a
            if (!rayT.surrounds(root)) return null
        }
        val hitpoint = ray.at(root)
        val outwardNormal = (hitpoint - currentCenter) / radius
        val uv = getSphereUv(outwardNormal)
        return Hit(ray, hitpoint, outwardNormal, material, root, uv.u, uv.v)
    }

    override fun aabbBoundingBox(): Aabb = this.bbox

    companion object {
        fun getSphereUv(point: Vector): UV {
            // p: a given point on the sphere of radius one, centered at the origin.
            // u: returned value [0,1] of angle around the Y axis from X=-1.
            // v: returned value [0,1] of angle from Y=-1 to Y=+1.
            //     <1 0 0> yields <0.50 0.50>       <-1  0  0> yields <0.00 0.50>
            //     <0 1 0> yields <0.50 1.00>       < 0 -1  0> yields <0.50 0.00>
            //     <0 0 1> yields <0.25 0.50>       < 0  0 -1> yields <0.75 0.50>

            val theta = Math.acos(-point.y.toDouble())
            val phi = Math.atan2(-point.z.toDouble(), point.x.toDouble()) + PI

            val u = (phi / (2* PI)).toFloat()
            val v = (theta / PI).toFloat()

            return UV(u = u, v = v)
        }
    }
}
