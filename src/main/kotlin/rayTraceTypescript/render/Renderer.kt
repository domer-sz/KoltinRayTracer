package rayTraceTypescript.render

import rayTraceTypescript.objects.Hittable
import java.nio.file.Path

/** A backend that turns a scene into a PPM file. Returns the number of primary rays traced. */
interface Renderer {
    fun render(world: Hittable, setup: CameraSetup, outputPath: Path): Long
}
