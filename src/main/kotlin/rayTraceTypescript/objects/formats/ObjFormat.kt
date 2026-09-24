package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh
import java.nio.charset.StandardCharsets

/**
 * Wavefront OBJ. Only geometry is read: `v` vertices and `f` faces, with the texture and normal
 * parts of a face corner ignored, since materials come from the scene rather than the file.
 * Faces may be polygons and may index vertices from the end with negative numbers.
 */
internal object ObjFormat {

    fun parse(bytes: ByteArray): Mesh {
        val positions = FloatList()
        val builder = MeshBuilder()

        bytes.toString(StandardCharsets.UTF_8).lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> {
                    require(parts.size >= 4) { "Vertex needs three coordinates: '$line'" }
                    positions.add(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
                }
                "f" -> {
                    val corners = parts.drop(1)
                    if (corners.size >= 3) {
                        builder.polygon(positions, IntArray(corners.size) { resolve(corners[it], positions.tripleCount) })
                    }
                }
            }
        }

        require(builder.triangleCount > 0) { "No faces found; is this an OBJ file?" }
        return builder.build()
    }

    /** `f 1/2/3` - only the vertex index matters; negative indices count back from the end. */
    private fun resolve(corner: String, vertexCount: Int): Int {
        val index = corner.substringBefore('/').toIntOrNull()
            ?: throw IllegalArgumentException("Malformed face corner '$corner'")
        return if (index > 0) index - 1 else vertexCount + index
    }
}
