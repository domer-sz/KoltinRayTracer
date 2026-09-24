package rayTraceTypescript.utils

/**
 * A small JSON reader, enough for glTF manifests: objects become maps, arrays become lists,
 * numbers become doubles. The project has no JSON dependency and one format needing one does
 * not justify adding it.
 */
object Json {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWhitespace()
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun array(value: Any?): List<Any?>? = value as? List<Any?>

    fun int(value: Any?): Int? = (value as? Double)?.toInt()

    private class Parser(private val text: String) {
        private var at = 0

        fun value(): Any? {
            skipWhitespace()
            require(at < text.length) { "JSON ends unexpectedly" }
            return when (val c = text[at]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) numberValue()
                        else throw IllegalArgumentException("Unexpected character '$c' at $at")
            }
        }

        fun skipWhitespace() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        private fun objectValue(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            at++                                          // {
            skipWhitespace()
            if (at < text.length && text[at] == '}') { at++; return result }
            while (true) {
                skipWhitespace()
                val key = stringValue()
                skipWhitespace()
                require(text[at] == ':') { "Expected ':' at $at" }
                at++
                result[key] = value()
                skipWhitespace()
                when (text[at]) {
                    ',' -> at++
                    '}' -> { at++; return result }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $at")
                }
            }
        }

        private fun arrayValue(): List<Any?> {
            val result = ArrayList<Any?>()
            at++                                          // [
            skipWhitespace()
            if (at < text.length && text[at] == ']') { at++; return result }
            while (true) {
                result += value()
                skipWhitespace()
                when (text[at]) {
                    ',' -> at++
                    ']' -> { at++; return result }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $at")
                }
            }
        }

        private fun stringValue(): String {
            require(text[at] == '"') { "Expected a string at $at" }
            at++
            val builder = StringBuilder()
            while (text[at] != '"') {
                if (text[at] == '\\') {
                    at++
                    when (val escape = text[at]) {
                        '"', '\\', '/' -> builder.append(escape)
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            builder.append(text.substring(at + 1, at + 5).toInt(16).toChar())
                            at += 4
                        }
                        else -> throw IllegalArgumentException("Bad escape '\\$escape' at $at")
                    }
                } else {
                    builder.append(text[at])
                }
                at++
            }
            at++
            return builder.toString()
        }

        private fun numberValue(): Double {
            val start = at
            if (text[at] == '-') at++
            while (at < text.length && (text[at].isDigit() || text[at] in ".eE+-")) at++
            return text.substring(start, at).toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            require(text.startsWith(word, at)) { "Expected '$word' at $at" }
            at += word.length
            return value
        }
    }
}
