package rayTraceTypescript.utils

import kotlin.random.Random

object RandomSource {
    private val threadLocal: ThreadLocal<Random> = ThreadLocal.withInitial { Random.Default }
    private var deterministicSeed: Long? = null

    fun withSeed(seed: Long) {
        deterministicSeed = seed
        threadLocal.set(Random(seed))
    }

    fun reset() {
        deterministicSeed = null
        threadLocal.set(Random.Default)
    }

    fun nextFloat(): Float = threadLocal.get().nextFloat()

    fun nextFloat(min: Float, max: Float): Float =
        threadLocal.get().nextFloat() * (max - min) + min

    fun isDeterministic(): Boolean = deterministicSeed != null

    fun deterministicSeedValue(): Long? = deterministicSeed

    fun <T> scoped(seed: Long?, block: () -> T): T {
        if (seed == null) return block()
        val previous = threadLocal.get()
        threadLocal.set(Random(seed))
        return try {
            block()
        } finally {
            threadLocal.set(previous)
        }
    }
}
