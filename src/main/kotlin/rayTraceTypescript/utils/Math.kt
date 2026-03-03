package rayTraceTypescript.utils

import kotlin.math.PI

const val infinity: Float = Float.POSITIVE_INFINITY


fun degreesToRadians(degrees: Float): Float = (degrees * PI / 180.0f).toFloat()
fun randomFloat(min: Float, max: Float): Float = RandomSource.nextFloat(min, max)
