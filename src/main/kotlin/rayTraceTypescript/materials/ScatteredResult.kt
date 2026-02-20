package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector

class ScatteredResult {
    var albedoR: Float = 0.0f
    var albedoG: Float = 0.0f
    var albedoB: Float = 0.0f
    val scattered: Ray = Ray(Point(0.0f, 0.0f, 0.0f), Vector(0.0f, 0.0f, 0.0f), 0.0f)

    constructor()

    constructor(albedo: Color, scattered: Ray) : this() {
        set(albedo, scattered)
    }

    fun set(albedo: Color, scattered: Ray): ScatteredResult {
        this.albedoR = albedo.r
        this.albedoG = albedo.g
        this.albedoB = albedo.b
        this.scattered.copyFrom(scattered)
        return this
    }

    fun set(
        albedoR: Float,
        albedoG: Float,
        albedoB: Float,
        originX: Float,
        originY: Float,
        originZ: Float,
        directionX: Float,
        directionY: Float,
        directionZ: Float,
        time: Float,
    ): ScatteredResult {
        this.albedoR = albedoR
        this.albedoG = albedoG
        this.albedoB = albedoB
        scattered.set(originX, originY, originZ, directionX, directionY, directionZ, time)
        return this
    }
}
