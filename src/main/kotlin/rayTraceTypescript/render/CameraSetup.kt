package rayTraceTypescript.render

import rayTraceTypescript.Point
import rayTraceTypescript.Vector

/**
 * Result of Camera.initialize(): everything a renderer needs to shoot rays.
 * Computed once on the CPU so that every backend starts from identical numbers.
 */
data class CameraSetup(
    val imageWidth: Int,
    val imageHeight: Int,
    val samplesPerPixel: Int,
    val maxReflectionDepth: Int,
    val pixelSamplesScale: Float,
    val cameraCenter: Point,
    val pixel00: Point,
    val pixelDeltaU: Vector,
    val pixelDeltaV: Vector,
    val defocusAngle: Float,
    val defocusDiscU: Vector,
    val defocusDiscV: Vector
) {
    val totalPixels: Int get() = imageWidth * imageHeight
}
