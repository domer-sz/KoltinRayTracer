package rayTraceTypescript

import rayTraceTypescript.utils.randomFloat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

open class Vector(val x: Float, val y: Float, val z: Float) {
    constructor(x: Double, y: Double, z: Double) : this(x.toFloat(), y.toFloat(), z.toFloat())
    fun negate(): Vector = Vector(-x, -y, -z)
    fun plus(other: Vector): Vector = Vector(x + other.x, y + other.y, z + other.z)
    fun minus(other: Vector): Vector = Vector(x - other.x, y - other.y, z - other.z)
    fun scale(s: Float): Vector = Vector(x * s, y * s, z * s)
    fun divide(s: Float): Vector = Vector(x / s, y / s, z / s)
    fun multiply(other: Vector): Vector = Vector(x * other.x, y * other.y, z * other.z)

    fun length(): Float = sqrt(lengthSquared())
    fun lengthSquared(): Float = x * x + y * y + z * z
    fun unit(): Vector = divide(length())

    fun nearZero(): Boolean {
        val s = 1e-8f
        return abs(x) < s && abs(y) < s && abs(z) < s
    }

    override fun toString(): String = "$" + "x $" + "y $" + "z"

    companion object {
        @JvmStatic
        fun dotProduct(u: Vector, v: Vector): Float = u.x * v.x + u.y * v.y + u.z * v.z
        @JvmStatic
        fun cross(u: Vector, v: Vector): Vector =
            Vector(u.y * v.z - u.z * v.y, u.z * v.x - u.x * v.z, u.x * v.y - u.y * v.x)

        @JvmStatic
        fun unit(v: Vector): Vector = v.unit()

        @JvmStatic
        fun random(min: Float = 0.0f, max: Float = 1.0f): Vector =
            Vector(randomFloat(min, max), randomFloat(min, max), randomFloat(min, max))

        @JvmStatic
        fun randomMinMax(min: Float, max: Float): Vector = random(min, max)

        @JvmStatic
        fun randomVectorInUnitDisc(): Vector {
            while (true) {
                val p = Vector(randomFloat(-1.0f, 1.0f), randomFloat(-1.0f, 1.0f), 0.0f)
                if (p.lengthSquared() < 1.0f) return p
            }
        }

        @JvmStatic
        fun randomInUnitSphere(): Vector {
            while (true) {
                val p = random(-1.0f, 1.0f)
                if (p.lengthSquared() < 1.0f) return p
            }
        }

        @JvmStatic
        fun randomOnHemisphere(normal: Vector): Vector {
            val onUnitSphere = randomInUnitSphere().unit()
            return if (dotProduct(onUnitSphere, normal) > 0.0f) onUnitSphere else onUnitSphere.negate()
        }

        @JvmStatic
        fun reflect(v: Vector, n: Vector): Vector =
            v.minus(n.scale(2.0f * dotProduct(v, n)))

        @JvmStatic
        fun refract(uv: Vector, n: Vector, etai_over_etat: Float): Vector {
            val cosTheta = min(dotProduct(uv.negate(), n), 1.0f)
            val rOutPerp = (n.scale(cosTheta).plus(uv)).scale(etai_over_etat)
            val rOutParallel = n.scale(sqrt(abs(1.0f - rOutPerp.lengthSquared()))).negate()
            return rOutPerp.plus(rOutParallel)
        }
    }
}

operator fun Vector.plus(other: Vector): Vector = this.plus(other)
operator fun Vector.minus(other: Vector): Vector = this.minus(other)
operator fun Vector.times(s: Float): Vector = this.scale(s)
operator fun Vector.div(s: Float): Vector = this.divide(s)
operator fun Vector.unaryMinus(): Vector = this.negate()
