package rayTraceTypescript.render

import org.lwjgl.PointerBuffer
import org.lwjgl.system.Library
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.SharedLibrary
import org.lwjgl.system.libffi.FFICIF
import org.lwjgl.system.libffi.FFIType
import org.lwjgl.system.libffi.LibFFI
import java.io.File

/**
 * Intel Open Image Denoise, called straight from its DLL through LWJGL's libffi - no binding
 * library needed. The "RT" filter takes the noisy linear image plus the albedo and normal of
 * the first hit (see the kernel's AOV_MODE) and returns a clean image of the same size.
 *
 * The Windows x64 DLLs of OIDN 2.5.1 ship with the repository in native/oidn/windows-x64, so a
 * fresh clone denoises as is; -Drt.oidn=<dir> or OIDN_DIR point elsewhere. Only the CPU device
 * is used, as OIDN's GPU devices need a newer card than a GTX 1070.
 */
class Denoiser private constructor(private val library: SharedLibrary) : AutoCloseable {

    private val newDevice = Fn("oidnNewDevice", LibFFI.ffi_type_pointer, LibFFI.ffi_type_sint32)
    private val commitDevice = Fn("oidnCommitDevice", LibFFI.ffi_type_void, LibFFI.ffi_type_pointer)
    private val deviceError = Fn("oidnGetDeviceError", LibFFI.ffi_type_sint32, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer)
    private val releaseDevice = Fn("oidnReleaseDevice", LibFFI.ffi_type_void, LibFFI.ffi_type_pointer)
    private val newFilter = Fn("oidnNewFilter", LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer)
    private val setImage = Fn(
        "oidnSetSharedFilterImage", LibFFI.ffi_type_void,
        LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_sint32,
        LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64,
        LibFFI.ffi_type_uint64
    )
    private val setBool = Fn("oidnSetFilterBool", LibFFI.ffi_type_void, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_uint8)
    private val commitFilter = Fn("oidnCommitFilter", LibFFI.ffi_type_void, LibFFI.ffi_type_pointer)
    private val executeFilter = Fn("oidnExecuteFilter", LibFFI.ffi_type_void, LibFFI.ffi_type_pointer)
    private val releaseFilter = Fn("oidnReleaseFilter", LibFFI.ffi_type_void, LibFFI.ffi_type_pointer)

    /** Denoises linear [color] (3 floats per pixel, rows top to bottom) with its two guides. */
    fun denoise(width: Int, height: Int, color: FloatArray, albedo: FloatArray, normal: FloatArray): FloatArray {
        val images = listOf(color, albedo, normal, FloatArray(color.size)).map { data ->
            MemoryUtil.memAllocFloat(data.size).put(data).flip()
        }
        val device = newDevice(OIDN_DEVICE_TYPE_CPU)
        try {
            commitDevice(device)
            val filter = newFilter(device, "RT")
            try {
                listOf("color", "albedo", "normal", "output").zip(images).forEach { (name, image) ->
                    setImage(
                        filter, name, MemoryUtil.memAddress(image), OIDN_FORMAT_FLOAT3,
                        width.toLong(), height.toLong(), 0L, 12L, width * 12L
                    )
                }
                setBool(filter, "hdr", true)
                commitFilter(filter)
                executeFilter(filter)
                checkError(device)
                return FloatArray(color.size).also { images[3].get(it) }
            } finally {
                releaseFilter(filter)
            }
        } finally {
            releaseDevice(device)
            images.forEach { MemoryUtil.memFree(it) }
        }
    }

    private fun checkError(device: Long) {
        MemoryStack.stackPush().use { stack ->
            val message = stack.mallocPointer(1)
            val code = deviceError(device, MemoryUtil.memAddress(message)).toInt()
            if (code != 0) {
                val text = message.get(0).takeIf { it != 0L }?.let { MemoryUtil.memUTF8(it) } ?: "no message"
                throw IllegalStateException("Open Image Denoise failed ($code): $text")
            }
        }
    }

    override fun close() = library.free()

    /** One C function and the libffi call description for its signature. */
    private inner class Fn(name: String, private val returns: FFIType, private vararg val params: FFIType) {
        private val address = library.getFunctionAddress(name).also {
            require(it != 0L) { "$name is missing from ${library.name}" }
        }
        private val types: PointerBuffer = MemoryUtil.memAllocPointer(params.size).also { buffer ->
            params.forEachIndexed { i, type -> buffer.put(i, type) }
        }
        private val cif = FFICIF.calloc().also {
            check(LibFFI.ffi_prep_cif(it, LibFFI.FFI_DEFAULT_ABI, returns, types) == LibFFI.FFI_OK) { "ffi_prep_cif($name)" }
        }

        /** Arguments: Long for pointers and sizes, Int, Boolean, or String (passed as UTF-8). */
        operator fun invoke(vararg args: Any): Long {
            MemoryStack.stackPush().use { stack ->
                val values = stack.mallocPointer(params.size)
                args.forEachIndexed { i, arg ->
                    val cell = when (arg) {
                        is Long -> MemoryUtil.memAddress(stack.mallocLong(1).put(0, arg))
                        is Int -> MemoryUtil.memAddress(stack.mallocInt(1).put(0, arg))
                        is Boolean -> MemoryUtil.memAddress(stack.malloc(1).put(0, if (arg) 1 else 0))
                        is String -> MemoryUtil.memAddress(stack.pointers(MemoryUtil.memAddress(stack.UTF8(arg))))
                        else -> throw IllegalArgumentException("Unsupported argument ${arg::class}")
                    }
                    values.put(i, cell)
                }
                val result = stack.malloc(8)
                LibFFI.ffi_call(cif, address, result, values)
                return result.getLong(0)
            }
        }
    }

    companion object {
        private const val OIDN_DEVICE_TYPE_CPU = 1
        private const val OIDN_FORMAT_FLOAT3 = 3

        /** Where the repository keeps its own copy of the OIDN DLLs, relative to the project root. */
        private const val BUNDLED = "native/oidn/windows-x64"

        /**
         * The OIDN folder: -Drt.oidn or OIDN_DIR when set, else the copy bundled with the
         * repository. Null when the chosen folder holds no OpenImageDenoise.dll.
         */
        fun folder(): File? = File(System.getProperty("rt.oidn") ?: System.getenv("OIDN_DIR") ?: BUNDLED)
            .takeIf { File(it, "OpenImageDenoise.dll").isFile }

        /**
         * Loads OIDN from [folder]. Its own dependencies go first: Windows resolves a DLL's imports
         * against modules already loaded, not against the folder the DLL came from.
         */
        fun open(folder: File): Denoiser {
            for (dependency in listOf("tbb12.dll", "OpenImageDenoise_core.dll")) {
                File(folder, dependency).takeIf { it.isFile }?.let { Library.loadNative("rayTraceTypescript", it.absolutePath) }
            }
            return Denoiser(Library.loadNative("rayTraceTypescript", File(folder, "OpenImageDenoise.dll").absolutePath))
        }
    }
}
