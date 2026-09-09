package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.gpu.SceneBuffers
import rayTraceTypescript.gpu.SceneFlattener
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Sphere

class SceneFlattenerTest {

    private fun scene() = HittableList(
        mutableListOf(
            Sphere(Point(0.0f, -1000.0f, 0.0f), 1000.0f, Lambertian(Color(0.5f, 0.5f, 0.5f))),
            Sphere(Point(0.0f, 1.0f, 0.0f), 1.0f, Dielectric(1.5f)),
            Sphere(Point(-4.0f, 1.0f, 0.0f), 1.0f, Lambertian(Color(0.4f, 0.2f, 0.1f))),
            Sphere(Point(4.0f, 1.0f, 0.0f), 1.0f, Metal(Color(0.7f, 0.6f, 0.5f), 0.0f))
        )
    )

    @Test
    fun `keeps every sphere and material`() {
        val buffers = SceneFlattener.flatten(scene())
        assertEquals(4, buffers.sphereCount)
        assertEquals(4, buffers.materialInts.size / SceneBuffers.MATERIAL_INT_STRIDE)
        // 3 textures: two distinct Lambertian colors plus the metal albedo; the dielectric has none.
        assertEquals(3, buffers.textureInts.size / SceneBuffers.TEXTURE_INT_STRIDE)
        assertEquals(4, buffers.spheres.size / SceneBuffers.SPHERE_STRIDE)
    }

    @Test
    fun `root bounds cover the world`() {
        val world = scene()
        val buffers = SceneFlattener.flatten(world)
        val box = world.aabbBoundingBox()
        val root = buffers.rootNode * SceneBuffers.NODE_BOUNDS_STRIDE
        assertEquals(box.x.min, buffers.nodeBounds[root])
        assertEquals(box.y.min, buffers.nodeBounds[root + 1])
        assertEquals(box.z.min, buffers.nodeBounds[root + 2])
        assertEquals(box.x.max, buffers.nodeBounds[root + 3])
        assertEquals(box.y.max, buffers.nodeBounds[root + 4])
        assertEquals(box.z.max, buffers.nodeBounds[root + 5])
    }

    @Test
    fun `bvh trees stay within the kernel traversal stack`() {
        val buffers = SceneFlattener.flatten(HittableList(mutableListOf(BvhNode(prepareWorld()))))
        assertTrue(
            buffers.maxDepth <= SceneBuffers.MAX_TRAVERSAL_DEPTH,
            "Tree depth ${buffers.maxDepth} exceeds the kernel stack"
        )
        assertEquals(prepareWorld().objects.size, buffers.sphereCount)
    }
}
