package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh

/** Growable float storage; mesh files rarely say up front how much data they hold. */
internal class FloatList(initialCapacity: Int = 1024) {
    private var data = FloatArray(initialCapacity.coerceAtLeast(16))
    var size: Int = 0
        private set

    val tripleCount: Int get() = size / 3

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun add(x: Float, y: Float, z: Float) {
        add(x); add(y); add(z)
    }

    operator fun get(index: Int): Float = data[index]

    fun toFloatArray(): FloatArray = data.copyOf(size)
}

/** Collects triangles for the format parsers and turns them into a [Mesh]. */
internal class MeshBuilder(expectedTriangles: Int = 128) {
    private val data = FloatList(expectedTriangles * Mesh.VERTEX_FLOATS)

    val triangleCount: Int get() = data.size / Mesh.VERTEX_FLOATS

    fun triangle(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ) {
        data.add(ax, ay, az)
        data.add(bx, by, bz)
        data.add(cx, cy, cz)
    }

    /**
     * Adds one face given as indices into a flat x/y/z vertex list. Faces with more than three
     * corners are triangulated as a fan, which is what every format here assumes for the convex
     * polygons it allows.
     */
    fun polygon(positions: FloatList, indices: IntArray) {
        if (indices.size < 3) return
        for (corner in 1 until indices.size - 1) {
            val a = indices[0] * 3
            val b = indices[corner] * 3
            val c = indices[corner + 1] * 3
            require(a >= 0 && c + 2 < positions.size) { "Face references a vertex outside the file" }
            triangle(
                positions[a], positions[a + 1], positions[a + 2],
                positions[b], positions[b + 1], positions[b + 2],
                positions[c], positions[c + 1], positions[c + 2]
            )
        }
    }

    fun build(): Mesh = Mesh(data.toFloatArray())
}
