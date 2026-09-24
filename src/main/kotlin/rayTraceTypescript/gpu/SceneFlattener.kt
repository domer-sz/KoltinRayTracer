package rayTraceTypescript.gpu

import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
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
import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap

/**
 * Walks the CPU scene graph once and lays it out as flat arrays for the GPU.
 *
 * The tree keeps the same geometry, so traversal on both sides returns the same closest hit.
 * [HittableList] is split in the middle instead of chained, which keeps the kernel's traversal
 * stack shallow without changing which sphere is hit first.
 */
class SceneFlattener private constructor() {

    private val nodeBounds = ArrayList<Float>()
    private val nodeLinks = ArrayList<Int>()
    private val spheres = ArrayList<Float>()
    private val sphereMaterials = ArrayList<Int>()
    private val triangles = ArrayList<Float>()
    private val triangleMaterials = ArrayList<Int>()
    private val quads = ArrayList<Float>()
    private val quadMaterials = ArrayList<Int>()
    private val materialInts = ArrayList<Int>()
    private val materialFloats = ArrayList<Float>()
    private val textureInts = ArrayList<Int>()
    private val textureFloats = ArrayList<Float>()
    private val images = ByteArrayOutputStream()
    private val perlinVectors = ArrayList<Float>()
    private val perlinPermutations = ArrayList<Int>()

    private val materialIndices = IdentityHashMap<Material, Int>()
    private val textureIndices = IdentityHashMap<Texture, Int>()
    private val colorTextures = IdentityHashMap<Color, Int>()
    private val perlinBlocks = IdentityHashMap<Perlin, Int>()

    private var maxDepth = 0

    companion object {
        fun flatten(world: Hittable): SceneBuffers = SceneFlattener().build(world)
    }

    private fun build(world: Hittable): SceneBuffers {
        val root = emit(world, depth = 1, transform = Transform.IDENTITY)
        require(maxDepth <= SceneBuffers.MAX_TRAVERSAL_DEPTH) {
            "Scene tree is $maxDepth levels deep, kernel stack holds ${SceneBuffers.MAX_TRAVERSAL_DEPTH}"
        }
        return SceneBuffers(
            nodeBounds = nodeBounds.toFloatArray(),
            nodeLinks = nodeLinks.toIntArray(),
            spheres = spheres.toFloatArray(),
            sphereMaterials = sphereMaterials.toIntArray(),
            triangles = triangles.toFloatArray(),
            triangleMaterials = triangleMaterials.toIntArray(),
            quads = quads.toFloatArray(),
            quadMaterials = quadMaterials.toIntArray(),
            materialInts = materialInts.toIntArray(),
            materialFloats = materialFloats.toFloatArray(),
            textureInts = textureInts.toIntArray(),
            textureFloats = textureFloats.toFloatArray(),
            textureImages = images.toByteArray(),
            perlinVectors = perlinVectors.toFloatArray(),
            perlinPermutations = perlinPermutations.toIntArray(),
            rootNode = root,
            maxDepth = maxDepth
        )
    }

    private fun emit(hittable: Hittable, depth: Int, transform: Transform): Int {
        if (depth > maxDepth) maxDepth = depth
        return when (hittable) {
            is Sphere -> emitSphere(hittable, transform)
            is Triangle -> emitTriangle(hittable, transform)
            is Quad -> emitQuad(hittable, transform)
            // Instances fold into the primitives below them instead of becoming nodes.
            is Translate -> emit(hittable.obj, depth, transform.after(Transform.translation(hittable.offset)))
            is RotateY -> emit(hittable.obj, depth, transform.after(Transform.rotation(hittable.cosTheta, hittable.sinTheta)))
            is BvhNode ->
                // A one-object BVH node stores the same child twice; testing it once is enough.
                if (hittable.left === hittable.right) emit(hittable.left, depth, transform)
                else emitInner(
                    emit(hittable.left, depth + 1, transform),
                    emit(hittable.right, depth + 1, transform)
                )
            is HittableList -> {
                require(hittable.objects.isNotEmpty()) { "Cannot render an empty world" }
                emitRange(hittable.objects, 0, hittable.objects.size, depth, transform)
            }
            else -> throw IllegalArgumentException("Unsupported hittable for GPU rendering: ${hittable::class.java.name}")
        }
    }

