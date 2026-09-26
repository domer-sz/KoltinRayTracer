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
import rayTraceTypescript.objects.Triangle

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
        // The root stores its two children's boxes; together they must cover the world.
        val at = buffers.rootNode * SceneBuffers.NODE_INTS
        fun f(k: Int) = java.lang.Float.intBitsToFloat(buffers.nodes[at + k])
        assertEquals(box.x.min, minOf(f(0), f(4)))
        assertEquals(box.x.max, maxOf(f(1), f(5)))
        assertEquals(box.y.min, minOf(f(2), f(6)))
        assertEquals(box.y.max, maxOf(f(3), f(7)))
        assertEquals(box.z.min, minOf(f(8), f(10)))
        assertEquals(box.z.max, maxOf(f(9), f(11)))
    }

    @Test
    fun `flattens triangles as their own leaf kind`() {
        val material = Lambertian(Color(0.7f, 0.3f, 0.2f))
        val world = HittableList(
            mutableListOf(
                Sphere(Point(0.0f, -1000.0f, 0.0f), 1000.0f, Lambertian(Color(0.5f, 0.5f, 0.5f))),
                Triangle(Point(0.0f, 0.0f, 0.0f), Point(1.0f, 0.0f, 0.0f), Point(0.0f, 1.0f, 0.0f), material),
                Triangle(Point(0.0f, 0.0f, 0.0f), Point(0.0f, 1.0f, 0.0f), Point(0.0f, 0.0f, 1.0f), material)
            )
        )
        val buffers = SceneFlattener.flatten(world)

        assertEquals(2, buffers.triangleCount)
        assertEquals(1, buffers.sphereCount)
        assertEquals(2, buffers.triangles.size / SceneBuffers.TRIANGLE_STRIDE)

        val leafKinds = buffers.refs.filterIndexed { i, _ -> i % 2 == 0 }
        assertEquals(2, leafKinds.count { it == SceneBuffers.LEAF_TRIANGLE })
        assertEquals(1, leafKinds.count { it == SceneBuffers.LEAF_SPHERE })

        // First vertex and both edges, each padded to a float4, exactly as the kernel reads them.
        val first = buffers.triangles.copyOfRange(0, SceneBuffers.TRIANGLE_STRIDE)
        assertTrue(
            first.contentEquals(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f)) ||
                first.contentEquals(floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f))
        )
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
