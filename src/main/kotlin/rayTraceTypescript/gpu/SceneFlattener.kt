package rayTraceTypescript.gpu

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Isotropic
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.ConstantMedium
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.objects.RotateY
import rayTraceTypescript.objects.Translate
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.objects.Triangle
import rayTraceTypescript.textures.CheckerTexture
import rayTraceTypescript.textures.ImageTexture
import rayTraceTypescript.textures.NoiseTexture
import rayTraceTypescript.textures.Perlin
import rayTraceTypescript.textures.SolidColorTexture
import rayTraceTypescript.textures.Texture
import rayTraceTypescript.textures.UvCheckerTexture
import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap

/**
 * Walks the CPU scene graph once and lays it out as flat arrays for the GPU.
 *
 * The CPU tree is not copied: its shape is whatever the scene was assembled from (lists split by
 * index, the books' median-split BvhNode), which is fine for a CPU but costs the GPU dearly on
 * big meshes. Instead every primitive is collected with its instance transforms folded in, and
 * a fresh tree is built over them with the surface area heuristic (see [BvhBuilder]). The closest
 * hit along a ray does not depend on the tree, so both renderers still see the same scene.
 */
class SceneFlattener private constructor() {

    private val spheres = FloatBuffer()
    private val sphereMaterials = IntBuffer()
    private val triangles = FloatBuffer()
    private val triangleMaterials = IntBuffer()
    private val quads = FloatBuffer()
    private val quadMaterials = IntBuffer()
    private val mediumInts = IntBuffer()
    private val mediumFloats = FloatBuffer()
    private val lights = IntBuffer()
    private val materialInts = IntBuffer()
    private val materialFloats = FloatBuffer()
    private val textureInts = IntBuffer()
    private val textureFloats = FloatBuffer()
    private val images = ByteArrayOutputStream()
    private val perlinVectors = FloatBuffer()
    private val perlinPermutations = IntBuffer()

    private val materialIndices = IdentityHashMap<Material, Int>()
    private val textureIndices = IdentityHashMap<Texture, Int>()
    private val colorTextures = IdentityHashMap<Color, Int>()
    private val perlinBlocks = IdentityHashMap<Perlin, Int>()

    private val bvh = BvhBuilder()

    companion object {
        fun flatten(world: Hittable, lights: Hittable? = null): SceneBuffers =
            SceneFlattener().build(world, lights)
    }

    private fun build(world: Hittable, lightSources: Hittable?): SceneBuffers {
        val refs = PrimitiveRefs()
        collect(world, Transform.IDENTITY, refs)
        require(refs.size > 0) { "Cannot render an empty world" }
        val root = bvh.build(refs)
        lightSources?.let { collectLights(it, Transform.IDENTITY) }
        require(bvh.maxDepth <= SceneBuffers.MAX_TRAVERSAL_DEPTH) {
            "Scene tree is ${bvh.maxDepth} levels deep, kernel stack holds ${SceneBuffers.MAX_TRAVERSAL_DEPTH}"
        }
        val (orderedTriangles, orderedTriangleMaterials) = trianglesInLeafOrder()
        return SceneBuffers(
            nodes = bvh.nodes.toArray(),
            leaves = bvh.leaves.toArray(),
            refs = bvh.refs.toArray(),
            spheres = spheres.toArray(),
            sphereMaterials = sphereMaterials.toArray(),
            triangles = orderedTriangles,
            triangleMaterials = orderedTriangleMaterials,
            quads = quads.toArray(),
            quadMaterials = quadMaterials.toArray(),
            mediumInts = mediumInts.toArray(),
            mediumFloats = mediumFloats.toArray(),
            lights = lights.toArray(),
            materialInts = materialInts.toArray(),
            materialFloats = materialFloats.toArray(),
            textureInts = textureInts.toArray(),
            textureFloats = textureFloats.toArray(),
            textureImages = images.toByteArray(),
            perlinVectors = perlinVectors.toArray(),
            perlinPermutations = perlinPermutations.toArray(),
            rootNode = root,
            maxDepth = bvh.maxDepth
        )
    }

