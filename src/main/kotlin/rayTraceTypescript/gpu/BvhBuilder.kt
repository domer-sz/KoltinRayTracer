package rayTraceTypescript.gpu

/**
 * Builds the GPU's bounding volume hierarchy: a binary tree split by the surface area heuristic
 * (binned, over primitive centroids, trying all three axes), with up to [MAX_LEAF] primitives
 * per leaf.
 *
 * Each inner node stores the boxes of both children rather than its own, so the kernel tests a
 * node's two children with one read of 64 bytes and can step into the nearer one first:
 *  - float4 0: child A  min.x, max.x, min.y, max.y
 *  - float4 1: child B  min.x, max.x, min.y, max.y
 *  - float4 2: child A  min.z, max.z, child B  min.z, max.z
 *  - int4   3: child A code, child B code, 0, 0
 * A code >= 0 is another inner node; a negative code is a leaf, `~code` indexing [leaves]
 * (first ref, count). [refs] holds (kind, index) pairs in leaf order.
 *
 * Several trees share these buffers: the scene's, and one per volume boundary.
 */
internal class BvhBuilder {
    /** [NODE_INTS] per inner node; floats are stored as their raw bits. */
    val nodes = IntBuffer(1024)
    val leaves = IntBuffer(1024)
    val refs = IntBuffer(1024)
    var maxDepth = 0
        private set

    private lateinit var prims: PrimitiveRefs
    private lateinit var order: IntArray
    private lateinit var centroids: FloatArray

    // Scratch for binning, reused across nodes.
    private val binCounts = IntArray(BINS)
    private val binBounds = FloatArray(BINS * 6)
    private val rightAreas = FloatArray(BINS)

    /** How deep each grafted tree goes, by its root node. */
    private val graftDepths = HashMap<Int, Int>()

    /** Builds a tree over [primitives] and returns the index of its root node. */
    fun build(primitives: PrimitiveRefs): Int {
        prims = primitives
        val n = primitives.size
        order = IntArray(n) { it }
        centroids = FloatArray(n * 3)
        for (i in 0 until n) for (a in 0 until 3) {
            centroids[i * 3 + a] = 0.5f * (primitives.bounds[i * 6 + a] + primitives.bounds[i * 6 + a + 3])
        }

        val root = allocateNode()
        if (n == 1 && primitives.kinds[0] == SUBTREE) {
            // A lone grafted tree still hangs below a root of its own, beside an empty leaf.
            val box = rangeBounds(0, 1)
            val grafted = primitives.indices[0]
            writeNode(root, box, grafted, box, emptyLeaf())
            maxDepth = maxOf(maxDepth, 1 + (graftDepths[grafted] ?: 0))
            return root
        }
        val mid = findSplit(0, n, forceSplit = n > 1)
        if (mid < 0) {
            // Too few primitives to split: one leaf, and an empty leaf with the same box beside it.
            val leaf = makeLeaf(0, n)
            val box = rangeBounds(0, n)
            writeNode(root, box, leaf, box, emptyLeaf())
            maxDepth = maxOf(maxDepth, 1)
        } else {
            fillNode(root, 0, mid, n, depth = 1)
        }
        return root
    }

    private fun fillNode(node: Int, start: Int, mid: Int, end: Int, depth: Int) {
        maxDepth = maxOf(maxDepth, depth)
        val leftBox = rangeBounds(start, mid)
        val rightBox = rangeBounds(mid, end)
        val left = child(start, mid, depth + 1)
        val right = child(mid, end, depth + 1)
        writeNode(node, leftBox, left, rightBox, right)
    }

    private fun child(start: Int, end: Int, depth: Int): Int {
        if (end - start == 1 && prims.kinds[order[start]] == SUBTREE) {
            // A grafted tree is a child as it is; its root node is already in [nodes].
            val grafted = prims.indices[order[start]]
            maxDepth = maxOf(maxDepth, depth - 1 + (graftDepths[grafted] ?: 0))
            return grafted
        }
        // A grafted tree cannot share a leaf, so a range holding one is split down to it.
        val mid = findSplit(start, end, forceSplit = end - start > MAX_LEAF || holdsSubtree(start, end))
        if (mid < 0) return makeLeaf(start, end)
        val node = allocateNode()
        fillNode(node, start, mid, end, depth)
        return node
    }

