package rayTraceTypescript.gpu

import org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR
import org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY
import org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE
import org.lwjgl.opencl.CL10.clCreateBuffer
import org.lwjgl.opencl.CL10.clCreateKernel
import org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel
import org.lwjgl.opencl.CL10.clEnqueueReadBuffer
import org.lwjgl.opencl.CL10.clFinish
import org.lwjgl.opencl.CL10.clReleaseKernel
import org.lwjgl.opencl.CL10.clReleaseMemObject
import org.lwjgl.opencl.CL10.clReleaseProgram
import org.lwjgl.opencl.CL10.clSetKernelArg1f
import org.lwjgl.opencl.CL10.clSetKernelArg1i
import org.lwjgl.opencl.CL10.clSetKernelArg1l
import org.lwjgl.opencl.CL10.clSetKernelArg1p
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.render.CameraSetup
import rayTraceTypescript.render.Denoiser
import rayTraceTypescript.render.PpmWriter
import rayTraceTypescript.render.ProgressBar
import rayTraceTypescript.render.Renderer
import rayTraceTypescript.utils.RandomSource
import java.nio.file.Path
import kotlin.random.Random

/**
 * Runs the whole path tracer on an OpenCL device: one work item per pixel, all samples,
 * bounces, BVH traversal and shading happen in the kernel. The CPU only flattens the scene,
 * hands over the camera basis and writes the PPM.
 */
class GpuRenderer : Renderer {

    override fun render(world: Hittable, setup: CameraSetup, outputPath: Path): Long {
        val flattenStart = System.nanoTime()
        val scene = SceneFlattener.flatten(world, setup.lights)
        val flattenMs = (System.nanoTime() - flattenStart) / 1_000_000
        System.err.println("[gpu] flatten $flattenMs ms (${scene.nodeCount} nodes, depth ${scene.maxDepth})")
        val denoiser = requestedDenoiser()

        // Holds the card at full clocks for the whole render (see GpuKeepAwake).
        GpuKeepAwake.startIfUseful().use {
            OpenClContext.create().use { cl ->
                val buffers = mutableListOf<Long>()
                try {
                    fun readOnly(data: FloatArray) = create(cl.context, pad(data)).also { buffers += it }
                    fun readOnly(data: IntArray) = create(cl.context, pad(data)).also { buffers += it }

                    val sceneBuffers = listOf(
                        readOnly(cameraParameters(setup)),
                        readOnly(scene.nodes), readOnly(scene.leaves), readOnly(scene.refs),
                        readOnly(scene.spheres), readOnly(scene.sphereMaterials),
                        readOnly(scene.triangles), readOnly(scene.triangleMaterials),
                        readOnly(scene.quads), readOnly(scene.quadMaterials),
                        readOnly(scene.mediumInts), readOnly(scene.mediumFloats), readOnly(scene.lights),
                        readOnly(scene.materialInts), readOnly(scene.materialFloats),
                        readOnly(scene.textureInts), readOnly(scene.textureFloats),
                        createBytes(cl.context, scene.textureImages).also { buffers += it },
                        readOnly(scene.perlinVectors), readOnly(scene.perlinPermutations)
                    )
                    val output = MemoryStack.stackPush().use { stack ->
                        val errcode = stack.mallocInt(1)
                        val handle = clCreateBuffer(
                            cl.context,
                            // Read as well as written: every slice of samples after the first adds to it.
                            CL_MEM_READ_WRITE.toLong(),
                            setup.totalPixels * 3L * Float.SIZE_BYTES,
                            errcode
                        )
                        OpenClContext.check(errcode, "clCreateBuffer(output)")
                        handle
                    }
                    buffers += output

                    val pass = Pass(cl, scene, setup, sceneBuffers + output, output)
                    // -Drt.aov=albedo|normal renders a denoiser guide instead of the image.
                    val mainMode = when (System.getProperty("rt.aov")) {
                        "albedo" -> AOV_ALBEDO
                        "normal" -> AOV_NORMAL
                        else -> AOV_IMAGE
                    }
                    val pixels = pass.trace(mainMode, setup.samplesPerPixel)
                    PpmWriter.write(outputPath, setup.imageWidth, setup.imageHeight, pixels)

                    if (denoiser != null && mainMode == AOV_IMAGE) {
                        denoiser.use {
                            // The guides only see the first hit, so a few hundred samples leave them
                            // clean; the denoiser needs them antialiased like the image, not more.
                            val guideSamples = minOf(setup.samplesPerPixel, GUIDE_SAMPLES)
                            val albedo = pass.trace(AOV_ALBEDO, guideSamples)
                            val normal = pass.trace(AOV_NORMAL, guideSamples)
                            val start = System.nanoTime()
                            val clean = it.denoise(setup.imageWidth, setup.imageHeight, pixels, albedo, normal)
                            System.err.println("[gpu] denoised in ${(System.nanoTime() - start) / 1_000_000} ms")
                            PpmWriter.write(denoisedPath(outputPath), setup.imageWidth, setup.imageHeight, clean)
                        }
                    }
                } finally {
                    buffers.forEach { clReleaseMemObject(it) }
                }
            }
        }

        return setup.totalPixels.toLong() * setup.samplesPerPixel
    }

