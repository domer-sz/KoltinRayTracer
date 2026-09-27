package rayTraceTypescript.render

import rayTraceTypescript.Color
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Linear RGB samples -> ASCII PPM, plus the same pixels as a PNG beside it (see [pngPath]):
 * the PPM is the project's reference format, the PNG a sixth of the size and viewable anywhere.
 * Quantization goes through [Color] once for both, so the CPU and GPU backends share one
 * gamma/clamp/rounding path and the two files always hold identical pixels.
 */
object PpmWriter {
    fun write(outputPath: Path, width: Int, height: Int, linearRgb: FloatArray) {
        require(linearRgb.size == width * height * 3) { "Expected ${width * height * 3} samples, got ${linearRgb.size}" }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val sb = StringBuilder("P3\n${width} ${height}\n255\n")
        for (pixel in 0 until width * height) {
            val color = Color(linearRgb[pixel * 3], linearRgb[pixel * 3 + 1], linearRgb[pixel * 3 + 2])
            val r = color.colorR()
            val g = color.colorG()
            val b = color.colorB()
            sb.append("$r $g $b\n")
            image.setRGB(pixel % width, pixel / width, (r shl 16) or (g shl 8) or b)
        }
        Files.write(outputPath, sb.toString().toByteArray(StandardCharsets.UTF_8))
        ImageIO.write(image, "png", pngPath(outputPath).toFile())
    }

    /** Where the PNG copy of [outputPath] goes: the same name with a .png extension. */
    fun pngPath(outputPath: Path): Path {
        val name = outputPath.fileName.toString()
        val stem = if (name.lowercase().endsWith(".ppm")) name.dropLast(4) else name
        return outputPath.resolveSibling("$stem.png")
    }
}
