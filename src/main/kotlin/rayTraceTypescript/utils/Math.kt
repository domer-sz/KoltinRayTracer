package rayTraceTypescript.utils

import rayTraceTypescript.utils.RandomSource

const val infinity: Double = Double.POSITIVE_INFINITY
const val pi: Double = 3.141592653589793

fun degreesToRadians(degrees: Double): Double = degrees * pi / 180.0
fun randomFloat(min: Double, max: Double): Double = RandomSource.nextDouble(min, max)
