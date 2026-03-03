package rayTraceTypescript.textures

import rayTraceTypescript.Color
import rayTraceTypescript.Interval
import rayTraceTypescript.Point

class ImageTexture(private val image: RtwImage) : Texture {
    constructor(imageName: String) : this(RtwImage(imageName))

    override fun value(u: Float, v: Float, point: Point): Color {
        if (image.height() <= 0) return Color(0f,1f,1f)

        // Clamp input texture coordinates to [0,1] x [1,0]
//        u = interval(0,1).clamp(u);
//        v = 1.0 - interval(0,1).clamp(v);  // Flip V to image coordinates
//
//        auto i = int(u * image.width());
//        auto j = int(v * image.height());
//        auto pixel = image.pixel_data(i,j);
//
//        auto color_scale = 1.0 / 255.0;
//        return color(color_scale*pixel[0], color_scale*pixel[1], color_scale*pixel[2]);

        val uu = Interval(0f, 1f).clamp(u)
        val vv = 1.0f - Interval(0f, 1f).clamp(v)

        val i = (uu * image.width()).toInt()
        val j = (vv * image.height()).toInt()
        val pixel = image.pixelData(i, j)
        val colorScale = 1.0f / 255.0f
        return Color(colorScale*pixel[0], colorScale*pixel[1], colorScale*pixel[2])
    }
}