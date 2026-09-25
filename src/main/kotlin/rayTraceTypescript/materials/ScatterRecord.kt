package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.pdf.Pdf

/**
 * What a material does with an incoming ray. Diffuse surfaces hand back a density to sample
 * from ([pdf]); mirrors and glass hand back the one ray they reflect or refract into
 * ([skipPdfRay]), because a specular direction is not something to importance sample.
 */
class ScatterRecord(
    val attenuation: Color,
    val pdf: Pdf? = null,
    val skipPdfRay: Ray? = null
)