    /** Appends every primitive below [hittable] to [refs], instance transforms applied. */
    private fun collect(hittable: Hittable, transform: Transform, refs: PrimitiveRefs) {
        when (hittable) {
            is Sphere -> {
                val (index, start, end) = appendSphere(hittable, transform)
                val r = hittable.radius
                refs.add(
                    SceneBuffers.LEAF_SPHERE, index,
                    minOf(start.x, end.x) - r, minOf(start.y, end.y) - r, minOf(start.z, end.z) - r,
                    maxOf(start.x, end.x) + r, maxOf(start.y, end.y) + r, maxOf(start.z, end.z) + r
                )
            }
            is Triangle -> {
                val triangle = if (transform === Transform.IDENTITY) hittable
                else Triangle(
                    transform.point(hittable.v0), transform.point(hittable.v1), transform.point(hittable.v2),
                    hittable.material
                )
                val index = triangleMaterials.size
                for (v in listOf(triangle.v0, triangle.edge1, triangle.edge2)) {
                    triangles.add(v.x); triangles.add(v.y); triangles.add(v.z); triangles.add(0.0f)
                }
                triangleMaterials.add(registerMaterial(triangle.material))
                val box = triangle.aabbBoundingBox()
                refs.add(SceneBuffers.LEAF_TRIANGLE, index, box.x.min, box.y.min, box.z.min, box.x.max, box.y.max, box.z.max)
            }
            is Quad -> {
                val (index, quad) = appendQuad(hittable, transform)
                val box = quad.aabbBoundingBox()
                refs.add(SceneBuffers.LEAF_QUAD, index, box.x.min, box.y.min, box.z.min, box.x.max, box.y.max, box.z.max)
            }
            is ConstantMedium -> collectMedium(hittable, transform, refs)
            is Translate -> collect(hittable.obj, transform.after(Transform.translation(hittable.offset)), refs)
            is RotateY -> collect(hittable.obj, transform.after(Transform.rotation(hittable.cosTheta, hittable.sinTheta)), refs)
            is BvhNode -> {
                collect(hittable.left, transform, refs)
                // A one-object BVH node stores the same child twice; it is one primitive.
                if (hittable.left !== hittable.right) collect(hittable.right, transform, refs)
            }
            is HittableList -> hittable.objects.forEach { collect(it, transform, refs) }
            else -> throw IllegalArgumentException("Unsupported hittable for GPU rendering: ${hittable::class.java.name}")
        }
    }

    /**
     * A volume gets a tree of its own over its boundary, and one opaque leaf in the tree that
     * contains it: the kernel runs a second, nested traversal over the boundary to find where a
     * ray enters and leaves the fog.
     */
    private fun collectMedium(medium: ConstantMedium, transform: Transform, refs: PrimitiveRefs) {
        val boundary = PrimitiveRefs()
        collect(medium.boundary, transform, boundary)
        require(boundary.kinds.toArray().none { it == SceneBuffers.LEAF_MEDIUM }) { "A volume cannot contain a volume" }
        val boundaryRoot = bvh.build(boundary)

        val index = mediumFloats.size
        mediumInts.add(boundaryRoot)
        mediumInts.add(registerMaterial(medium.phaseFunction))
        mediumFloats.add(medium.negativeInverseDensity)
        val b = boundary.totalBounds()
        refs.add(SceneBuffers.LEAF_MEDIUM, index, b[0], b[1], b[2], b[3], b[4], b[5])
    }

    /**
     * Lights are written a second time, as bare geometry outside the tree: the renderer samples
     * them directly, which is a different job from finding them along a ray.
     */
    private fun collectLights(hittable: Hittable, transform: Transform) {
        when (hittable) {
            is Quad -> {
                val (index, _) = appendQuad(hittable, transform)
                lights.add(SceneBuffers.LEAF_QUAD)
                lights.add(index)
            }
            is Sphere -> {
                val (index, _, _) = appendSphere(hittable, transform)
                lights.add(SceneBuffers.LEAF_SPHERE)
                lights.add(index)
            }
            is HittableList -> hittable.objects.forEach { collectLights(it, transform) }
            is Translate -> collectLights(hittable.obj, transform.after(Transform.translation(hittable.offset)))
            is RotateY -> collectLights(hittable.obj, transform.after(Transform.rotation(hittable.cosTheta, hittable.sinTheta)))
            is BvhNode -> {
                collectLights(hittable.left, transform)
                if (hittable.left !== hittable.right) collectLights(hittable.right, transform)
            }
            else -> throw IllegalArgumentException(
                "Only quads and spheres can be sampled as lights, not ${hittable::class.java.name}"
            )
        }
    }

