package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import rayTraceTypescript.gpu.SceneBuffers
import rayTraceTypescript.gpu.SceneFlattener
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.objects.checkeredBox
import rayTraceTypescript.textures.UvCheckerTexture

class UvCheckerTextureTest {
    private val white = Color(1f, 1f, 1f)
    private val black = Color(0f, 0f, 0f)
    private val origin = Point(0f, 0f, 0f)

    @Test
    fun `cells alternate across u and v`() {
        val texture = UvCheckerTexture(4f, 2f, white, black)
        assertEquals(white.r, texture.value(0.1f, 0.1f, origin).r)
        assertEquals(black.r, texture.value(0.3f, 0.1f, origin).r)
        assertEquals(black.r, texture.value(0.1f, 0.6f, origin).r)
        assertEquals(white.r, texture.value(0.3f, 0.6f, origin).r)
    }

    @Test
    fun `the far edge belongs to the last cell`() {
        val texture = UvCheckerTexture(4f, 2f, white, black)
        // u = 1 would be cell 4 of 0..3; it has to read as cell 3, like u = 0.99.
        assertEquals(texture.value(0.99f, 0.1f, origin).r, texture.value(1.0f, 0.1f, origin).r)
    }

    @Test
    fun `a checkered box counts square cells per face`() {
        val faces = checkeredBox(origin, Point(165f, 330f, 165f), 27.5f, white, black).objects.map { it as Quad }
        // Per face, the cell counts along its two edges, smaller first.
        val counts = faces.map { face ->
            val texture = (face.material as Lambertian).texture as UvCheckerTexture
            listOf(texture.cellsU, texture.cellsV).sorted()
        }
        assertEquals(4, counts.count { it == listOf(6f, 12f) }, "the four tall sides")
        assertEquals(2, counts.count { it == listOf(6f, 6f) }, "top and bottom")
    }

    @Test
    fun `the gpu gets the checker as texture type 5`() {
        val buffers = SceneFlattener.flatten(checkeredBox(origin, Point(165f, 330f, 165f), 27.5f, white, black))
        val types = buffers.textureInts.filterIndexed { i, _ -> i % SceneBuffers.TEXTURE_INT_STRIDE == 0 }
        assertEquals(6, types.count { it == 5 })
        assertEquals(true, buffers.kernelDefines().contains("-DTEX_UVCHECKER=1"))
    }
}
