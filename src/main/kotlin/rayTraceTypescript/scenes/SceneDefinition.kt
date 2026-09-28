package rayTraceTypescript.scenes

import rayTraceTypescript.Camera
import rayTraceTypescript.objects.Hittable

/**
 * A named scene: what to render and the camera to render it with. [parts] name the objects and
 * lights a viewer may take out or switch off, leaving the room itself in place. [world] is then
 * a HittableList holding each part that starts enabled as a direct child.
 */
class SceneDefinition(
    val name: String,
    val world: Hittable,
    val camera: Camera,
    val parts: List<ScenePart> = emptyList()
)

/**
 * One removable object or switchable light of a scene. [lightProxy] is its stand-in in the
 * camera's light list, when the renderer aims samples at it, so taking it out stops that too.
 * A part that does not start [enabled] is in neither the world nor the light list until a
 * viewer switches it on. A [isSurface] part is one of the room's walls: a viewer keeps it in
 * place and lets its material be changed instead.
 */
class ScenePart(
    val name: String,
    val hittable: Hittable,
    val lightProxy: Hittable? = null,
    val isLight: Boolean = false,
    val enabled: Boolean = true,
    val isSurface: Boolean = false
)
