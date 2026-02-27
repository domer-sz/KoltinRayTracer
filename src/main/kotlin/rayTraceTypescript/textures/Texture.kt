package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Point

interface Texture {
    fun value(u: Float, v: Float, point: Point): Color
}