    /**
     * Picks the cheapest binned SAH plane over [start, end) and partitions [order] around it.
     * Returns the split position, or -1 when a leaf is cheaper (and allowed).
     */
    private fun findSplit(start: Int, end: Int, forceSplit: Boolean): Int {
        val count = end - start
        if (count <= 1) return -1

        var cMinX = Float.POSITIVE_INFINITY; var cMinY = Float.POSITIVE_INFINITY; var cMinZ = Float.POSITIVE_INFINITY
        var cMaxX = Float.NEGATIVE_INFINITY; var cMaxY = Float.NEGATIVE_INFINITY; var cMaxZ = Float.NEGATIVE_INFINITY
        for (i in start until end) {
            val c = order[i] * 3
            val x = centroids[c]; val y = centroids[c + 1]; val z = centroids[c + 2]
            if (x < cMinX) cMinX = x; if (x > cMaxX) cMaxX = x
            if (y < cMinY) cMinY = y; if (y > cMaxY) cMaxY = y
            if (z < cMinZ) cMinZ = z; if (z > cMaxZ) cMaxZ = z
        }
        val cMin = floatArrayOf(cMinX, cMinY, cMinZ)
        val cMax = floatArrayOf(cMaxX, cMaxY, cMaxZ)

        val parentArea = area(rangeBounds(start, end))
        var bestCost = Float.POSITIVE_INFINITY
        var bestAxis = -1
        var bestBin = -1

        for (axis in 0 until 3) {
            val extent = cMax[axis] - cMin[axis]
            if (extent <= 0.0f) continue
            val scale = BINS / extent
            binCounts.fill(0)
            for (b in 0 until BINS) resetBox(binBounds, b * 6)
            for (i in start until end) {
                val p = order[i]
                val b = binOf(centroids[p * 3 + axis], cMin[axis], scale)
                binCounts[b]++
                growBox(binBounds, b * 6, prims.bounds, p * 6)
            }
            // Sweep from the right to know each plane's right-hand area, then from the left.
            val box = FloatArray(6).also { resetBox(it, 0) }
            var rightCount = 0
            val rightCounts = IntArray(BINS)
            for (b in BINS - 1 downTo 1) {
                growBox(box, 0, binBounds, b * 6)
                rightCount += binCounts[b]
                rightCounts[b] = rightCount
                rightAreas[b] = if (rightCount > 0) area(box) else 0.0f
            }
            resetBox(box, 0)
            var leftCount = 0
            for (b in 0 until BINS - 1) {
                growBox(box, 0, binBounds, b * 6)
                leftCount += binCounts[b]
                val right = rightCounts[b + 1]
                if (leftCount == 0 || right == 0) continue
                val cost = TRAVERSAL_COST + (area(box) * leftCount + rightAreas[b + 1] * right) / parentArea
                if (cost < bestCost) {
                    bestCost = cost; bestAxis = axis; bestBin = b
                }
            }
        }

        if (bestAxis < 0) {
            // Every centroid in one spot: no plane separates them, so split the range in half.
            return if (forceSplit) start + count / 2 else -1
        }
        if (!forceSplit && bestCost >= count * INTERSECTION_COST) return -1

        val scale = BINS / (cMax[bestAxis] - cMin[bestAxis])
        var i = start
        var j = end - 1
        while (i <= j) {
            if (binOf(centroids[order[i] * 3 + bestAxis], cMin[bestAxis], scale) <= bestBin) {
                i++
            } else {
                val t = order[i]; order[i] = order[j]; order[j] = t
                j--
            }
        }
        return if (i == start || i == end) start + count / 2 else i
    }

    private fun holdsSubtree(start: Int, end: Int): Boolean {
        for (i in start until end) if (prims.kinds[order[i]] == SUBTREE) return true
        return false
    }

    /**
     * Copies [placed]'s tree into these buffers, its boxes scaled, turned and moved as the
     * placement says, its triangles counted from [triangleBase]. Returns its root node, to be
     * handed to [build] as one [SUBTREE] primitive.
     */
    fun graft(placed: PlacedMesh, triangleBase: Int): Int {
        val block = placed.block
        val nodeBase = nodes.size / NODE_INTS
        val leafBase = leaves.size / 2
        val refBase = refs.size / 2
        val source = block.nodes
        val box = FloatArray(6)
        fun f(i: Int) = java.lang.Float.intBitsToFloat(i)
        fun bits(v: Float) = java.lang.Float.floatToRawIntBits(v)
        for (node in 0 until source.size / NODE_INTS) {
            val at = node * NODE_INTS
            val out = FloatArray(12)
            // Child A's box, then child B's: x at 0-1 / 4-5, y at 2-3 / 6-7, z at 8-9 / 10-11.
            for (child in 0 until 2) {
                val x = at + child * 4
                val z = at + 8 + child * 2
                placedBox(placed, f(source[x]), f(source[x + 1]), f(source[x + 2]), f(source[x + 3]), f(source[z]), f(source[z + 1]), box)
                out[child * 4] = box[0]; out[child * 4 + 1] = box[3]
                out[child * 4 + 2] = box[1]; out[child * 4 + 3] = box[4]
                out[8 + child * 2] = box[2]; out[9 + child * 2] = box[5]
            }
            for (k in 0 until 12) nodes.add(bits(out[k]))
            for (k in 12 until 14) {
                val code = source[at + k]
                nodes.add(if (code >= 0) code + nodeBase else (code.inv() + leafBase).inv())
            }
            for (k in 14 until NODE_INTS) nodes.add(source[at + k])
        }
        for (i in block.leaves.indices step 2) {
            leaves.add(block.leaves[i] + refBase)
            leaves.add(block.leaves[i + 1])
        }
        for (i in block.refs.indices step 2) {
            refs.add(block.refs[i])
            refs.add(block.refs[i + 1] + triangleBase)
        }
        graftDepths[nodeBase] = block.maxDepth
        return nodeBase
    }

