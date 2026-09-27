package rayTraceTypescript.gpu

import org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR
import org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY
import org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE
import org.lwjgl.opencl.CL10.clCreateBuffer
import org.lwjgl.opencl.CL10.clCreateKernel
import org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel
import org.lwjgl.opencl.CL10.clEnqueueReadBuffer
import org.lwjgl.opencl.CL10.clEnqueueWriteBuffer
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
import rayTraceTypescript.render.CameraSetup
import rayTraceTypescript.utils.RandomSource
import kotlin.random.Random

/**
 * A scene living on the GPU: its buffers uploaded once, one compiled kernel per mode (the image
 * and the two denoiser guides), and two output buffers - the image, and a scratch one the guides
 * render into so they never disturb samples gathered so far.
 *
 * Everything that changes between launches - the camera, the resolution, which samples - is a
 * launch argument, so the same session serves a file render and an interactive viewer. Launches
 * block until the GPU is done; a session belongs to one thread.
 */
class GpuSession(private val scene: SceneBuffers, maxPixels: Int) : AutoCloseable {
    private val keepAwake = GpuKeepAwake.startIfUseful()
    private val cl = OpenClContext.create()
    private val buffers = mutableListOf<Long>()
    private val programs = HashMap<Int, Pair<Long, Long>>()
    private val seed = RandomSource.seedValue() ?: Random.Default.nextLong()

    private val cameraBuffer: Long
    private val sceneArguments: List<Long>
    private val outputs: List<Long>

    init {
        try {
            cameraBuffer = create(FloatArray(CAMERA_FLOATS))
            sceneArguments = listOf(
                cameraBuffer,
                create(scene.nodes), create(scene.leaves), create(scene.refs),
                create(scene.spheres), create(scene.sphereMaterials),
                create(scene.triangles), create(scene.triangleMaterials),
                create(scene.quads), create(scene.quadMaterials),
                create(scene.mediumInts), create(scene.mediumFloats), create(scene.lights),
                create(scene.materialInts), create(scene.materialFloats),
                create(scene.textureInts), create(scene.textureFloats),
                createBytes(scene.textureImages),
                create(scene.perlinVectors), create(scene.perlinPermutations)
            )
            // Read as well as written: every slice of samples after the first adds to it.
            outputs = List(2) { createOutput(maxPixels * 3L * Float.SIZE_BYTES) }
        } catch (t: Throwable) {
            close()
            throw t
        }
    }

    /** Points the camera buffer at [setup]; the next launch shoots rays from there. */
    fun setCamera(setup: CameraSetup) {
        val parameters = cameraParameters(setup)
        OpenClContext.check(
            clEnqueueWriteBuffer(cl.queue, cameraBuffer, true, 0, parameters, null, null),
            "clEnqueueWriteBuffer(camera)"
        )
    }

    /**
     * Traces samples [sampleStart] until [sampleStart] + [count] of every pixel of [setup]'s
     * image, in [mode], into [target], scaled by [scale]. A launch whose [sampleStart] is above
     * zero adds to what the target holds. Returns when the GPU is done.
     */
    fun launch(mode: Int, setup: CameraSetup, sampleStart: Int, count: Int, scale: Float, target: Int = IMAGE) {
        val kernel = kernel(mode)
        var arg = sceneArguments.size
        clSetKernelArg1p(kernel, arg++, outputs[target])
        clSetKernelArg1i(kernel, arg++, setup.imageWidth)
        clSetKernelArg1i(kernel, arg++, setup.imageHeight)
        clSetKernelArg1i(kernel, arg++, sampleStart)
        clSetKernelArg1i(kernel, arg++, count)
        clSetKernelArg1i(kernel, arg++, setup.maxReflectionDepth)
        clSetKernelArg1f(kernel, arg++, scale)
        clSetKernelArg1i(kernel, arg++, scene.rootNode)
        clSetKernelArg1i(kernel, arg++, scene.lightCount)
        clSetKernelArg1l(kernel, arg, seed)

        MemoryStack.stackPush().use { stack ->
            // One work item per pixel, a warp of 32 covering an 8x4 tile: neighbouring rays
            // stay together on their first bounces, where they diverge least.
            val tiles = ((setup.imageWidth + 7) / 8).toLong() * ((setup.imageHeight + 3) / 4)
            val localSize = System.getProperty("rt.cl.local")?.toLongOrNull() ?: LOCAL_SIZE
            val global = stack.mallocPointer(1).put(0, (tiles * 32 + localSize - 1) / localSize * localSize)
            val local = stack.mallocPointer(1).put(0, localSize)
            OpenClContext.check(
                clEnqueueNDRangeKernel(cl.queue, kernel, 1, null, global, local, null, null),
                "clEnqueueNDRangeKernel"
            )
            OpenClContext.check(clFinish(cl.queue), "clFinish")
        }
    }

