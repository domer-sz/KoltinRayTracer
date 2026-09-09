package rayTraceTypescript.textures

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow

class RtwImage() : AutoCloseable {

    private val bytesPerPixel = 3
    private var fdata: FloatArray? = null              // Linear floating point pixel data (RGBRGB...)
    private var bdata: ByteArray? = null               // Linear 8-bit pixel data (RGBRGB...)
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var bytesPerScanline: Int = 0

    companion object {
        private val MAGENTA = intArrayOf(255, 0, 255)

        private fun clamp(x: Int, low: Int, high: Int): Int {
            // Clamp to [low, high)
            if (x < low) return low
            if (x < high) return x
            return high - 1
        }

        private fun floatToByte(value: Float): Byte {
            return when {
                value <= 0.0f -> 0
                value >= 1.0f -> 255.toByte()
                else -> (256.0f * value).toInt().coerceIn(0, 255).toByte()
            }
        }

        /**
         * Convert sRGB component [0..1] to linear [0..1].
         * This approximates stb_loadf-like "linear" intent for standard images.
         */
        private fun srgbToLinear(c: Float): Float {
            return if (c <= 0.04045f) {
                c / 12.92f
            } else {
                (((c + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()
            }
        }
    }

    constructor(imageFilename: String) : this() {
        val filename = imageFilename
        val imageDir = System.getenv("RTW_IMAGES")

        if (imageDir != null && load("$imageDir/$imageFilename")) return
        if (load(filename)) return
        if (load("images/$filename")) return
        if (load("../images/$filename")) return
        if (load("../../images/$filename")) return
        if (load("../../../images/$filename")) return
        if (load("../../../../images/$filename")) return
        if (load("../../../../../images/$filename")) return
        if (load("../../../../../../images/$filename")) return

        System.err.println("ERROR: Could not load image file '$imageFilename'.")
    }

    /** Raw 8-bit RGB scanlines (RGBRGB...), or null when no image is loaded. */
    internal fun rgbBytes(): ByteArray? = bdata

    fun width(): Int = if (fdata == null) 0 else imageWidth
    fun height(): Int = if (fdata == null) 0 else imageHeight

    /**
     * Returns 3 RGB channel values for pixel at (x, y).
     * If no image is loaded, returns magenta.
     */
    fun pixelData(x: Int, y: Int): IntArray {
        val data = bdata ?: return MAGENTA.copyOf()

        val cx = clamp(x, 0, imageWidth)
        val cy = clamp(y, 0, imageHeight)
        val offset = cy * bytesPerScanline + cx * bytesPerPixel

        return intArrayOf(
            data[offset].toInt() and 0xFF,
            data[offset + 1].toInt() and 0xFF,
            data[offset + 2].toInt() and 0xFF
        )
    }

    /**
     * Loads image from file, converts to linear float RGB and 8-bit RGB.
     * Returns true on success.
     *
     * Note: Uses ImageIO (PNG/JPG/BMP/GIF depending on JVM plugins).
     * HDR formats are generally not supported by default ImageIO.
     */
    fun load(filename: String): Boolean {
        val file = File(filename)
        if (!file.exists() || !file.isFile) return false

        val img: BufferedImage = try {
            ImageIO.read(file) ?: return false
        } catch (_: Exception) {
            return false
        }

        imageWidth = img.width
        imageHeight = img.height
        if (imageWidth <= 0 || imageHeight <= 0) return false

        val totalPixels = imageWidth * imageHeight
        val rgbInts = IntArray(totalPixels)
        img.getRGB(0, 0, imageWidth, imageHeight, rgbInts, 0, imageWidth)

        val floats = FloatArray(totalPixels * bytesPerPixel)

        var j = 0
        for (argb in rgbInts) {
            val r8 = (argb ushr 16) and 0xFF
            val g8 = (argb ushr 8) and 0xFF
            val b8 = argb and 0xFF

            val rSrgb = r8 / 255.0f
            val gSrgb = g8 / 255.0f
            val bSrgb = b8 / 255.0f

            // Store linear RGB floats [0,1]
            floats[j++] = srgbToLinear(rSrgb)
            floats[j++] = srgbToLinear(gSrgb)
            floats[j++] = srgbToLinear(bSrgb)
        }

        fdata = floats
        bytesPerScanline = imageWidth * bytesPerPixel
        convertToBytes()
        return true
    }

    private fun convertToBytes() {
        val floats = fdata ?: run {
            bdata = null
            return
        }

        val totalBytes = imageWidth * imageHeight * bytesPerPixel
        val out = ByteArray(totalBytes)

        for (i in 0 until totalBytes) {
            out[i] = floatToByte(floats[i])
        }

        bdata = out
    }

    override fun close() {
        // Not strictly needed on JVM, but matches C++ intent.
        fdata = null
        bdata = null
        imageWidth = 0
        imageHeight = 0
        bytesPerScanline = 0
    }
}
