package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.objects.StlMesh
import rayTraceTypescript.objects.Triangle
import rayTraceTypescript.materials.Lambertian
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class StlMeshTest {

    /** Two facets of a unit square, offset so the mesh sits nowhere near the origin. */
    private val facets = listOf(
        floatArrayOf(10f, 4f, 0f, 11f, 4f, 0f, 11f, 6f, 0f),
        floatArrayOf(10f, 4f, 0f, 11f, 6f, 0f, 10f, 6f, 0f)
    )

    private fun binaryStl(): Path {
        val file = Files.createTempFile("mesh", ".stl")
        val buffer = ByteBuffer.allocate(84 + facets.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(80))
        buffer.putInt(facets.size)
        facets.forEach { facet ->
            repeat(3) { buffer.putFloat(0f) }          // facet normal, deliberately zero
            facet.forEach { buffer.putFloat(it) }
            buffer.putShort(0)
        }
        Files.write(file, buffer.array())
        return file
    }

    private fun asciiStl(): Path {
        val file = Files.createTempFile("mesh", ".stl")
        val text = buildString {
            append("solid test\n")
            facets.forEach { facet ->
                append("  facet normal 0 0 1\n    outer loop\n")
                for (vertex in 0 until 3) {
                    append("      vertex ${facet[vertex * 3]} ${facet[vertex * 3 + 1]} ${facet[vertex * 3 + 2]}\n")
                }
                append("    endloop\n  endfacet\n")
            }
            append("endsolid test\n")
        }
        Files.write(file, text.toByteArray(StandardCharsets.UTF_8))
        return file
    }

    @Test
    fun `reads binary stl files`() {
        val mesh = StlMesh.load(binaryStl())
        assertEquals(2, mesh.triangleCount)
        assertEquals(10f, mesh.vertices[0])
        assertEquals(4f, mesh.vertices[1])
        assertEquals(6f, mesh.vertices[7], "third vertex of the first facet")
    }

    @Test
    fun `ascii and binary files load to the same mesh`() {
        val binary = StlMesh.load(binaryStl())
        val ascii = StlMesh.load(asciiStl())
        assertEquals(binary.triangleCount, ascii.triangleCount)
        binary.vertices.indices.forEach { assertEquals(binary.vertices[it], ascii.vertices[it], 1e-6f) }
    }

    @Test
    fun `standing on the floor centers the model and rests it on y zero`() {
        val placed = StlMesh.load(binaryStl()).standingOnFloor()
        val box = placed.bounds()
        assertEquals(0f, box.y.min, 1e-5f, "lowest point sits on the floor")
        assertEquals(StlMesh.DEFAULT_TARGET_HEIGHT, box.y.size(), 1e-5f, "scaled to the default height")
        assertEquals(0f, (box.x.min + box.x.max) / 2f, 1e-5f, "centered in x")
        assertEquals(0f, (box.z.min + box.z.max) / 2f, 1e-5f, "centered in z")
    }

    @Test
    fun `keeps the original size when no target height is given`() {
        val placed = StlMesh.load(binaryStl()).standingOnFloor(targetHeight = null)
        assertEquals(2f, placed.bounds().y.size(), 1e-5f)
    }

    @Test
    fun `builds one triangle per facet`() {
        val hittables = StlMesh.load(binaryStl()).toHittables(Lambertian(Color(1f, 1f, 1f)))
        assertEquals(2, hittables.size)
        assertTrue(hittables.all { it is Triangle })
    }
}