    /** One run of the kernel over the whole image: the image itself, or one denoiser guide. */
    private inner class Pass(
        private val cl: OpenClContext,
        private val scene: SceneBuffers,
        private val setup: CameraSetup,
        private val arguments: List<Long>,
        private val output: Long
    ) {
        fun trace(mode: Int, samples: Int): FloatArray {
            val buildStart = System.nanoTime()
            // -Drt.cl.options adds compiler flags for experiments, e.g. -cl-nv-verbose.
            val extra = System.getProperty("rt.cl.options") ?: ""
            val options = listOf(BUILD_OPTIONS, scene.kernelDefines(), "-DAOV_MODE=$mode", registerCap(cl, scene, extra), extra)
                .joinToString(" ").trim()
            val program = cl.buildProgram(kernelSource(), options)
            val buildMs = (System.nanoTime() - buildStart) / 1_000_000
            val kernel = MemoryStack.stackPush().use { stack ->
                val errcode = stack.mallocInt(1)
                val handle = clCreateKernel(program, "render", errcode)
                OpenClContext.check(errcode, "clCreateKernel")
                handle
            }
            try {
                var arg = 0
                for (buffer in arguments) {
                    OpenClContext.check(clSetKernelArg1p(kernel, arg++, buffer), "clSetKernelArg($arg)")
                }
                clSetKernelArg1i(kernel, arg++, setup.imageWidth)
                clSetKernelArg1i(kernel, arg++, setup.imageHeight)
                val sampleStartArg = arg++
                val sampleCountArg = arg++
                clSetKernelArg1i(kernel, arg++, setup.maxReflectionDepth)
                clSetKernelArg1f(kernel, arg++, 1.0f / samples)
                clSetKernelArg1i(kernel, arg++, scene.rootNode)
                clSetKernelArg1i(kernel, arg++, scene.lightCount)
                clSetKernelArg1l(kernel, arg, seed())

                val progress = ProgressBar(PROGRESS_STEPS)
                val kernelStart = System.nanoTime()
                var launches = 0
                MemoryStack.stackPush().use { stack ->
                    // One work item per pixel, a warp of 32 covering an 8x4 tile: neighbouring
                    // rays stay together on their first bounces, where they diverge least.
                    val tiles = ((setup.imageWidth + 7) / 8).toLong() * ((setup.imageHeight + 3) / 4)
                    val localSize = (System.getProperty("rt.cl.local")?.toLongOrNull() ?: LOCAL_SIZE)
                    val globalWorkSize = stack.mallocPointer(1)
                        .put(0, (tiles * 32 + localSize - 1) / localSize * localSize)
                    val localWorkSize = stack.mallocPointer(1).put(0, localSize)

                    // Every launch covers the whole image and a slice of the samples, sized to
                    // take about TARGET_LAUNCH_MS: plenty of threads to keep every SM full, and
                    // short enough for the display driver watchdog. The random streams are
                    // keyed by pixel and sample, so the slicing does not change the image.
                    var done = 0
                    var slice = 1
                    while (done < samples) {
                        val count = minOf(slice, samples - done)
                        clSetKernelArg1i(kernel, sampleStartArg, done)
                        clSetKernelArg1i(kernel, sampleCountArg, count)
                        val launchStart = System.nanoTime()
                        OpenClContext.check(
                            clEnqueueNDRangeKernel(cl.queue, kernel, 1, null, globalWorkSize, localWorkSize, null, null),
                            "clEnqueueNDRangeKernel"
                        )
                        OpenClContext.check(clFinish(cl.queue), "clFinish")
                        val launchMs = maxOf((System.nanoTime() - launchStart) / 1_000_000.0, 1.0)
                        done += count
                        launches++
                        slice = (count * TARGET_LAUNCH_MS / launchMs).toInt().coerceIn(1, count * 4)
                        progress.report((done.toLong() * PROGRESS_STEPS / samples).toInt())
                    }
                }
                progress.finish()
                val kernelMs = (System.nanoTime() - kernelStart) / 1_000_000
                // Kept apart so the kernel's own speed is not buried under scene preparation.
                System.err.println("\n[gpu] ${MODE_NAMES[mode]}: build $buildMs ms, kernel $kernelMs ms in $launches launches")

                val pixels = FloatArray(setup.totalPixels * 3)
                OpenClContext.check(clEnqueueReadBuffer(cl.queue, output, true, 0, pixels, null, null), "clEnqueueReadBuffer")
                return pixels
            } finally {
                clReleaseKernel(kernel)
                clReleaseProgram(program)
            }
        }
    }

