package rayTraceTypescript.render

import rayTraceTypescript.Color
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Linear RGB samples -> ASCII PPM. Quantization goes through [Color] so the CPU and GPU
 * backends share one gamma/clamp/rounding path and produce byte-identical files for
 * identical pixel values.
 */
object PpmWriter {
    fun write(outputPath: Path, width: Int, height: Int, linearRgb: FloatArray) {
        require(linearRgb.size == width * height * 3) { "Expected ${width * height * 3} samples, got ${linearRgb.size}" }
        val sb = StringBuilder("P3\n${width} ${height}\n255\n")
        for (pixel in 0 until width * height) {
            val color = Color(linearRgb[pixel * 3], linearRgb[pixel * 3 + 1], linearRgb[pixel * 3 + 2])
            sb.append("${color.colorR()} ${color.colorG()} ${color.colorB()}\n")
        }
        Files.write(outputPath, sb.toString().toByteArray(StandardCharsets.UTF_8))
    }
}
