package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.Triangle
import rayTraceTypescript.utils.infinity

class TriangleTest {

    private val triangle = Triangle(
        Point(0f, 0f, 0f),
        Point(1f, 0f, 0f),
        Point(0f, 1f, 0f),
        Lambertian(Color(1f, 1f, 1f))
    )

    private fun shoot(origin: Point, direction: Vector) =
        triangle.hit(Ray(origin, direction), Interval(0.001f, infinity))

    @Test
    fun `hits inside the facet and reports barycentric coordinates`() {
        val hit = shoot(Point(0.25f, 0.25f, 1f), Vector(0f, 0f, -1f))
        assertNotNull(hit)
        assertEquals(1f, hit!!.t, 1e-5f)
        assertEquals(0.25f, hit.u, 1e-5f)
        assertEquals(0.25f, hit.v, 1e-5f)
    }

    @Test
    fun `misses outside the facet`() {
        assertNull(shoot(Point(0.9f, 0.9f, 1f), Vector(0f, 0f, -1f)), "beyond the hypotenuse")
        assertNull(shoot(Point(-0.2f, 0.2f, 1f), Vector(0f, 0f, -1f)), "outside the first edge")
    }

    @Test
    fun `ignores rays running along the facet plane`() {
        assertNull(shoot(Point(-1f, 0.25f, 0f), Vector(1f, 0f, 0f)))
    }

    @Test
    fun `flips the normal for a back side hit`() {
        val front = shoot(Point(0.25f, 0.25f, 1f), Vector(0f, 0f, -1f))!!
        val back = shoot(Point(0.25f, 0.25f, -1f), Vector(0f, 0f, 1f))!!
        assertTrue(front.frontFace)
        assertTrue(!back.frontFace)
        assertEquals(1f, front.normal.z, 1e-5f)
        assertEquals(-1f, back.normal.z, 1e-5f, "a mesh stays visible from both sides")
    }

    @Test
    fun `pads the flat axis of its bounding box`() {
        val box = triangle.aabbBoundingBox()
        assertTrue(box.z.size() > 0f, "a facet in an axis plane needs a thick enough box for the BVH")
    }
}