    /** Copies the first [into].size floats of [target] back to the host. */
    fun read(target: Int, into: FloatArray) {
        OpenClContext.check(clEnqueueReadBuffer(cl.queue, outputs[target], true, 0, into, null, null), "clEnqueueReadBuffer")
    }

    /** The compiled kernel for [mode], built on first use (the driver caches it across runs too). */
    private fun kernel(mode: Int): Long = programs.getOrPut(mode) {
        val start = System.nanoTime()
        // -Drt.cl.options adds compiler flags for experiments, e.g. -cl-nv-verbose.
        val extra = System.getProperty("rt.cl.options") ?: ""
        val options = listOf(BUILD_OPTIONS, scene.kernelDefines(), "-DAOV_MODE=$mode", registerCap(extra), extra)
            .joinToString(" ").trim()
        val program = cl.buildProgram(GpuRenderer.kernelSource(), options)
        val kernel = MemoryStack.stackPush().use { stack ->
            val errcode = stack.mallocInt(1)
            val handle = clCreateKernel(program, "render", errcode)
            OpenClContext.check(errcode, "clCreateKernel")
            handle
        }
        sceneArguments.forEachIndexed { i, buffer ->
            OpenClContext.check(clSetKernelArg1p(kernel, i, buffer), "clSetKernelArg($i)")
        }
        System.err.println("[gpu] built the ${MODE_NAMES[mode]} kernel in ${(System.nanoTime() - start) / 1_000_000} ms")
        program to kernel
    }.second

    /**
     * Left alone, NVIDIA's compiler gives the kernel ~100-128 registers, which fits only a
     * quarter of the threads a Pascal SM can hold. Capping registers costs some spilling but
     * hides memory latency far better: measured on a GTX 1070, 64 is best across the scenes,
     * except that volumes (a nested traversal) want 72. Only NVIDIA understands the flag.
     */
    private fun registerCap(extra: String): String {
        if (!cl.extensions.contains("cl_nv_compiler_options") || extra.contains("maxrregcount")) return ""
        return "-cl-nv-maxrregcount=${if (scene.mediumCount > 0) 72 else 64}"
    }

    override fun close() {
        programs.values.forEach { (program, kernel) ->
            clReleaseKernel(kernel)
            clReleaseProgram(program)
        }
        buffers.forEach { clReleaseMemObject(it) }
        cl.close()
        keepAwake?.close()
    }

    // OpenCL rejects zero-sized buffers, so empty sections get one padding element.
    private fun create(data: FloatArray): Long {
        val errcode = IntArray(1)
        val buffer = clCreateBuffer(cl.context, READ_FLAGS, if (data.isEmpty()) FloatArray(1) else data, errcode)
        OpenClContext.check(errcode, "clCreateBuffer")
        return buffer.also { buffers += it }
    }

    private fun create(data: IntArray): Long {
        val errcode = IntArray(1)
        val buffer = clCreateBuffer(cl.context, READ_FLAGS, if (data.isEmpty()) IntArray(1) else data, errcode)
        OpenClContext.check(errcode, "clCreateBuffer")
        return buffer.also { buffers += it }
    }

    private fun createBytes(data: ByteArray): Long {
        val native = MemoryUtil.memAlloc(maxOf(data.size, 1))
        try {
            if (data.isNotEmpty()) native.put(data).flip()
            val errcode = IntArray(1)
            val buffer = clCreateBuffer(cl.context, READ_FLAGS, native, errcode)
            OpenClContext.check(errcode, "clCreateBuffer(images)")
            return buffer.also { buffers += it }
        } finally {
            MemoryUtil.memFree(native)
        }
    }

    private fun createOutput(bytes: Long): Long = MemoryStack.stackPush().use { stack ->
        val errcode = stack.mallocInt(1)
        val handle = clCreateBuffer(cl.context, CL_MEM_READ_WRITE.toLong(), bytes, errcode)
        OpenClContext.check(errcode, "clCreateBuffer(output)")
        handle.also { buffers += it }
    }

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
        /** Kernel modes (AOV_MODE): the image, then the denoiser's albedo and normal guides. */
        const val MODE_IMAGE = 0
        const val MODE_ALBEDO = 1
        const val MODE_NORMAL = 2
        private val MODE_NAMES = listOf("image", "albedo guide", "normal guide")

        /** Output targets: the image, and the scratch buffer the guides use. */
        const val IMAGE = 0
        const val GUIDE = 1

        private const val CAMERA_FLOATS = 23
        private const val LOCAL_SIZE = 64L
        // Unsafe math (fused multiply-adds, faster division and square roots) leaves the image
        // the same to within 0.1 of 255 per pixel at 2000 spp, and speeds the kernel up by a
        // quarter. Never -cl-fast-relaxed-math or -cl-finite-math-only: the traversal relies on
        // INFINITY for open intervals and on NaN-tolerant fmin/fmax in the slab test.
        private const val BUILD_OPTIONS = "-cl-std=CL1.2 -cl-unsafe-math-optimizations"
        private const val READ_FLAGS = (CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR).toLong()
    }
}
