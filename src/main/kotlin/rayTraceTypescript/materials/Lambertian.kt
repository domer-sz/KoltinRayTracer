package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.Vector
import rayTraceTypescript.objects.Hit
import rayTraceTypescript.pdf.CosinePdf
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture
import rayTraceTypescript.utils.pi

class Lambertian(val texture: Texture) : Material {
    constructor(color: Color) : this(SolidColorTexture(color))

    override fun scatter(rayIn: Ray, hit: Hit): ScatterRecord =
        ScatterRecord(texture.value(hit.u, hit.v, hit.point), CosinePdf(hit.normal))

    override fun scatteringPdf(rayIn: Ray, hit: Hit, scattered: Ray): Float {
        val cosine = Vector.dotProduct(hit.normal, Vector.unit(scattered.direction))
        return if (cosine < 0.0f) 0.0f else cosine / pi
    }
}
