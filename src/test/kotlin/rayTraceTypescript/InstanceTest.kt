package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.RotateY
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.objects.Translate
import rayTraceTypescript.objects.box
import rayTraceTypescript.utils.infinity

class InstanceTest {

    private val white = Lambertian(Color(0.73f, 0.73f, 0.73f))
    private val unitSphere = Sphere(Point(0f, 0f, 0f), 1f, white)

    private fun shoot(target: Hittable, origin: Point, direction: Vector) =
        target.hit(Ray(origin, direction), Interval(0.001f, infinity))

    @Test
    fun `translate moves both the object and its box`() {
        val moved = Translate(unitSphere, Vector(10f, 0f, 0f))

        assertNull(shoot(moved, Point(0f, 0f, -5f), Vector(0f, 0f, 1f)), "nothing left at the origin")

        val hit = shoot(moved, Point(10f, 0f, -5f), Vector(0f, 0f, 1f))
        assertNotNull(hit)
        assertEquals(10f, hit!!.point.x, 1e-4f, "the hit comes back in world space")
        assertEquals(-1f, hit.point.z, 1e-4f)

        val box = moved.aabbBoundingBox()
        assertEquals(9f, box.x.min, 1e-4f)
        assertEquals(11f, box.x.max, 1e-4f)
    }

    @Test
    fun `rotate y turns the object around the axis`() {
        // A block one unit wide in x and four deep in z, spun a quarter turn.
        val block = box(Point(-0.5f, -0.5f, -2f), Point(0.5f, 0.5f, 2f), white)
        val rotated = RotateY(block, 90.0f)

        val box = rotated.aabbBoundingBox()
        assertEquals(4f, box.x.size(), 1e-3f, "depth and width swap places")
        assertEquals(1f, box.z.size(), 1e-3f)

        val hit = shoot(rotated, Point(1.8f, 0f, -5f), Vector(0f, 0f, 1f))
        assertNotNull(hit, "the far end of the block now reaches out along x")
    }

    @Test
    fun `rotation keeps the normal pointing at the ray`() {
        val hit = shoot(RotateY(unitSphere, 45.0f), Point(0f, 0f, -5f), Vector(0f, 0f, 1f))!!
        assertEquals(-1f, hit.normal.z, 1e-4f)
        assertEquals(0f, hit.normal.x, 1e-4f)
    }
}
