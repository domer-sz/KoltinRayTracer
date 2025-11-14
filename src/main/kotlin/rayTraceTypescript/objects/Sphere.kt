package rayTraceTypescript.objects

import rayTraceTypescript.Interval
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material
import kotlin.math.sqrt

class Sphere(val center: Point, val radius: Double, val material: Material) : Hittable {
    override fun hit(ray: Ray, rayT: Interval): Hit? {
        val oc = ray.origin.minus(center)
        val a = ray.direction.lengthSquared()
        val h = Vector.dotProduct(ray.direction, oc.negate())
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
        val outwardNormal = hitpoint.minus(center).divide(radius)
        return Hit(ray, hitpoint, outwardNormal, material, root)
    }
}