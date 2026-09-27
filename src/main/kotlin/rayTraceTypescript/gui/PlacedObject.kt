package rayTraceTypescript.gui

import rayTraceTypescript.Color
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.Mesh

/** The looks a placed model can take. */
enum class Finish(val label: String, val material: () -> Material) {
    GREY("Szary mat", { Lambertian(Color(0.45f, 0.45f, 0.45f)) }),
    WHITE("Biały mat", { Lambertian(Color(0.73f, 0.73f, 0.73f)) }),
    RED("Czerwony mat", { Lambertian(Color(0.65f, 0.08f, 0.06f)) }),
    GLASS("Szkło", { Dielectric(1.5f) }),
    MIRROR("Lustro", { Metal(Color(0.9f, 0.9f, 0.9f), 0.0f) }),
    GOLD("Złoto", { Metal(Color(0.9f, 0.7f, 0.3f), 0.05f) });

    override fun toString() = label
}

/**
 * A model added in the viewer: the mesh as loaded, and where and how it stands. [x], [y], [z]
 * are the middle of its footprint, [height] its size after scaling, [rotation] its turn about
 * the vertical in degrees.
 */
class PlacedObject(val name: String, private val mesh: Mesh) {
    @Volatile var x = 0.0
    @Volatile var y = 0.0
    @Volatile var z = 0.0
    @Volatile var rotation = 0.0
    @Volatile var height = 1.0
    @Volatile var zUp = false
    @Volatile var finish = Finish.GREY

    val triangleCount: Int get() = mesh.triangleCount

    /** The mesh stood up, turned, scaled to [height] and moved to its place. */
    fun placedMesh(): Mesh = (if (zUp) mesh.zUpToYUp() else mesh)
        .rotatedY(rotation.toFloat())
        .standingOnFloor(targetHeight = height.toFloat())
        .transformed(1.0f, Vector(0f, 0f, 0f), Vector(x.toFloat(), y.toFloat(), z.toFloat()))

    override fun toString() = name
}
