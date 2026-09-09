package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Point

class SolidColorTexture(internal val albedo: Color): Texture {
    constructor(red: Float, green: Float, blue: Float) : this(Color(red, green, blue))

    override fun value(u: Float, v: Float, point: Point): Color {
       return albedo
    }
}