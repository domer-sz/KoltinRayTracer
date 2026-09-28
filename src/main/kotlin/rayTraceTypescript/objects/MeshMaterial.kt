package rayTraceTypescript.objects

import rayTraceTypescript.Color
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal

/**
 * A material a model file names for some of its faces - an entry of an OBJ's .mtl - reduced
 * to what this renderer can draw: a [colour] (linear, as `Kd` holds it), how [metallic] and how
 * rough the surface is, light it gives off, and glass.
 */
class MeshMaterial(
    val name: String,
    val colour: Color,
    val metallic: Float = 0.0f,
    val roughness: Float = 0.0f,
    val emission: Color? = null,
    /** Below one for glass (the .mtl's `d`), with [refractiveIndex] as its `Ni`. */
    val opacity: Float = 1.0f,
    val refractiveIndex: Float = 1.5f
) {
    fun material(): Material = when {
        emission != null && (emission.r > 0f || emission.g > 0f || emission.b > 0f) -> DiffuseLight(emission)
        opacity < 0.5f -> Dielectric(refractiveIndex.coerceAtLeast(1.0f))
        metallic >= 0.5f -> Metal(colour, roughness.coerceIn(0.0f, 1.0f))
        else -> Lambertian(colour)
    }
}
