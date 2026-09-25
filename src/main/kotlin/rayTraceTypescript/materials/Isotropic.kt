package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.pdf.SpherePdf
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture
import rayTraceTypescript.utils.pi

/** The phase function inside a volume: a scattered ray leaves in any direction at all. */
class Isotropic(val texture: Texture) : Material {
    constructor(color: Color) : this(SolidColorTexture(color))

    override fun scatter(rayIn: Ray, hit: Hit): ScatterRecord =
        ScatterRecord(texture.value(hit.u, hit.v, hit.point), SpherePdf())

    override fun scatteringPdf(rayIn: Ray, hit: Hit, scattered: Ray): Float = 1.0f / (4.0f * pi)
}
