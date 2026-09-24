package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Triangles read from an STL file, stored as flat vertex data (9 floats per facet: three
 * vertices, each x/y/z). Both STL flavours are supported - binary and ASCII.
 *
 * STL carries no units, origin or orientation convention, so a freshly loaded mesh can sit
 * anywhere and be any size. [standingOnFloor] is the default placement: it scales the model to
 * a workable height and puts it centred on the floor, where the scenes in this project put
 * their ground.
 */
class StlMesh(val vertices: FloatArray) {

    init {
        require(vertices.isNotEmpty()) { "Mesh has no triangles" }
        require(vertices.size % VERTEX_FLOATS == 0) { "Expected $VERTEX_FLOATS floats per triangle" }
    }

    val triangleCount: Int get() = vertices.size / VERTEX_FLOATS

    fun bounds(): Aabb {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < vertices.size) {
            val x = vertices[i]; val y = vertices[i + 1]; val z = vertices[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        return Aabb.fromPoints(Point(minX, minY, minZ), Point(maxX, maxY, maxZ))
    }

    /**
     * Centres the model over the origin in x/z, rests its lowest point on [floorY] and - unless
     * [targetHeight] is null - scales it uniformly to that height, so models authored in
     * millimetres and models authored in metres both arrive at a sensible size.
     */
    fun standingOnFloor(targetHeight: Float? = DEFAULT_TARGET_HEIGHT, floorY: Float = 0.0f): StlMesh {
        val box = bounds()
        val height = box.y.size()
        val scale = if (targetHeight == null || height <= 0.0f) 1.0f else targetHeight / height
        val pivot = Vector((box.x.min + box.x.max) / 2.0f, box.y.min, (box.z.min + box.z.max) / 2.0f)
        return transformed(scale, pivot, Vector(0.0f, floorY, 0.0f))
    }

    /** Moves every vertex to `(vertex - pivot) * scale + target`. */
    fun transformed(scale: Float, pivot: Vector, target: Vector): StlMesh {
        val moved = FloatArray(vertices.size)
        var i = 0
        while (i < vertices.size) {
            moved[i] = (vertices[i] - pivot.x) * scale + target.x
            moved[i + 1] = (vertices[i + 1] - pivot.y) * scale + target.y
            moved[i + 2] = (vertices[i + 2] - pivot.z) * scale + target.z
            i += 3
        }
        return StlMesh(moved)
    }

    fun toHittables(material: Material): List<Hittable> = (0 until triangleCount).map { index ->
        val at = index * VERTEX_FLOATS
        Triangle(
            Point(vertices[at], vertices[at + 1], vertices[at + 2]),
            Point(vertices[at + 3], vertices[at + 4], vertices[at + 5]),
            Point(vertices[at + 6], vertices[at + 7], vertices[at + 8]),
            material
        )
    }

    companion object {
        /** Height a loaded model is scaled to, in scene units - roughly the big spheres' size. */
        const val DEFAULT_TARGET_HEIGHT = 2.0f

        private const val VERTEX_FLOATS = 9
        private const val BINARY_HEADER = 84          // 80 byte comment + uint32 triangle count
        private const val BINARY_FACET = 50           // normal + 3 vertices + attribute count

        fun load(path: String): StlMesh = load(Paths.get(path))

        fun load(path: Path): StlMesh {
            require(Files.isRegularFile(path)) { "STL file not found: $path" }
            val bytes = Files.readAllBytes(path)
            return if (looksBinary(bytes)) parseBinary(bytes) else parseAscii(bytes)
        }

        /**
         * A binary STL's size is exactly the header plus its declared facet count; the leading
         * "solid" keyword is not a reliable marker, some exporters write it into binary files.
         */
        private fun looksBinary(bytes: ByteArray): Boolean {
            if (bytes.size < BINARY_HEADER) return false
            val declared = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            return bytes.size.toLong() == BINARY_HEADER + declared * BINARY_FACET
        }

        private fun parseBinary(bytes: ByteArray): StlMesh {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(80)
            val count = buffer.int
            val vertices = FloatArray(count * VERTEX_FLOATS)
            for (facet in 0 until count) {
                buffer.position(BINARY_HEADER + facet * BINARY_FACET + 12)   // skip the facet normal
                for (component in 0 until VERTEX_FLOATS) {
                    vertices[facet * VERTEX_FLOATS + component] = buffer.float
                }
            }
            return StlMesh(vertices)
        }

        private fun parseAscii(bytes: ByteArray): StlMesh {
            val tokens = bytes.toString(StandardCharsets.UTF_8).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val vertices = ArrayList<Float>(tokens.size / 4)
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
            require(vertices.isNotEmpty()) { "No vertices found; is this an STL file?" }
            return StlMesh(vertices.toFloatArray())
        }
    }
}
