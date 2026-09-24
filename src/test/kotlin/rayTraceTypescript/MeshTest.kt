package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.Mesh
import rayTraceTypescript.objects.Triangle

class MeshTest {

    /** A single facet, two units tall, sitting well away from the origin. */
    private val mesh = Mesh(floatArrayOf(10f, 4f, 0f, 11f, 4f, 0f, 11f, 6f, 0f))

    @Test
    fun `standing on the floor centers the model and rests it on y zero`() {
        val box = mesh.standingOnFloor().bounds()
        assertEquals(0f, box.y.min, 1e-5f, "lowest point sits on the floor")
        assertEquals(Mesh.DEFAULT_TARGET_HEIGHT, box.y.size(), 1e-5f, "scaled to the default height")
        assertEquals(0f, (box.x.min + box.x.max) / 2f, 1e-5f, "centered in x")
        assertEquals(0f, (box.z.min + box.z.max) / 2f, 1e-5f, "centered in z")
    }

    @Test
    fun `keeps the authored size when no target height is given`() {
        val box = mesh.standingOnFloor(targetHeight = null).bounds()
        assertEquals(2f, box.y.size(), 1e-5f)
        assertEquals(0f, box.y.min, 1e-5f, "still dropped onto the floor")
    }

    @Test
    fun `builds one triangle per facet`() {
        val hittables = mesh.toHittables(Lambertian(Color(1f, 1f, 1f)))
        assertEquals(1, hittables.size)
        assertTrue(hittables.single() is Triangle)
    }
}
