package rayTraceTypescript.utils

import kotlin.random.Random

object RandomSource {
    private var rng: Random = Random.Default

    fun withSeed(seed: Long) {
        rng = Random(seed)
    }

    fun reset() {
        rng = Random.Default
    }

    fun nextDouble(): Double = rng.nextDouble()

    fun nextDouble(min: Double, max: Double): Double = rng.nextDouble(min, max)
}
