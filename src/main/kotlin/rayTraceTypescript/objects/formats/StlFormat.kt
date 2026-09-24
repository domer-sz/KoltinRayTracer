package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** STL - both the binary and the ASCII flavour. Facet normals are ignored; see Triangle. */
internal object StlFormat {

    private const val BINARY_HEADER = 84          // 80 byte comment + uint32 triangle count
    private const val BINARY_FACET = 50           // normal + 3 vertices + attribute count

    fun parse(bytes: ByteArray): Mesh =
        if (looksBinary(bytes)) parseBinary(bytes) else parseAscii(bytes)

    /**
     * A binary STL's size is exactly the header plus its declared facet count; the leading
     * "solid" keyword is not a reliable marker, some exporters write it into binary files.
     */
    private fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.size < BINARY_HEADER) return false
        val declared = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        return bytes.size.toLong() == BINARY_HEADER + declared * BINARY_FACET
    }

    private fun parseBinary(bytes: ByteArray): Mesh {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(80)
        val count = buffer.int
        val vertices = FloatArray(count * Mesh.VERTEX_FLOATS)
        for (facet in 0 until count) {
            buffer.position(BINARY_HEADER + facet * BINARY_FACET + 12)   // skip the facet normal
            for (component in 0 until Mesh.VERTEX_FLOATS) {
                vertices[facet * Mesh.VERTEX_FLOATS + component] = buffer.float
            }
        }
        return Mesh(vertices)
    }

    private fun parseAscii(bytes: ByteArray): Mesh {
        val tokens = bytes.toString(StandardCharsets.UTF_8).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val vertices = FloatList()
        var i = 0
        while (i < tokens.size) {
            if (tokens[i].equals("vertex", ignoreCase = true)) {
                require(i + 3 < tokens.size) { "Truncated vertex in ASCII STL" }
                for (component in 1..3) {
                    vertices.add(
                        tokens[i + component].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed vertex value '${tokens[i + component]}'")
                    )
                }
                i += 4
            } else {
                i += 1
            }
        }
        require(vertices.size > 0) { "No vertices found; is this an STL file?" }
        return Mesh(vertices.toFloatArray())
    }
}
