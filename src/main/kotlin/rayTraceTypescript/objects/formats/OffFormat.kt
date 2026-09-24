package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh
import java.nio.charset.StandardCharsets

/**
 * Geomview OFF. Parsed line by line rather than token by token, so the per-vertex extras of the
 * COFF/NOFF variants (colors, normals) fall off the end of each vertex line on their own.
 */
internal object OffFormat {

    fun parse(bytes: ByteArray): Mesh {
        val lines = bytes.toString(StandardCharsets.UTF_8)
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .iterator()

        require(lines.hasNext()) { "Empty OFF file" }
        var header = lines.next()
        require(header.uppercase().endsWith("OFF")) { "Missing the OFF keyword" }
        // The counts may share the header line or sit on the next one.
        val counts = header.split(Regex("\\s+")).drop(1).ifEmpty {
            require(lines.hasNext()) { "OFF file ends before its counts" }
            lines.next().split(Regex("\\s+"))
        }
        require(counts.size >= 2) { "OFF header needs a vertex and a face count" }
        val vertexCount = counts[0].toInt()
        val faceCount = counts[1].toInt()

        val positions = FloatList(vertexCount * 3)
        repeat(vertexCount) {
            require(lines.hasNext()) { "OFF file ends inside its vertex list" }
            val parts = lines.next().split(Regex("\\s+"))
            positions.add(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
        }

        val builder = MeshBuilder(faceCount)
        repeat(faceCount) {
            require(lines.hasNext()) { "OFF file ends inside its face list" }
            val parts = lines.next().split(Regex("\\s+"))
            val corners = parts[0].toInt()
            builder.polygon(positions, IntArray(corners) { parts[it + 1].toInt() })
        }

        require(builder.triangleCount > 0) { "OFF file holds no faces" }
        return builder.build()
    }
}
