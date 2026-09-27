package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import kotlin.math.floor

/**
 * A checkerboard laid out in a surface's own u/v coordinates: [cellsU] by [cellsV] cells across
 * it. Unlike [CheckerTexture], which cuts space into cubes, it stays square on a rotated face;
 * give each face the counts that match its size (see checkeredBox).
 */
class UvCheckerTexture(
    internal val cellsU: Float,
    internal val cellsV: Float,
    internal val even: Texture,
    internal val odd: Texture
) : Texture {

    constructor(cellsU: Float, cellsV: Float, even: Color, odd: Color) :
        this(cellsU, cellsV, SolidColorTexture(even), SolidColorTexture(odd))

    override fun value(u: Float, v: Float, point: Point): Color {
        val isEven = (cell(u, cellsU) + cell(v, cellsV)) % 2 == 0
        return if (isEven) even.value(u, v, point) else odd.value(u, v, point)
    }

    companion object {
        /** The cell a coordinate falls in; the far edge (exactly 1) belongs to the last cell. */
        internal fun cell(coordinate: Float, cells: Float): Int =
            minOf(floor(coordinate * cells).toInt(), cells.toInt() - 1).coerceAtLeast(0)
    }
}
