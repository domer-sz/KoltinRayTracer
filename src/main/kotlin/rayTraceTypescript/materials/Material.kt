package rayTraceTypescript.materials

import rayTraceTypescript.Color
import rayTraceTypescript.Ray
import rayTraceTypescript.objects.Hit

interface Material {
    /** How the surface scatters, or null when it absorbs the ray (or only emits). */
    fun scatter(rayIn: Ray, hit: Hit): ScatterRecord? = null

    /** Density of the surface's own scattering for a chosen direction; 0 for specular ones. */
    fun scatteringPdf(rayIn: Ray, hit: Hit, scattered: Ray): Float = 0.0f

    /** Light leaving the surface on its own; only emissive materials return anything. */
    fun emitted(rayIn: Ray, hit: Hit): Color = Color(0.0f, 0.0f, 0.0f)
}
