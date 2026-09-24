package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.materials.Isotropic
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.ConstantMedium
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.objects.box
import rayTraceTypescript.utils.RandomSource
import rayTraceTypescript.utils.infinity

class ConstantMediumTest {

    private val white = Lambertian(Color(1f, 1f, 1f))
    private val boundary = box(Point(-1f, -1f, -1f), Point(1f, 1f, 1f), white)

    private fun scatters(density: Float, rays: Int = 2000): Int {
        val fog = ConstantMedium(boundary, density, Color(1f, 1f, 1f))
        RandomSource.withSeed(99L)
        return try {
            (0 until rays).count {
                fog.hit(Ray(Point(0f, 0f, -5f), Vector(0f, 0f, 1f)), Interval(0.001f, infinity)) != null
            }
        } finally {
            RandomSource.reset()
        }
    }

    @Test
    fun `dense fog stops nearly every ray, thin fog lets them through`() {
        val dense = scatters(5.0f)
        val thin = scatters(0.01f)
        assertTrue(dense > 1900, "dense fog scattered only $dense of 2000 rays")
        assertTrue(thin < 100, "thin fog scattered $thin of 2000 rays")
    }

    @Test
    fun `a scatter happens inside the boundary and uses the phase function`() {
        val fog = ConstantMedium(boundary, 5.0f, Color(0.5f, 0.5f, 0.5f))
        RandomSource.withSeed(3L)
        try {
            val hit = fog.hit(Ray(Point(0f, 0f, -5f), Vector(0f, 0f, 1f)), Interval(0.001f, infinity))
            assertNotNull(hit)
            assertTrue(hit!!.point.z in -1.001f..1.001f, "scattered at z=${hit.point.z}, outside the box")
            assertTrue(hit.material is Isotropic)
        } finally {
            RandomSource.reset()
        }
    }

    @Test
    fun `the volume borrows its bounding box from the boundary`() {
        val sphere = Sphere(Point(0f, 0f, 0f), 2f, white)
        val fog = ConstantMedium(sphere, 0.5f, Color(1f, 1f, 1f))
        assertEquals(sphere.aabbBoundingBox().x.min, fog.aabbBoundingBox().x.min, 1e-5f)
        assertEquals(sphere.aabbBoundingBox().y.max, fog.aabbBoundingBox().y.max, 1e-5f)
    }
}