    /**
     * The denoiser, when -Drt.denoise asks for one (any value but "off") and its DLLs are where
     * -Drt.oidn or OIDN_DIR points. Without them the render still runs, just without it.
     */
    private fun requestedDenoiser(): Denoiser? {
        val wanted = System.getProperty("rt.denoise")?.let { it != "off" } ?: false
        if (!wanted) return null
        val folder = Denoiser.folder()
        if (folder == null) {
            System.err.println("[gpu] -Drt.denoise: no OpenImageDenoise.dll in -Drt.oidn / OIDN_DIR, rendering without denoising")
            return null
        }
        return try {
            Denoiser.open(folder)
        } catch (t: Throwable) {
            System.err.println("[gpu] could not load Open Image Denoise from $folder: ${t.message}")
            null
        }
    }

    /** image.ppm -> image-denoised.ppm, beside it. */
    private fun denoisedPath(outputPath: Path): Path {
        val name = outputPath.fileName.toString()
        val stem = if (name.lowercase().endsWith(".ppm")) name.dropLast(4) else name
        return outputPath.resolveSibling("$stem-denoised.ppm")
    }

    /**
     * Left alone, NVIDIA's compiler gives the kernel ~100-128 registers, which fits only a
     * quarter of the threads a Pascal SM can hold. Capping registers costs some spilling but
     * hides memory latency far better: measured on a GTX 1070, 64 is best across the scenes,
     * except that volumes (a nested traversal) want 72. Only NVIDIA understands the flag.
     */
    private fun registerCap(cl: OpenClContext, scene: SceneBuffers, extra: String): String {
        if (!cl.extensions.contains("cl_nv_compiler_options") || extra.contains("maxrregcount")) return ""
        return "-cl-nv-maxrregcount=${if (scene.mediumCount > 0) 72 else 64}"
    }

