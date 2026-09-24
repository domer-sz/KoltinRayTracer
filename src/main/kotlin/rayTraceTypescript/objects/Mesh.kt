package rayTraceTypescript.objects

import rayTraceTypescript.Aabb
import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Material

/**
 * A triangle soup, stored as flat vertex data (9 floats per facet: three vertices, each x/y/z).
 * Every supported 3D file format is parsed into this one shape by [MeshLoader].
 *
 * Mesh files carry no shared convention for units, origin or orientation, so a freshly loaded
 * model can sit anywhere and be any size. [standingOnFloor] is the default placement: it scales
 * the model to a workable height and puts it centred on the floor, where the scenes in this
 * project put their ground.
 */
class Mesh(val vertices: FloatArray) {

    init {
        require(vertices.isNotEmpty()) { "Mesh has no triangles" }
        require(vertices.size % VERTEX_FLOATS == 0) { "Expected $VERTEX_FLOATS floats per triangle" }
    }

    val triangleCount: Int get() = vertices.size / VERTEX_FLOATS

    fun bounds(): Aabb {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < vertices.size) {
            val x = vertices[i]; val y = vertices[i + 1]; val z = vertices[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        return Aabb.fromPoints(Point(minX, minY, minZ), Point(maxX, maxY, maxZ))
    }

    /**
     * Centres the model over the origin in x/z, rests its lowest point on [floorY] and - unless
     * [targetHeight] is null - scales it uniformly to that height, so models authored in
     * millimetres and models authored in metres both arrive at a sensible size.
     */
    fun standingOnFloor(targetHeight: Float? = DEFAULT_TARGET_HEIGHT, floorY: Float = 0.0f): Mesh {
        val box = bounds()
        val height = box.y.size()
        val scale = if (targetHeight == null || height <= 0.0f) 1.0f else targetHeight / height
        val pivot = Vector((box.x.min + box.x.max) / 2.0f, box.y.min, (box.z.min + box.z.max) / 2.0f)
        return transformed(scale, pivot, Vector(0.0f, floorY, 0.0f))
    }

    /** Moves every vertex to `(vertex - pivot) * scale + target`. */
    fun transformed(scale: Float, pivot: Vector, target: Vector): Mesh {
        val moved = FloatArray(vertices.size)
        var i = 0
        while (i < vertices.size) {
            moved[i] = (vertices[i] - pivot.x) * scale + target.x
            moved[i + 1] = (vertices[i + 1] - pivot.y) * scale + target.y
            moved[i + 2] = (vertices[i + 2] - pivot.z) * scale + target.z
            i += 3
        }
        return Mesh(moved)
    }

    fun toHittables(material: Material): List<Hittable> = (0 until triangleCount).map { index ->
        val at = index * VERTEX_FLOATS
        Triangle(
            Point(vertices[at], vertices[at + 1], vertices[at + 2]),
            Point(vertices[at + 3], vertices[at + 4], vertices[at + 5]),
            Point(vertices[at + 6], vertices[at + 7], vertices[at + 8]),
            material
        )
    }

    companion object {
        /** Height a loaded model is scaled to, in scene units - roughly the big spheres' size. */
        const val DEFAULT_TARGET_HEIGHT = 2.0f

        /** Three vertices of x/y/z per triangle. */
        const val VERTEX_FLOATS = 9
    }
}