    /**
     * Rewrites the triangle buffer in the order the leaves reference it, so the facets of one
     * leaf - and of neighbouring leaves - sit next to each other in memory.
     */
    private fun trianglesInLeafOrder(): Pair<FloatArray, IntArray> {
        val source = triangles.toArray()
        val sourceMaterials = triangleMaterials.toArray()
        val remap = IntArray(sourceMaterials.size) { -1 }
        val ordered = FloatArray(source.size)
        val orderedMaterials = IntArray(sourceMaterials.size)
        var next = 0
        val refs = bvh.refs
        for (i in 0 until refs.size step 2) {
            if (refs[i] != SceneBuffers.LEAF_TRIANGLE) continue
            val old = refs[i + 1]
            if (remap[old] < 0) {
                remap[old] = next
                System.arraycopy(source, old * SceneBuffers.TRIANGLE_STRIDE, ordered, next * SceneBuffers.TRIANGLE_STRIDE, SceneBuffers.TRIANGLE_STRIDE)
                orderedMaterials[next] = sourceMaterials[old]
                next++
            }
            refs[i + 1] = remap[old]
        }
        return ordered to orderedMaterials
    }

    /** Writes a quad's geometry and returns its index; the caller decides where it is referenced. */
    private fun appendQuad(original: Quad, transform: Transform): Pair<Int, Quad> {
        // Rebuilding the quad from transformed corners lets its own constructor work out the
        // plane, the uv basis and the padded box again.
        val quad = if (transform === Transform.IDENTITY) original
        else Quad(transform.point(original.q), transform.vector(original.u), transform.vector(original.v), original.material)

        val index = quadMaterials.size
        for (vector in listOf(quad.q, quad.u, quad.v, quad.w, quad.normal)) {
            quads.add(vector.x)
            quads.add(vector.y)
            quads.add(vector.z)
        }
        quads.add(quad.d)
        quadMaterials.add(registerMaterial(quad.material))
        return index to quad
    }

    /** Writes a sphere's geometry and returns its index. */
    private fun appendSphere(sphere: Sphere, transform: Transform): Triple<Int, Point, Point> {
        val sphereIndex = sphereMaterials.size
        // A sphere is round: a rotation only moves its centre, so the radius survives untouched.
        val start = transform.point(sphere.center.origin)
        val end = transform.point(sphere.center.at(1.0f))

        spheres.add(start.x)
        spheres.add(start.y)
        spheres.add(start.z)
        spheres.add(end.x - start.x)
        spheres.add(end.y - start.y)
        spheres.add(end.z - start.z)
        spheres.add(sphere.radius)
        spheres.add(0.0f)
        sphereMaterials.add(registerMaterial(sphere.material))
        return Triple(sphereIndex, start, end)
    }

    private fun registerMaterial(material: Material): Int = materialIndices.getOrPut(material) {
        val index = materialInts.size / SceneBuffers.MATERIAL_INT_STRIDE
        when (material) {
            is Lambertian -> {
                materialInts.add(0)
                materialInts.add(registerTexture(material.texture))
                materialFloats.add(0.0f)
                materialFloats.add(0.0f)
            }
            is Metal -> {
                materialInts.add(1)
                materialInts.add(registerColor(material.albedo))
                materialFloats.add(material.fuzz)
                materialFloats.add(0.0f)
            }
            is DiffuseLight -> {
                materialInts.add(3)
                materialInts.add(registerTexture(material.texture))
                materialFloats.add(0.0f)
                materialFloats.add(0.0f)
            }
            is Isotropic -> {
                materialInts.add(4)
                materialInts.add(registerTexture(material.texture))
                materialFloats.add(0.0f)
                materialFloats.add(0.0f)
            }
            is Dielectric -> {
                materialInts.add(2)
                materialInts.add(0)
                materialFloats.add(0.0f)
                materialFloats.add(material.ri)
            }
            else -> throw IllegalArgumentException("Unsupported material for GPU rendering: ${material::class.java.name}")
        }
        index
    }

