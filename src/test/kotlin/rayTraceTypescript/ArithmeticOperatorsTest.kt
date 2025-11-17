package rayTraceTypescript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArithmeticOperatorsTest {
    @Test
    fun `vector operator chaining preserves evaluation order`() {
        val a = Vector(1.0f, -2.0f, 3.0f)
        val b = Vector(-4.0f, 1.0f, 2.0f)
        val c = Vector(0.5f, -1.5f, 0.25f)

        val result = (((a + b * 2.0f) - (c / 2.0f)) - c) * 0.25f

        assertVectorEquals(Vector(-1.9375f, 0.5625f, 1.65625f), result)
    }

    @Test
    fun `color operator chaining preserves evaluation order`() {
        val c1 = Color(0.25f, 0.5f, 0.75f)
        val c2 = Color(0.5f, 0.25f, 0.1f)
        val c3 = Color(1.0f, 0.5f, 0.25f)

        val result = ((c1 + c2 * 2.0f) - (c3 / 4.0f)) * c2

        assertColorEquals(Color(0.5f, 0.21875f, 0.08875f), result)
    }

    @Test
    fun `vector unit and cross products stay consistent`() {
        val from = Vector(13.0f, 2.0f, 3.0f)
        val w = from.unit()
        assertVectorEquals(Vector(0.9636241f, 0.14824986f, 0.2223748f), w, 1e-6f)

        val vUp = Vector(0.0f, 1.0f, 0.0f)
        val u = Vector.unit(Vector.cross(vUp, w))
        assertVectorEquals(Vector(0.2248595f, 0.0f, -0.9743912f), u, 1e-6f)

        val v = Vector.cross(w, u)
        assertVectorEquals(Vector(-0.14445336f, 0.98894995f, -0.03333539f), v, 1e-6f)
    }

    private fun assertVectorEquals(expected: Vector, actual: Vector, delta: Float = 1e-6f) {
        assertEquals(expected.x, actual.x, delta, "x mismatch")
        assertEquals(expected.y, actual.y, delta, "y mismatch")
        assertEquals(expected.z, actual.z, delta, "z mismatch")
    }

    private fun assertColorEquals(expected: Color, actual: Color, delta: Float = 1e-6f) {
        assertEquals(expected.r, actual.r, delta, "r mismatch")
        assertEquals(expected.g, actual.g, delta, "g mismatch")
        assertEquals(expected.b, actual.b, delta, "b mismatch")
    }
}
