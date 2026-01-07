package rayTraceTypescript.objects

import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material
import kotlin.math.sqrt

class Sphere  constructor(val center: Ray, val radius: Float, val material: Material) : Hittable {

    //static sphere
    constructor(center: Point, radius: Float, material: Material) : this(Ray(center, Vector(0.0, 0.0, 0.0)), radius, material)
    constructor(center: Point, radius: Double, material: Material) : this(Ray(center, Vector(0.0, 0.0, 0.0)), radius.toFloat(), material)

    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val oc = ray.origin - center.origin
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
        val outwardNormal = (hitpoint - center.origin) / radius
        return Hit(ray, hitpoint, outwardNormal, material, root)
    }
}
