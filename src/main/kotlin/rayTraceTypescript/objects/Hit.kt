package rayTraceTypescript.objects

import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector

class Hit {
    val point: Point = Point(0.0f, 0.0f, 0.0f)
    val normal: Vector = Vector(0.0f, 0.0f, 0.0f)
    var t: Float = 0.0f
    var frontFace: Boolean = true
    var materialType: Int = MATERIAL_LAMBERTIAN
    var albedoR: Float = 0.0f
    var albedoG: Float = 0.0f
    var albedoB: Float = 0.0f
    var fuzz: Float = 0.0f
    var refractiveIndex: Float = 1.0f

    constructor()

    constructor(ray: Ray, point: Point, outwardNormal: Vector, t: Float) : this() {
        set(ray, point.x, point.y, point.z, outwardNormal.x, outwardNormal.y, outwardNormal.z, t)
    }

    fun set(
        ray: Ray,
        pointX: Float,
        pointY: Float,
        pointZ: Float,
        outwardNormalX: Float,
        outwardNormalY: Float,
        outwardNormalZ: Float,
        t: Float,
    ): Hit {
        this.point.set(pointX, pointY, pointZ)
        this.t = t
        setFrontFace(ray, outwardNormalX, outwardNormalY, outwardNormalZ)
        return this
    }

    fun setMaterialLambertian(albedoR: Float, albedoG: Float, albedoB: Float): Hit {
        materialType = MATERIAL_LAMBERTIAN
        this.albedoR = albedoR
        this.albedoG = albedoG
        this.albedoB = albedoB
        this.fuzz = 0.0f
        this.refractiveIndex = 1.0f
        return this
    }

    fun setMaterialMetal(albedoR: Float, albedoG: Float, albedoB: Float, fuzz: Float): Hit {
        materialType = MATERIAL_METAL
        this.albedoR = albedoR
        this.albedoG = albedoG
        this.albedoB = albedoB
        this.fuzz = fuzz
        this.refractiveIndex = 1.0f
        return this
    }

    fun setMaterialDielectric(refractiveIndex: Float): Hit {
        materialType = MATERIAL_DIELECTRIC
        this.albedoR = 1.0f
        this.albedoG = 1.0f
        this.albedoB = 1.0f
        this.fuzz = 0.0f
        this.refractiveIndex = refractiveIndex
        return this
    }

    fun setMaterialData(
        materialType: Int,
        albedoR: Float,
        albedoG: Float,
        albedoB: Float,
        fuzz: Float,
        refractiveIndex: Float,
    ): Hit {
        this.materialType = materialType
        this.albedoR = albedoR
        this.albedoG = albedoG
        this.albedoB = albedoB
        this.fuzz = fuzz
        this.refractiveIndex = refractiveIndex
        return this
    }

    fun copyFrom(other: Hit): Hit {
        point.set(other.point.x, other.point.y, other.point.z)
        normal.set(other.normal.x, other.normal.y, other.normal.z)
        t = other.t
        frontFace = other.frontFace
        materialType = other.materialType
        albedoR = other.albedoR
        albedoG = other.albedoG
        albedoB = other.albedoB
        fuzz = other.fuzz
        refractiveIndex = other.refractiveIndex
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

    companion object {
        const val MATERIAL_LAMBERTIAN: Int = 0
        const val MATERIAL_METAL: Int = 1
        const val MATERIAL_DIELECTRIC: Int = 2
    }
}
