package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rayTraceTypescript.gpu.SceneFlattener
import rayTraceTypescript.scenes.BookScenes

class BookScenesTest {

    /** The model scene wants a file on disk; every other scene has to stand on its own. */
    private val selfContained = BookScenes.names.filterNot { it == "model" }

    @Test
    fun `every scene builds and flattens for the gpu`() {
        selfContained.forEach { name ->
            val scene = BookScenes.byName(name)
            val buffers = SceneFlattener.flatten(scene.world)
            assertTrue(
                buffers.sphereCount + buffers.quadCount + buffers.triangleCount + buffers.mediumCount > 0,
                "$name produced no primitives"
            )
            assertTrue(scene.camera.imageWidth > 0, "$name has no image size")
        }
    }

    @Test
    fun `the old numeric scene names still work`() {
        assertTrue(BookScenes.byName("1").name == "bouncing-spheres")
        assertTrue(BookScenes.byName("2").name == "earth")
    }

    @Test
    fun `an unknown scene lists what is available`() {
        val failure = assertThrows(IllegalArgumentException::class.java) { BookScenes.byName("nope") }
        assertTrue(failure.message!!.contains("cornell-box"), failure.message)
    }
}