    /** The box around a local box once placed: its four turned x/z corners, scaled and moved. */
    private fun placedBox(p: PlacedMesh, minX: Float, maxX: Float, minY: Float, maxY: Float, minZ: Float, maxZ: Float, out: FloatArray) {
        var loX = Float.POSITIVE_INFINITY; var hiX = Float.NEGATIVE_INFINITY
        var loZ = Float.POSITIVE_INFINITY; var hiZ = Float.NEGATIVE_INFINITY
        for (corner in 0 until 4) {
            val x = if (corner and 1 == 0) minX else maxX
            val z = if (corner and 2 == 0) minZ else maxZ
            val tx = (p.cos * x + p.sin * z) * p.scale + p.offset.x
            val tz = (-p.sin * x + p.cos * z) * p.scale + p.offset.z
            if (tx < loX) loX = tx; if (tx > hiX) hiX = tx
            if (tz < loZ) loZ = tz; if (tz > hiZ) hiZ = tz
        }
        // Rounding in the product could shave the box below the triangles moved the same way.
        val slack = 1e-5f * maxOf(1.0f, kotlin.math.abs(p.scale) * (maxX - minX + maxY - minY + maxZ - minZ))
        out[0] = loX - slack; out[3] = hiX + slack
        out[1] = minY * p.scale + p.offset.y - slack; out[4] = maxY * p.scale + p.offset.y + slack
        out[2] = loZ - slack; out[5] = hiZ + slack
    }

    private fun binOf(value: Float, min: Float, scale: Float): Int =
        ((value - min) * scale).toInt().coerceIn(0, BINS - 1)

    private fun makeLeaf(start: Int, end: Int): Int {
        val leaf = leaves.size / 2
        leaves.add(refs.size / 2)
        leaves.add(end - start)
        for (i in start until end) {
            refs.add(prims.kinds[order[i]])
            refs.add(prims.indices[order[i]])
        }
        return leaf.inv()
    }

    private fun emptyLeaf(): Int {
        val leaf = leaves.size / 2
        leaves.add(refs.size / 2)
        leaves.add(0)
        return leaf.inv()
    }

    private fun allocateNode(): Int {
        val node = nodes.size / NODE_INTS
        repeat(NODE_INTS) { nodes.add(0) }
        return node
    }

    private fun writeNode(node: Int, a: FloatArray, codeA: Int, b: FloatArray, codeB: Int) {
        val at = node * NODE_INTS
        val values = floatArrayOf(
            a[0], a[3], a[1], a[4],
            b[0], b[3], b[1], b[4],
            a[2], a[5], b[2], b[5]
        )
        for (k in values.indices) nodes[at + k] = java.lang.Float.floatToRawIntBits(values[k])
        nodes[at + 12] = codeA
        nodes[at + 13] = codeB
    }

    private fun rangeBounds(start: Int, end: Int): FloatArray {
        val box = FloatArray(6).also { resetBox(it, 0) }
        for (i in start until end) growBox(box, 0, prims.bounds, order[i] * 6)
        return box
    }

    private fun resetBox(box: FloatArray, at: Int) {
        for (a in 0 until 3) {
            box[at + a] = Float.POSITIVE_INFINITY
            box[at + a + 3] = Float.NEGATIVE_INFINITY
        }
    }

    private fun growBox(box: FloatArray, at: Int, source: FloatBuffer, from: Int) {
        for (a in 0 until 3) {
            if (source[from + a] < box[at + a]) box[at + a] = source[from + a]
            if (source[from + a + 3] > box[at + a + 3]) box[at + a + 3] = source[from + a + 3]
        }
    }

    private fun growBox(box: FloatArray, at: Int, source: FloatArray, from: Int) {
        for (a in 0 until 3) {
            if (source[from + a] < box[at + a]) box[at + a] = source[from + a]
            if (source[from + a + 3] > box[at + a + 3]) box[at + a + 3] = source[from + a + 3]
        }
    }

    private fun area(box: FloatArray): Float {
        val dx = box[3] - box[0]
        val dy = box[4] - box[1]
        val dz = box[5] - box[2]
        if (dx < 0.0f || dy < 0.0f || dz < 0.0f) return 0.0f
        return 2.0f * (dx * dy + dy * dz + dz * dx)
    }

    companion object {
        const val NODE_INTS = SceneBuffers.NODE_INTS
        const val MAX_LEAF = 4
        /** A primitive kind only [build] sees: a tree already grafted in, its index the root node. */
        const val SUBTREE = -100
        private const val BINS = 16
        /**
         * Cost of testing a node's two boxes, relative to one primitive test. The GPU reads both
         * boxes in one go and tests them in registers, so it is cheap: measured on a GTX 1070,
         * 0.5 beats 1.0 by 10% on a 331k-triangle mesh and 4% on the spheres (0.2-0.5 all alike),
         * at the price of 5% on the few big quads of the Cornell box.
         */
        private const val TRAVERSAL_COST = 0.5f
        private const val INTERSECTION_COST = 1.0f
    }
}
