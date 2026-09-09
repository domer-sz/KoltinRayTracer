package rayTraceTypescript.utils

import kotlin.random.Random

object RandomSource {
    private var rng: Random = Random.Default
    private var seed: Long? = null

    fun withSeed(seed: Long) {
        this.seed = seed
        rng = Random(seed)
    }

    fun reset() {
        seed = null
        rng = Random.Default
    }

    /** The pinned seed, if any; the GPU backend derives its per-sample streams from it. */
    fun seedValue(): Long? = seed

    fun nextFloat(): Float = rng.nextFloat()

    fun nextFloat(min: Float, max: Float): Float = rng.nextFloat() * (max - min) + min
}