    private fun registerTexture(texture: Texture): Int = textureIndices.getOrPut(texture) {
        when (texture) {
            is SolidColorTexture -> solidTexture(texture.albedo)
            is CheckerTexture -> {
                // Children first: their indices are payload of the checker entry.
                val even = registerTexture(texture.even)
                val odd = registerTexture(texture.odd)
                textureEntry(intArrayOf(1, even, odd, 0), floatArrayOf(texture.invertedScale, 0.0f, 0.0f, 0.0f))
            }
            is ImageTexture -> imageTexture(texture)
            is NoiseTexture -> textureEntry(
                intArrayOf(4, registerPerlin(texture.perlin), 0, 0),
                floatArrayOf(texture.scale, 0.0f, 0.0f, 0.0f)
            )
            is UvCheckerTexture -> {
                val even = registerTexture(texture.even)
                val odd = registerTexture(texture.odd)
                textureEntry(intArrayOf(5, even, odd, 0), floatArrayOf(texture.cellsU, texture.cellsV, 0.0f, 0.0f))
            }
            else -> throw IllegalArgumentException("Unsupported texture for GPU rendering: ${texture::class.java.name}")
        }
    }

    /** Copies one Perlin instance's lattice into the shared buffers and returns its block. */
    private fun registerPerlin(perlin: Perlin): Int = perlinBlocks.getOrPut(perlin) {
        val block = perlinVectors.size / SceneBuffers.PERLIN_BLOCK
        perlin.randomVectors.forEach { vector ->
            perlinVectors.add(vector.x)
            perlinVectors.add(vector.y)
            perlinVectors.add(vector.z)
        }
        listOf(perlin.permX, perlin.permY, perlin.permZ).forEach { table ->
            table.forEach { perlinPermutations.add(it) }
        }
        block
    }

    private fun registerColor(color: Color): Int = colorTextures.getOrPut(color) { solidTexture(color) }

    private fun solidTexture(color: Color): Int =
        textureEntry(intArrayOf(0, 0, 0, 0), floatArrayOf(color.r, color.g, color.b, 0.0f))

    private fun imageTexture(texture: ImageTexture): Int {
        val image = texture.image
        val bytes = image.rgbBytes()
        // Matches ImageTexture.value(): an image that failed to load renders as cyan.
        if (bytes == null || image.height() <= 0) return textureEntry(intArrayOf(3, 0, 0, 0), FloatArray(4))
        val offset = images.size()
        images.write(bytes)
        return textureEntry(intArrayOf(2, offset, image.width(), image.height()), FloatArray(4))
    }

    private fun textureEntry(ints: IntArray, floats: FloatArray): Int {
        val index = textureInts.size / SceneBuffers.TEXTURE_INT_STRIDE
        ints.forEach { textureInts.add(it) }
        floats.forEach { textureFloats.add(it) }
        return index
    }
}

/** A growable FloatArray: the scene can run to millions of floats, too many to box one by one. */
internal class FloatBuffer(capacity: Int = 64) {
    private var data = FloatArray(capacity)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    operator fun get(index: Int): Float = data[index]
    operator fun set(index: Int, value: Float) { data[index] = value }
    fun toArray(): FloatArray = data.copyOf(size)
}

/** The Int counterpart of [FloatBuffer]. */
internal class IntBuffer(capacity: Int = 64) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    operator fun get(index: Int): Int = data[index]
    operator fun set(index: Int, value: Int) { data[index] = value }
    fun toArray(): IntArray = data.copyOf(size)
}

/** Primitives waiting for a tree: what they are, where their data lives, and their box. */
internal class PrimitiveRefs {
    val kinds = IntBuffer()
    val indices = IntBuffer()
    val bounds = FloatBuffer()
    val size: Int get() = kinds.size

    fun add(kind: Int, index: Int, minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float) {
        kinds.add(kind)
        indices.add(index)
        bounds.add(minX); bounds.add(minY); bounds.add(minZ)
        bounds.add(maxX); bounds.add(maxY); bounds.add(maxZ)
    }

    fun totalBounds(): FloatArray {
        val b = floatArrayOf(
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        )
        for (i in 0 until size) for (a in 0 until 3) {
            b[a] = minOf(b[a], bounds[i * 6 + a])
            b[a + 3] = maxOf(b[a + 3], bounds[i * 6 + a + 3])
        }
        return b
    }
}
