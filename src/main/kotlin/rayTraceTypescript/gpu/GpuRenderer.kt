package rayTraceTypescript.gpu

import org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR
import org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY
import org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY
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
        val scene = SceneFlattener.flatten(world)
        val pixels = FloatArray(setup.totalPixels * 3)

        OpenClContext.create().use { cl ->
            val program = cl.buildProgram(kernelSource(), BUILD_OPTIONS)
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
                val nodeBounds = readOnly(scene.nodeBounds)
                val nodeLinks = readOnly(scene.nodeLinks)
                val spheres = readOnly(scene.spheres)
                val sphereMaterials = readOnly(scene.sphereMaterials)
                val materialInts = readOnly(scene.materialInts)
                val materialFloats = readOnly(scene.materialFloats)
                val textureInts = readOnly(scene.textureInts)
                val textureFloats = readOnly(scene.textureFloats)
                val textureImages = createBytes(cl.context, scene.textureImages).also { buffers += it }

                val output = MemoryStack.stackPush().use { stack ->
                    val errcode = stack.mallocInt(1)
                    val handle = clCreateBuffer(
                        cl.context,
                        CL_MEM_WRITE_ONLY.toLong(),
                        pixels.size.toLong() * Float.SIZE_BYTES,
                        errcode
                    )
                    OpenClContext.check(errcode, "clCreateBuffer(output)")
                    handle
                }
                buffers += output

                var arg = 0
                for (buffer in listOf(
                    cameraBuffer, nodeBounds, nodeLinks, spheres, sphereMaterials,
                    materialInts, materialFloats, textureInts, textureFloats, textureImages, output
                )) {
                    OpenClContext.check(clSetKernelArg1p(kernel, arg++, buffer), "clSetKernelArg($arg)")
                }
                clSetKernelArg1i(kernel, arg++, setup.imageWidth)
                clSetKernelArg1i(kernel, arg++, setup.imageHeight)
                val rowOffsetArg = arg++
                clSetKernelArg1i(kernel, rowOffsetArg, 0)
                clSetKernelArg1i(kernel, arg++, setup.samplesPerPixel)
                clSetKernelArg1i(kernel, arg++, setup.maxReflectionDepth)
                clSetKernelArg1f(kernel, arg++, setup.pixelSamplesScale)
                clSetKernelArg1i(kernel, arg++, scene.rootNode)
                clSetKernelArg1l(kernel, arg, seed())

                val progress = ProgressBar(setup.totalPixels)
                MemoryStack.stackPush().use { stack ->
                    val globalWorkSize = stack.mallocPointer(1)
                    var row = 0
                    while (row < setup.imageHeight) {
                        val rows = minOf(ROWS_PER_BATCH, setup.imageHeight - row)
                        clSetKernelArg1i(kernel, rowOffsetArg, row)
                        globalWorkSize.put(0, (setup.imageWidth.toLong() * rows))
                        OpenClContext.check(
                            clEnqueueNDRangeKernel(cl.queue, kernel, 1, null, globalWorkSize, null, null, null),
                            "clEnqueueNDRangeKernel"
                        )
                        OpenClContext.check(clFinish(cl.queue), "clFinish")
                        row += rows
                        progress.report(row * setup.imageWidth)
                    }
                }

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

        return setup.totalPixels.toLong() * setup.samplesPerPixel
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
        setup.defocusAngle
    )

    companion object {
        private const val ROWS_PER_BATCH = 16
        // No fast-math: it would change shading brightness and break the nearZero/infinity checks.
        private const val BUILD_OPTIONS = "-cl-std=CL1.2"
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
