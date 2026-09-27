package rayTraceTypescript.objects

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.textures.UvCheckerTexture
import kotlin.math.max
import kotlin.math.min

/** An axis-aligned box as six quads, the way The Next Week builds the Cornell box's blocks. */
fun box(a: Point, b: Point, material: Material): HittableList {
    val minimum = Point(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z))
    val maximum = Point(max(a.x, b.x), max(a.y, b.y), max(a.z, b.z))

    val dx = Vector(maximum.x - minimum.x, 0.0f, 0.0f)
    val dy = Vector(0.0f, maximum.y - minimum.y, 0.0f)
    val dz = Vector(0.0f, 0.0f, maximum.z - minimum.z)

    return HittableList(
        mutableListOf(
            Quad(Point(minimum.x, minimum.y, maximum.z), dx, dy, material),          // front
            Quad(Point(maximum.x, minimum.y, maximum.z), -dz, dy, material),         // right
            Quad(Point(maximum.x, minimum.y, minimum.z), -dx, dy, material),         // back
            Quad(Point(minimum.x, minimum.y, minimum.z), dz, dy, material),          // left
            Quad(Point(minimum.x, maximum.y, maximum.z), dx, -dz, material),         // top
            Quad(Point(minimum.x, minimum.y, minimum.z), dx, dz, material)           // bottom
        )
    )
}

/**
 * [box] covered in a checkerboard of square cells [cell] units wide. Each face counts its own
 * cells from its size (in its u/v coordinates), so the squares stay square on every face and
 * follow the box however it is later turned or moved.
 */
fun checkeredBox(a: Point, b: Point, cell: Float, even: Color, odd: Color): HittableList =
    HittableList(
        box(a, b, Lambertian(even)).objects.map { face ->
            face as Quad
            val texture = UvCheckerTexture(face.u.length() / cell, face.v.length() / cell, even, odd)
            Quad(face.q, face.u, face.v, Lambertian(texture))
        }.toMutableList()
    )
