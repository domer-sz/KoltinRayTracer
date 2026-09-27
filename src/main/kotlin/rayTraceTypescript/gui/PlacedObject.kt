package rayTraceTypescript.gui

import rayTraceTypescript.Color
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.Mesh
import rayTraceTypescript.scenes.ScenePart

/** An object listed in the viewer: part of the scene itself, or a model added there. */
sealed interface SceneEntry

/**
 * An object or light the scene came with; the viewer can take it out (switch it off) and put it
 * back, not move it.
 */
class BuiltInObject(val part: ScenePart) : SceneEntry {
    @Volatile var enabled = part.enabled

    override fun toString() = part.name
}

/**
 * The kinds of material a placed model can be made of, and what their one number means:
 * [parameterLabel] with its range and step, or null when the kind has none.
 */
enum class MaterialKind(
    val label: String,
    val parameterLabel: String?,
    val min: Double = 0.0,
    val max: Double = 1.0,
    val step: Double = 0.05
) {
    MATTE("Matowy", null),
    METAL("Metal", "Chropowatość", 0.0, 1.0, 0.05),
    GLASS("Szkło", "Wsp. załamania", 1.0, 3.0, 0.05),
    LIGHT("Świecący", "Moc", 0.1, 100.0, 0.5);

    override fun toString() = label
}

/**
 * What a placed model is made of: a [kind], a colour ([red], [green], [blue] as albedo or
 * emission, 0..1, linear) and the kind's [parameter] - roughness for metal, refractive index for
 * glass, strength for a light. Glass ignores the colour, as the renderer's glass is clear.
 */
data class MaterialSpec(
    val kind: MaterialKind,
    val red: Float,
    val green: Float,
    val blue: Float,
    val parameter: Double = 0.0
) {
    fun material(): Material = when (kind) {
        MaterialKind.MATTE -> Lambertian(Color(red, green, blue))
        MaterialKind.METAL -> Metal(Color(red, green, blue), parameter.toFloat().coerceIn(0f, 1f))
        MaterialKind.GLASS -> Dielectric(parameter.toFloat().coerceAtLeast(1f))
        MaterialKind.LIGHT -> DiffuseLight(Color(red * parameter.toFloat(), green * parameter.toFloat(), blue * parameter.toFloat()))
    }

    /** The colour as the screen shows it: the renderer displays linear values through a square root. */
    fun screenColor(): java.awt.Color = java.awt.Color(toScreen(red), toScreen(green), toScreen(blue))

    fun withScreenColor(c: java.awt.Color) = copy(red = toLinear(c.red), green = toLinear(c.green), blue = toLinear(c.blue))

    private fun toScreen(v: Float) = kotlin.math.sqrt(v.coerceIn(0f, 1f))
    private fun toLinear(v: Int) = (v / 255f).let { it * it }
}

/** Ready-made materials to start from; any of them can be changed further. */
enum class MaterialPreset(val label: String, val spec: MaterialSpec) {
    GREY("Szary mat", MaterialSpec(MaterialKind.MATTE, 0.45f, 0.45f, 0.45f)),
    WHITE("Biały mat", MaterialSpec(MaterialKind.MATTE, 0.73f, 0.73f, 0.73f)),
    RED("Czerwony mat", MaterialSpec(MaterialKind.MATTE, 0.65f, 0.08f, 0.06f)),
    BLUE("Niebieski mat", MaterialSpec(MaterialKind.MATTE, 0.08f, 0.18f, 0.6f)),
    GLASS("Szkło", MaterialSpec(MaterialKind.GLASS, 1f, 1f, 1f, 1.5)),
    DIAMOND("Diament", MaterialSpec(MaterialKind.GLASS, 1f, 1f, 1f, 2.42)),
    MIRROR("Lustro", MaterialSpec(MaterialKind.METAL, 0.9f, 0.9f, 0.9f, 0.0)),
    GOLD("Złoto", MaterialSpec(MaterialKind.METAL, 0.9f, 0.7f, 0.3f, 0.05)),
    COPPER("Miedź", MaterialSpec(MaterialKind.METAL, 0.95f, 0.64f, 0.54f, 0.15)),
    BRUSHED("Stal szczotkowana", MaterialSpec(MaterialKind.METAL, 0.7f, 0.7f, 0.72f, 0.35)),
    LAMP("Świecący biały", MaterialSpec(MaterialKind.LIGHT, 1f, 1f, 1f, 4.0));

    override fun toString() = label
}

/**
 * A model added in the viewer: the mesh as loaded, and where and how it stands. [x], [y], [z]
 * are the middle of its footprint, [height] its size after scaling, [rotation] its turn about
 * the vertical in degrees.
 */
class PlacedObject(val name: String, private val mesh: Mesh) : SceneEntry {
    @Volatile var x = 0.0
    @Volatile var y = 0.0
    @Volatile var z = 0.0
    @Volatile var rotation = 0.0
    @Volatile var height = 1.0
    @Volatile var zUp = false
    @Volatile var material = MaterialPreset.GREY.spec

    val triangleCount: Int get() = mesh.triangleCount

    /** The mesh stood up, turned, scaled to [height] and moved to its place. */
    fun placedMesh(): Mesh = shapedMesh()
        .transformed(1.0f, Vector(0f, 0f, 0f), Vector(x.toFloat(), y.toFloat(), z.toFloat()))

    /** The mesh stood up, turned and scaled, its footprint centred on the origin at y = 0. */
    private fun shapedMesh(): Mesh = (if (zUp) mesh.zUpToYUp() else mesh)
        .rotatedY(rotation.toFloat())
        .standingOnFloor(targetHeight = height.toFloat())

    @Volatile private var boundsFor: Triple<Boolean, Double, Double>? = null
    @Volatile private var bounds = FloatArray(6)

    /**
     * The model's box relative to its position (min x, y, z, max x, y, z), for drawing it while
     * it is dragged and for standing other models on it. Worked out again only when the model is
     * turned, resized or stood up differently.
     */
    fun localBounds(): FloatArray {
        val key = Triple(zUp, rotation, height)
        if (key != boundsFor) {
            val box = shapedMesh().bounds()
            bounds = floatArrayOf(box.x.min, box.y.min, box.z.min, box.x.max, box.y.max, box.z.max)
            boundsFor = key
        }
        return bounds
    }

    override fun toString() = name
}