    private fun emitRange(objects: List<Hittable>, start: Int, end: Int, depth: Int, transform: Transform): Int {
        if (end - start == 1) return emit(objects[start], depth, transform)
        val mid = (start + end) / 2
        return emitInner(
            emitRange(objects, start, mid, depth + 1, transform),
            emitRange(objects, mid, end, depth + 1, transform)
        )
    }

    private fun emitQuad(original: Quad, transform: Transform): Int {
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

        val box = quad.aabbBoundingBox()
        return emitNode(
            floatArrayOf(box.x.min, box.y.min, box.z.min, box.x.max, box.y.max, box.z.max),
            index,
            SceneBuffers.LEAF_QUAD
        )
    }

    private fun emitTriangle(original: Triangle, transform: Transform): Int {
        val triangle = if (transform === Transform.IDENTITY) original
        else Triangle(
            transform.point(original.v0), transform.point(original.v1), transform.point(original.v2),
            original.material
        )

        val index = triangleMaterials.size
        for (component in listOf(triangle.v0, triangle.edge1, triangle.edge2)) {
            triangles.add(component.x)
            triangles.add(component.y)
            triangles.add(component.z)
        }
        repeat(3) { triangles.add(0.0f) }
        triangleMaterials.add(registerMaterial(triangle.material))

        val box = triangle.aabbBoundingBox()
        return emitNode(
            floatArrayOf(box.x.min, box.y.min, box.z.min, box.x.max, box.y.max, box.z.max),
            index,
            SceneBuffers.LEAF_TRIANGLE
        )
    }

    private fun emitSphere(sphere: Sphere, transform: Transform): Int {
        val sphereIndex = sphereMaterials.size
        // A sphere is round: a rotation only moves its centre, so the radius survives untouched.
        val start = transform.point(sphere.center.origin)
        val end = transform.point(sphere.center.at(1.0f))
        val radius = sphere.radius

        spheres.add(start.x)
        spheres.add(start.y)
        spheres.add(start.z)
        spheres.add(end.x - start.x)
        spheres.add(end.y - start.y)
        spheres.add(end.z - start.z)
        spheres.add(radius)
        spheres.add(0.0f)
        sphereMaterials.add(registerMaterial(sphere.material))

        return emitNode(
            floatArrayOf(
                minOf(start.x, end.x) - radius, minOf(start.y, end.y) - radius, minOf(start.z, end.z) - radius,
                maxOf(start.x, end.x) + radius, maxOf(start.y, end.y) + radius, maxOf(start.z, end.z) + radius
            ),
            sphereIndex,
            SceneBuffers.LEAF_SPHERE
        )
    }

    private fun emitInner(left: Int, right: Int): Int {
        val bounds = FloatArray(SceneBuffers.NODE_BOUNDS_STRIDE)
        for (i in 0 until 3) {
            bounds[i] = minOf(boundsAt(left, i), boundsAt(right, i))
            bounds[i + 3] = maxOf(boundsAt(left, i + 3), boundsAt(right, i + 3))
        }
        return emitNode(bounds, left, right)
    }

    private fun boundsAt(node: Int, component: Int): Float =
        nodeBounds[node * SceneBuffers.NODE_BOUNDS_STRIDE + component]

    private fun emitNode(bounds: FloatArray, first: Int, second: Int): Int {
        val index = nodeLinks.size / SceneBuffers.NODE_LINK_STRIDE
        bounds.forEach { nodeBounds.add(it) }
        nodeLinks.add(first)
        nodeLinks.add(second)
        return index
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
