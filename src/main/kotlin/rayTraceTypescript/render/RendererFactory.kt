package rayTraceTypescript.render

import rayTraceTypescript.gpu.GpuRenderer
import rayTraceTypescript.objects.Hittable
import java.nio.file.Path

/**
 * Picks the rendering backend. `-Drt.renderer=gpu|cpu` forces one; the default `auto` renders
 * on the GPU and falls back to the CPU when no OpenCL device, driver or kernel is usable.
 */
object RendererFactory {
    fun create(): Renderer = when (System.getProperty("rt.renderer", "auto").lowercase()) {
        "cpu" -> CpuRenderer()
        "gpu" -> GpuRenderer()
        else -> AutoRenderer()
    }

    private class AutoRenderer : Renderer {
        override fun render(world: Hittable, setup: CameraSetup, outputPath: Path): Long = try {
            GpuRenderer().render(world, setup, outputPath)
        } catch (t: Throwable) {
            System.err.println("GPU rendering unavailable (${t.message}); falling back to the CPU renderer.")
            CpuRenderer().render(world, setup, outputPath)
        }
    }
}
