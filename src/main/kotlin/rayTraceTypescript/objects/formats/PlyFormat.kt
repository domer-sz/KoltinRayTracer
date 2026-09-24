package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Stanford PLY - ASCII and both binary byte orders, the format most scanned models ship in.
 *
 * A PLY file declares its own record layout, so the header is turned into a property table and
 * every element is then read in order. Elements this renderer does not care about still have to
 * be read, otherwise the cursor would drift out of step with the file.
 */
internal object PlyFormat {

    private enum class Scalar(val size: Int) {
        I8(1), U8(1), I16(2), U16(2), I32(4), U32(4), F32(4), F64(8);

        companion object {
            fun of(name: String): Scalar = when (name.lowercase()) {
                "char", "int8" -> I8
                "uchar", "uint8" -> U8
                "short", "int16" -> I16
                "ushort", "uint16" -> U16
                "int", "int32" -> I32
                "uint", "uint32" -> U32
                "float", "float32" -> F32
                "double", "float64" -> F64
                else -> throw IllegalArgumentException("Unknown PLY property type '$name'")
            }
        }
    }

    private class Property(val name: String, val type: Scalar, val countType: Scalar?)
    private class Element(val name: String, val count: Int, val properties: MutableList<Property> = mutableListOf())

    private interface Cursor {
        fun read(type: Scalar): Double
    }

    private class AsciiCursor(text: String) : Cursor {
        private val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.iterator()
        override fun read(type: Scalar): Double {
            require(tokens.hasNext()) { "PLY file ends before its declared data" }
            return tokens.next().toDouble()
        }
    }

    private class BinaryCursor(private val buffer: ByteBuffer) : Cursor {
        override fun read(type: Scalar): Double = when (type) {
            Scalar.I8 -> buffer.get().toDouble()
            Scalar.U8 -> (buffer.get().toInt() and 0xFF).toDouble()
            Scalar.I16 -> buffer.short.toDouble()
            Scalar.U16 -> (buffer.short.toInt() and 0xFFFF).toDouble()
            Scalar.I32 -> buffer.int.toDouble()
            Scalar.U32 -> (buffer.int.toLong() and 0xFFFFFFFFL).toDouble()
            Scalar.F32 -> buffer.float.toDouble()
            Scalar.F64 -> buffer.double
        }
    }

    fun parse(bytes: ByteArray): Mesh {
        val headerEnd = indexOfHeaderEnd(bytes)
        val header = String(bytes, 0, headerEnd, StandardCharsets.US_ASCII)
        val lines = header.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull()?.lowercase() == "ply") { "Missing the PLY magic line" }

        var format = "ascii"
        val elements = mutableListOf<Element>()
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            when (parts[0].lowercase()) {
                "format" -> format = parts[1].lowercase()
                "element" -> elements += Element(parts[1].lowercase(), parts[2].toInt())
                "property" -> {
                    val element = elements.lastOrNull() ?: throw IllegalArgumentException("Property outside an element")
                    element.properties += if (parts[1].equals("list", ignoreCase = true)) {
                        Property(parts[4].lowercase(), Scalar.of(parts[3]), Scalar.of(parts[2]))
                    } else {
                        Property(parts[2].lowercase(), Scalar.of(parts[1]), null)
                    }
                }
            }
        }

        val cursor = when (format) {
            "ascii" -> AsciiCursor(String(bytes, headerEnd, bytes.size - headerEnd, StandardCharsets.US_ASCII))
            "binary_little_endian", "binary_big_endian" -> BinaryCursor(
                ByteBuffer.wrap(bytes, headerEnd, bytes.size - headerEnd)
                    .order(if (format.endsWith("little_endian")) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            )
            else -> throw IllegalArgumentException("Unsupported PLY format '$format'")
        }

        val positions = FloatList()
        val builder = MeshBuilder()
        for (element in elements) {
            repeat(element.count) { readRow(element, cursor, positions, builder) }
        }

        require(builder.triangleCount > 0) { "PLY file holds no faces" }
        return builder.build()
    }

    private fun readRow(element: Element, cursor: Cursor, positions: FloatList, builder: MeshBuilder) {
        var x = 0.0f; var y = 0.0f; var z = 0.0f
        var face: IntArray? = null

        for (property in element.properties) {
            if (property.countType != null) {
                val count = cursor.read(property.countType).toInt()
                val values = IntArray(count) { cursor.read(property.type).toInt() }
                if (face == null) face = values
                continue
            }
            val value = cursor.read(property.type).toFloat()
            when (property.name) {
                "x" -> x = value
                "y" -> y = value
                "z" -> z = value
            }
        }

        when (element.name) {
            "vertex" -> positions.add(x, y, z)
            "face" -> face?.let { builder.polygon(positions, it) }
        }
    }

    private fun indexOfHeaderEnd(bytes: ByteArray): Int {
        val marker = "end_header".toByteArray(StandardCharsets.US_ASCII)
        outer@ for (start in 0..bytes.size - marker.size) {
            for (i in marker.indices) if (bytes[start + i] != marker[i]) continue@outer
            var end = start + marker.size
            if (end < bytes.size && bytes[end] == '\r'.code.toByte()) end++
            if (end < bytes.size && bytes[end] == '\n'.code.toByte()) end++
            return end
        }
        throw IllegalArgumentException("PLY header has no end_header line")
    }
}
