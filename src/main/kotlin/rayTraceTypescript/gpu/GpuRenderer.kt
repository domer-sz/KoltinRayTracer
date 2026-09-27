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
        val pixels = FloatArray(setup.totalPixels * 3)
        val flattenMs = (System.nanoTime() - flattenStart) / 1_000_000

        // Holds the card at full clocks for the whole render (see GpuKeepAwake).
        GpuKeepAwake.startIfUseful().use {
            OpenClContext.create().use { cl ->
                val buildStart = System.nanoTime()
                // -Drt.cl.options adds compiler flags for experiments, e.g. -cl-nv-verbose.
                val extra = System.getProperty("rt.cl.options") ?: ""
                // -Drt.aov=albedo|normal renders a denoiser guide instead of the image.
                val aov = when (System.getProperty("rt.aov")) { "albedo" -> "-DAOV_MODE=1"; "normal" -> "-DAOV_MODE=2"; else -> "" }
                val options = listOf(BUILD_OPTIONS, scene.kernelDefines(), aov, registerCap(cl, scene, extra), extra)
                    .joinToString(" ").trim()
                val program = cl.buildProgram(kernelSource(), options)
                val buildMs = (System.nanoTime() - buildStart) / 1_000_000
                val kernel = MemoryStack.stackPush().use { stack ->
                    val errcode = stack.mallocInt(1)
                    val handle = clCreateKernel(program, "render", errcode)
                    OpenClContext.check(errcode, "clCreateKernel")
                    handle
                }
    
                val buffers = mutableListOf<Long>()
                try {
                    fun readOnly(data: FloatArray) = create(cl.context, pad(data)).also { buffers += it }
                    fun readOnly(data: IntArray) = create(cl.context, pad(data)).also { buffers += it }
    
                    val cameraBuffer = readOnly(cameraParameters(setup))
                    val nodes = readOnly(scene.nodes)
                    val leaves = readOnly(scene.leaves)
                    val refs = readOnly(scene.refs)
                    val spheres = readOnly(scene.spheres)
                    val sphereMaterials = readOnly(scene.sphereMaterials)
                    val triangles = readOnly(scene.triangles)
                    val triangleMaterials = readOnly(scene.triangleMaterials)
                    val quads = readOnly(scene.quads)
                    val quadMaterials = readOnly(scene.quadMaterials)
                    val mediumInts = readOnly(scene.mediumInts)
                    val mediumFloats = readOnly(scene.mediumFloats)
                    val lights = readOnly(scene.lights)
                    val materialInts = readOnly(scene.materialInts)
                    val materialFloats = readOnly(scene.materialFloats)
                    val textureInts = readOnly(scene.textureInts)
                    val textureFloats = readOnly(scene.textureFloats)
                    val textureImages = createBytes(cl.context, scene.textureImages).also { buffers += it }
                    val perlinVectors = readOnly(scene.perlinVectors)
                    val perlinPermutations = readOnly(scene.perlinPermutations)
    
                    val output = MemoryStack.stackPush().use { stack ->
                        val errcode = stack.mallocInt(1)
                        val handle = clCreateBuffer(
                            cl.context,
                            // Read as well as written: every slice of samples after the first adds to it.
                            CL_MEM_READ_WRITE.toLong(),
                            pixels.size.toLong() * Float.SIZE_BYTES,
                            errcode
                        )
                        OpenClContext.check(errcode, "clCreateBuffer(output)")
                        handle
                    }
                    buffers += output
    
                    var arg = 0
                    for (buffer in listOf(
                        cameraBuffer, nodes, leaves, refs, spheres, sphereMaterials,
                        triangles, triangleMaterials, quads, quadMaterials, mediumInts, mediumFloats, lights,
                        materialInts, materialFloats, textureInts, textureFloats, textureImages,
                        perlinVectors, perlinPermutations, output
                    )) {
                        OpenClContext.check(clSetKernelArg1p(kernel, arg++, buffer), "clSetKernelArg($arg)")
                    }
                    clSetKernelArg1i(kernel, arg++, setup.imageWidth)
                    clSetKernelArg1i(kernel, arg++, setup.imageHeight)
                    val sampleStartArg = arg++
                    val sampleCountArg = arg++
                    clSetKernelArg1i(kernel, arg++, setup.maxReflectionDepth)
                    clSetKernelArg1f(kernel, arg++, setup.pixelSamplesScale)
                    clSetKernelArg1i(kernel, arg++, scene.rootNode)
                    clSetKernelArg1i(kernel, arg++, scene.lightCount)
                    clSetKernelArg1l(kernel, arg, seed())
    
                    val progress = ProgressBar(PROGRESS_STEPS)
                    val kernelStart = System.nanoTime()
                    var launches = 0
                    MemoryStack.stackPush().use { stack ->
                        // One work item per pixel, a warp's 32 covering an 8x4 tile: neighbouring
                        // rays stay together on their first bounces, where they diverge least.
                        val tiles = ((setup.imageWidth + 7) / 8).toLong() * ((setup.imageHeight + 3) / 4)
                        val localSize = (System.getProperty("rt.cl.local")?.toLongOrNull() ?: LOCAL_SIZE)
                        val globalWorkSize = stack.mallocPointer(1)
                            .put(0, (tiles * 32 + localSize - 1) / localSize * localSize)
                        val localWorkSize = stack.mallocPointer(1).put(0, localSize)
    
                        // Every launch covers the whole image and a slice of the samples, sized to
                        // take about TARGET_LAUNCH_MS: plenty of threads to keep every SM full, and
                        // short enough for the display driver's watchdog. The random streams are
                        // keyed by pixel and sample, so the slicing does not change the image.
                        var done = 0
                        var slice = 1
                        while (done < setup.samplesPerPixel) {
                            val count = minOf(slice, setup.samplesPerPixel - done)
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
                            progress.report((done.toLong() * PROGRESS_STEPS / setup.samplesPerPixel).toInt())
                        }
                    }
                    val kernelMs = (System.nanoTime() - kernelStart) / 1_000_000
                    // Kept apart so the kernel's own speed is not buried under scene preparation.
                    System.err.println(
                        "\n[gpu] flatten $flattenMs ms, build $buildMs ms, kernel $kernelMs ms in $launches launches " +
                            "(${scene.nodeCount} nodes, depth ${scene.maxDepth})"
                    )
    
                    OpenClContext.check(
                        clEnqueueReadBuffer(cl.queue, output, true, 0, pixels, null, null),
                        "clEnqueueReadBuffer"
                    )
                    PpmWriter.write(outputPath, setup.imageWidth, setup.imageHeight, pixels)
                    progress.finish()
                } finally {
                    buffers.forEach { clReleaseMemObject(it) }
                    clReleaseKernel(kernel)
                    clReleaseProgram(program)
                }
            }
        }

        return setup.totalPixels.toLong() * setup.samplesPerPixel
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
