package rayTraceTypescript.objects

import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material

class Hit {
    val point: Point = Point(0.0f, 0.0f, 0.0f)
    val normal: Vector = Vector(0.0f, 0.0f, 0.0f)
    lateinit var material: Material
    var t: Float = 0.0f
    var frontFace: Boolean = true

    constructor()

    constructor(ray: Ray, point: Point, outwardNormal: Vector, material: Material, t: Float) : this() {
        set(ray, point.x, point.y, point.z, outwardNormal.x, outwardNormal.y, outwardNormal.z, material, t)
    }

    fun set(
        ray: Ray,
        pointX: Float,
        pointY: Float,
        pointZ: Float,
        outwardNormalX: Float,
        outwardNormalY: Float,
        outwardNormalZ: Float,
        material: Material,
        t: Float,
    ): Hit {
        this.point.set(pointX, pointY, pointZ)
        this.material = material
        this.t = t
        setFrontFace(ray, outwardNormalX, outwardNormalY, outwardNormalZ)
        return this
    }

    fun copyFrom(other: Hit): Hit {
        point.set(other.point.x, other.point.y, other.point.z)
        normal.set(other.normal.x, other.normal.y, other.normal.z)
        material = other.material
        t = other.t
        frontFace = other.frontFace
        return this
    }

    private fun setFrontFace(ray: Ray, outwardNormalX: Float, outwardNormalY: Float, outwardNormalZ: Float) {
        frontFace = (ray.direction.x * outwardNormalX + ray.direction.y * outwardNormalY + ray.direction.z * outwardNormalZ) < 0.0f
        if (frontFace) {
            normal.set(outwardNormalX, outwardNormalY, outwardNormalZ)
        } else {
            normal.set(-outwardNormalX, -outwardNormalY, -outwardNormalZ)
        }
    }
}