    /** Same seed as the CPU path when the test harness pinned one, random otherwise. */
    private fun seed(): Long = RandomSource.seedValue() ?: Random.Default.nextLong()

    private fun cameraParameters(setup: CameraSetup): FloatArray = floatArrayOf(
        setup.cameraCenter.x, setup.cameraCenter.y, setup.cameraCenter.z,
        setup.pixel00.x, setup.pixel00.y, setup.pixel00.z,
        setup.pixelDeltaU.x, setup.pixelDeltaU.y, setup.pixelDeltaU.z,
        setup.pixelDeltaV.x, setup.pixelDeltaV.y, setup.pixelDeltaV.z,
        setup.defocusDiscU.x, setup.defocusDiscU.y, setup.defocusDiscU.z,
        setup.defocusDiscV.x, setup.defocusDiscV.y, setup.defocusDiscV.z,
        setup.defocusAngle,
        // A scene without a background colour keeps the sky gradient.
        if (setup.background == null) 0.0f else 1.0f,
        setup.background?.r ?: 0.0f,
        setup.background?.g ?: 0.0f,
        setup.background?.b ?: 0.0f
    )

    companion object {
        private const val TARGET_LAUNCH_MS = 200.0
        private const val LOCAL_SIZE = 64L
        private const val PROGRESS_STEPS = 1000

        private const val AOV_IMAGE = 0
        private const val AOV_ALBEDO = 1
        private const val AOV_NORMAL = 2
        private val MODE_NAMES = listOf("image", "albedo guide", "normal guide")
        /** Samples for the denoiser guides; they only see the first hit, so few are enough. */
        private const val GUIDE_SAMPLES = 256
        // Unsafe math (fused multiply-adds, faster division and square roots) leaves the image
        // the same to within 0.1 of 255 per pixel at 2000 spp, and speeds the kernel up by a
        // quarter. Never -cl-fast-relaxed-math or -cl-finite-math-only: the traversal relies on
        // INFINITY for open intervals and on NaN-tolerant fmin/fmax in the slab test.
        private const val BUILD_OPTIONS = "-cl-std=CL1.2 -cl-unsafe-math-optimizations"
        private const val KERNEL_RESOURCE = "/kernels/raytracer.cl"

        fun unavailabilityReason(): String? = OpenClContext.unavailabilityReason()

        fun kernelSource(): String = GpuRenderer::class.java.getResourceAsStream(KERNEL_RESOURCE)
            ?.bufferedReader()?.use { it.readText() }
            ?: throw OpenClException("kernel resource $KERNEL_RESOURCE is missing from the jar")

        // OpenCL rejects zero-sized buffers, so empty sections get one padding element.
        private fun pad(data: FloatArray) = if (data.isEmpty()) FloatArray(1) else data
        private fun pad(data: IntArray) = if (data.isEmpty()) IntArray(1) else data

        private const val READ_FLAGS = (CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR).toLong()

        private fun create(context: Long, data: FloatArray): Long {
            val errcode = IntArray(1)
            val buffer = clCreateBuffer(context, READ_FLAGS, data, errcode)
            OpenClContext.check(errcode, "clCreateBuffer")
            return buffer
        }

        private fun create(context: Long, data: IntArray): Long {
            val errcode = IntArray(1)
            val buffer = clCreateBuffer(context, READ_FLAGS, data, errcode)
            OpenClContext.check(errcode, "clCreateBuffer")
            return buffer
        }

        private fun createBytes(context: Long, data: ByteArray): Long {
            val native = MemoryUtil.memAlloc(maxOf(data.size, 1))
            try {
                if (data.isNotEmpty()) {
                    native.put(data)
                    native.flip()
                }
                val errcode = IntArray(1)
                val buffer = clCreateBuffer(context, READ_FLAGS, native, errcode)
                OpenClContext.check(errcode, "clCreateBuffer(images)")
                return buffer
            } finally {
                MemoryUtil.memFree(native)
            }
        }
    }
}
