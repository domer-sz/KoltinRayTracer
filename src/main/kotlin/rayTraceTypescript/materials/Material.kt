package rayTraceTypescript.materials

import rayTraceTypescript.Ray
import rayTraceTypescript.objects.Hit

interface Material {
    fun scatter(rayIn: Ray, hit: Hit): ScatteredResult?
}