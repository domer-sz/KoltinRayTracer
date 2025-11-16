package rayTraceTypescript.utils

import rayTraceTypescript.utils.RandomSource

const val infinity: Float = Float.POSITIVE_INFINITY
const val pi: Float = 3.1415927f

fun degreesToRadians(degrees: Float): Float = degrees * pi / 180.0f
fun randomFloat(min: Float, max: Float): Float = RandomSource.nextFloat(min, max)
