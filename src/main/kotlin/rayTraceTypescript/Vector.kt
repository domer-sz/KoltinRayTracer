package rayTraceTypescript

import rayTraceTypescript.utils.randomFloat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

open class Vector(var x: Float, var y: Float, var z: Float) {
    constructor(x: Double, y: Double, z: Double) : this(x.toFloat(), y.toFloat(), z.toFloat())

    open fun set(x: Float, y: Float, z: Float): Vector {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun set(other: Vector): Vector = set(other.x, other.y, other.z)

    fun negate(): Vector = Vector(-x, -y, -z)
    operator fun unaryMinus(): Vector = negate()
    operator fun plus(other: Vector): Vector = Vector(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector): Vector = Vector(x - other.x, y - other.y, z - other.z)
    fun scale(s: Float): Vector = Vector(x * s, y * s, z * s)
    operator fun times(s: Float): Vector = scale(s)
    fun divide(s: Float): Vector = Vector(x / s, y / s, z / s)
    operator fun div(s: Float): Vector = divide(s)
    fun multiply(other: Vector): Vector = Vector(x * other.x, y * other.y, z * other.z)
    operator fun times(other: Vector): Vector = multiply(other)

    fun length(): Float = sqrt(lengthSquared())
    fun lengthSquared(): Float = x * x + y * y + z * z
    fun unit(): Vector = divide(length())

    fun nearZero(): Boolean {
        val s = 1e-8f
        return abs(x) < s && abs(y) < s && abs(z) < s
    }

    override fun toString(): String = "$" + "x $" + "y $" + "z"

    operator fun get(i: Int): Float = when (i) {
        0 -> x
        1 -> y
        2 -> z
        else -> throw IndexOutOfBoundsException("Vector index $i")
    }

    fun toPoint(): Point {
        return Point(x, y, z)
    }

    companion object {
        @JvmStatic
        fun dotProduct(u: Vector, v: Vector): Float = u.x * v.x + u.y * v.y + u.z * v.z

        @JvmStatic
        fun cross(u: Vector, v: Vector): Vector =
            Vector(u.y * v.z - u.z * v.y, u.z * v.x - u.x * v.z, u.x * v.y - u.y * v.x)

        @JvmStatic
        fun unit(v: Vector): Vector = v.unit()

        @JvmStatic
        fun unit(v: Vector, out: Vector): Vector {
            val invLen = 1.0f / sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
            return out.set(v.x * invLen, v.y * invLen, v.z * invLen)
        }

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
        fun randomVectorInUnitDisc(out: Vector): Vector {
            while (true) {
                out.set(randomFloat(-1.0f, 1.0f), randomFloat(-1.0f, 1.0f), 0.0f)
                if (out.lengthSquared() < 1.0f) return out
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
        fun randomInUnitSphere(out: Vector): Vector {
            while (true) {
                out.set(
                    randomFloat(-1.0f, 1.0f),
                    randomFloat(-1.0f, 1.0f),
                    randomFloat(-1.0f, 1.0f),
                )
                if (out.lengthSquared() < 1.0f) return out
            }
        }

        @JvmStatic
        fun randomOnHemisphere(normal: Vector): Vector {
            val onUnitSphere = randomInUnitSphere().unit()
            return if (dotProduct(onUnitSphere, normal) > 0.0f) onUnitSphere else -onUnitSphere
        }

        @JvmStatic
        fun randomOnHemisphere(normal: Vector, out: Vector): Vector {
            randomInUnitSphere(out)
            unit(out, out)
            return if (dotProduct(out, normal) > 0.0f) out else out.set(-out.x, -out.y, -out.z)
        }

        @JvmStatic
        fun reflect(v: Vector, n: Vector): Vector =
            v - n * (2.0f * dotProduct(v, n))

        @JvmStatic
        fun reflect(v: Vector, n: Vector, out: Vector): Vector {
            val scale = 2.0f * dotProduct(v, n)
            return out.set(v.x - n.x * scale, v.y - n.y * scale, v.z - n.z * scale)
        }

        @JvmStatic
        fun refract(uv: Vector, n: Vector, etai_over_etat: Float): Vector {
            val cosTheta = min(dotProduct(-uv, n), 1.0f)
            val rOutPerp = (n * cosTheta + uv) * etai_over_etat
            val rOutParallel = -(n * sqrt(abs(1.0f - rOutPerp.lengthSquared())))
            return rOutPerp + rOutParallel
        }

        @JvmStatic
        fun refract(uv: Vector, n: Vector, etaiOverEtat: Float, out: Vector): Vector {
            val cosTheta = min(-(uv.x * n.x + uv.y * n.y + uv.z * n.z), 1.0f)
            val rOutPerpX = (uv.x + n.x * cosTheta) * etaiOverEtat
            val rOutPerpY = (uv.y + n.y * cosTheta) * etaiOverEtat
            val rOutPerpZ = (uv.z + n.z * cosTheta) * etaiOverEtat
            val rOutPerpLenSquared = rOutPerpX * rOutPerpX + rOutPerpY * rOutPerpY + rOutPerpZ * rOutPerpZ
            val parallelScale = -sqrt(abs(1.0f - rOutPerpLenSquared))
            return out.set(
                rOutPerpX + n.x * parallelScale,
                rOutPerpY + n.y * parallelScale,
                rOutPerpZ + n.z * parallelScale,
            )
        }
    }
}
