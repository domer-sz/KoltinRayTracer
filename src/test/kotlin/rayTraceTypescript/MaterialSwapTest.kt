package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.gui.BuiltInObject
import rayTraceTypescript.gui.MaterialKind
import rayTraceTypescript.gui.MaterialPreset
import rayTraceTypescript.gui.MaterialSwap
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.objects.RotateY
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.objects.Translate
import rayTraceTypescript.objects.box
import rayTraceTypescript.scenes.ScenePart
import rayTraceTypescript.textures.SolidColorTexture

class MaterialSwapTest {

    private val white = Lambertian(Color(0.73f, 0.73f, 0.73f))

    @Test
    fun `a rebuilt object keeps its shape and place and takes the new material`() {
        val column = Translate(RotateY(box(Point(0f, 0f, 0f), Point(165f, 330f, 165f), white), 15f), Vector(265f, 0f, 295f))
        val steel = Metal(Color(0.6f, 0.6f, 0.62f), 0.2f)
        val rebuilt = MaterialSwap.rebuild(column) { steel }
        assertTrue(MaterialSwap.firstMaterial(rebuilt) === steel)
        val a = column.aabbBoundingBox()
        val b = rebuilt.aabbBoundingBox()
        for ((p, q) in listOf(a.x to b.x, a.y to b.y, a.z to b.z)) {
            assertEquals(p.min, q.min, 1e-3f)
            assertEquals(p.max, q.max, 1e-3f)
        }
    }

    @Test
    fun `a scene object shows its own material and goes back to it`() {
        val sphere = BuiltInObject(ScenePart("Szklana kula", Sphere(Point(190f, 90f, 190f), 90f, Dielectric(1.5f))))
        assertEquals(MaterialKind.GLASS, sphere.original.kind)
        assertEquals(1.5, sphere.original.parameter, 1e-6)
        sphere.material = MaterialPreset.GOLD.spec
        assertTrue(sphere.changed)
        assertTrue(MaterialSwap.firstMaterial(sphere.hittable()) is Metal)
        sphere.material = sphere.original
        assertTrue(!sphere.changed)
        assertTrue(MaterialSwap.firstMaterial(sphere.hittable()) is Dielectric)
    }

    @Test
    fun `a light's power scales what it gives off`() {
        val lamp = Quad(Point(343f, 554f, 332f), Vector(-130f, 0f, 0f), Vector(0f, 0f, -105f), DiffuseLight(Color(15f, 15f, 15f)))
        val light = BuiltInObject(ScenePart("Lampa sufitowa", lamp, isLight = true))
        light.power = 2.5
        val emitted = ((MaterialSwap.firstMaterial(light.hittable()) as DiffuseLight).texture as SolidColorTexture).albedo
        assertEquals(37.5f, emitted.r, 1e-4f)
        light.power = 1.0
        assertTrue(light.hittable() === lamp, "at its own power the light is the scene's own")
    }
}
