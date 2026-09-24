package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.utils.infinity

class QuadTest {

    /** Two units wide and two tall, lying in the z = 0 plane with its corner at the origin. */
    private val quad = Quad(
        Point(0f, 0f, 0f),
        Vector(2f, 0f, 0f),
        Vector(0f, 2f, 0f),
        Lambertian(Color(1f, 1f, 1f))
    )

    private fun shoot(origin: Point, direction: Vector) =
        quad.hit(Ray(origin, direction), Interval(0.001f, infinity))

    @Test
    fun `hits the middle and reports unit square coordinates`() {
        // u cross v points along +z, so a ray coming from +z hits the front.
        val hit = shoot(Point(1f, 1f, 2f), Vector(0f, 0f, -1f))
        assertNotNull(hit)
        assertEquals(2f, hit!!.t, 1e-5f)
        assertEquals(0.5f, hit.u, 1e-5f)
        assertEquals(0.5f, hit.v, 1e-5f)
        assertTrue(hit.frontFace)
        assertEquals(1f, hit.normal.z, 1e-5f)
    }

    @Test
    fun `flips the normal for a hit from behind`() {
        val hit = shoot(Point(1f, 1f, -2f), Vector(0f, 0f, 1f))!!
        assertTrue(!hit.frontFace)
        assertEquals(-1f, hit.normal.z, 1e-5f, "the normal turns to face the ray")
    }

    @Test
    fun `reports the corners as zero and one`() {
        val corner = shoot(Point(0.02f, 0.02f, 1f), Vector(0f, 0f, -1f))!!
        assertTrue(corner.u < 0.02f && corner.v < 0.02f, "near the Q corner")
        val far = shoot(Point(1.98f, 1.98f, 1f), Vector(0f, 0f, -1f))!!
        assertTrue(far.u > 0.98f && far.v > 0.98f, "near the opposite corner")
    }

    @Test
    fun `misses outside the parallelogram`() {
        assertNull(shoot(Point(2.5f, 1f, 1f), Vector(0f, 0f, -1f)), "past the u edge")
        assertNull(shoot(Point(1f, -0.5f, 1f), Vector(0f, 0f, -1f)), "below the v edge")
    }

    @Test
    fun `ignores rays running along the plane`() {
        assertNull(shoot(Point(-1f, 1f, 0f), Vector(1f, 0f, 0f)))
    }

    @Test
    fun `pads the flat axis of its bounding box`() {
        assertTrue(quad.aabbBoundingBox().z.size() > 0f, "a flat quad still needs a box rays can enter")
    }
}
