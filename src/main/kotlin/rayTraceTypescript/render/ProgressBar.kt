package rayTraceTypescript.render

/** The `[####....] 42%` bar printed while rendering; shared by every backend. */
class ProgressBar(private val total: Int) {
    private var lastPct = -1

    fun report(done: Int) {
        val pct = (done * 100) / total
        if (pct > lastPct) {
            val barWidth = 40
            val filled = (pct * barWidth) / 100
            val bar = buildString {
                append('[')
                repeat(filled) { append('#') }
                repeat(barWidth - filled) { append('.') }
                append(']')
            }
            print("\r$bar $pct%")
            System.out.flush()
            lastPct = if (pct < 100) pct else 100
        }
    }

    fun finish() {
        println("\r[" + "#".repeat(40) + "] 100%")
    }
}
