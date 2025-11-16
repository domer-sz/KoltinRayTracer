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

    fun nextFloat(): Float = rng.nextFloat()

    fun nextFloat(min: Float, max: Float): Float = rng.nextFloat() * (max - min) + min
}
