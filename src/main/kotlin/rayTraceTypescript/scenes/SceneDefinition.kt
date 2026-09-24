package rayTraceTypescript.scenes

import rayTraceTypescript.Camera
import rayTraceTypescript.objects.Hittable

/** A named scene: what to render and the camera to render it with. */
class SceneDefinition(
    val name: String,
    val world: Hittable,
    val camera: Camera
)
