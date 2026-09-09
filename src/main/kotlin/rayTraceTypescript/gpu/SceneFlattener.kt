package rayTraceTypescript.gpu

import rayTraceTypescript.Color
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.textures.CheckerTexture
import rayTraceTypescript.textures.ImageTexture
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
    private val materialInts = ArrayList<Int>()
    private val materialFloats = ArrayList<Float>()
    private val textureInts = ArrayList<Int>()
    private val textureFloats = ArrayList<Float>()
    private val images = ByteArrayOutputStream()

    private val materialIndices = IdentityHashMap<Material, Int>()
    private val textureIndices = IdentityHashMap<Texture, Int>()
    private val colorTextures = IdentityHashMap<Color, Int>()

    private var maxDepth = 0

    companion object {
        fun flatten(world: Hittable): SceneBuffers = SceneFlattener().build(world)
    }

    private fun build(world: Hittable): SceneBuffers {
        val root = emit(world, depth = 1)
        require(maxDepth <= SceneBuffers.MAX_TRAVERSAL_DEPTH) {
            "Scene tree is $maxDepth levels deep, kernel stack holds ${SceneBuffers.MAX_TRAVERSAL_DEPTH}"
        }
        return SceneBuffers(
            nodeBounds = nodeBounds.toFloatArray(),
            nodeLinks = nodeLinks.toIntArray(),
            spheres = spheres.toFloatArray(),
            sphereMaterials = sphereMaterials.toIntArray(),
            materialInts = materialInts.toIntArray(),
            materialFloats = materialFloats.toFloatArray(),
            textureInts = textureInts.toIntArray(),
            textureFloats = textureFloats.toFloatArray(),
            textureImages = images.toByteArray(),
            rootNode = root,
            maxDepth = maxDepth
        )
    }

    private fun emit(hittable: Hittable, depth: Int): Int {
        if (depth > maxDepth) maxDepth = depth
        return when (hittable) {
            is Sphere -> emitLeaf(hittable)
            is BvhNode ->
                // A one-object BVH node stores the same child twice; testing it once is enough.
                if (hittable.left === hittable.right) emit(hittable.left, depth)
                else emitInner(emit(hittable.left, depth + 1), emit(hittable.right, depth + 1))
            is HittableList -> {
                require(hittable.objects.isNotEmpty()) { "Cannot render an empty world" }
                emitRange(hittable.objects, 0, hittable.objects.size, depth)
            }
            else -> throw IllegalArgumentException("Unsupported hittable for GPU rendering: ${hittable::class.java.name}")
        }
    }

    private fun emitRange(objects: List<Hittable>, start: Int, end: Int, depth: Int): Int {
        if (end - start == 1) return emit(objects[start], depth)
        val mid = (start + end) / 2
        return emitInner(emitRange(objects, start, mid, depth + 1), emitRange(objects, mid, end, depth + 1))
    }

    private fun emitLeaf(sphere: Sphere): Int {
        val sphereIndex = sphereMaterials.size
        val center = sphere.center
        spheres.add(center.origin.x)
        spheres.add(center.origin.y)
        spheres.add(center.origin.z)
        spheres.add(center.direction.x)
        spheres.add(center.direction.y)
        spheres.add(center.direction.z)
        spheres.add(sphere.radius)
        spheres.add(0.0f)
        sphereMaterials.add(registerMaterial(sphere.material))

        val box = sphere.aabbBoundingBox()
        return emitNode(
            floatArrayOf(box.x.min, box.y.min, box.z.min, box.x.max, box.y.max, box.z.max),
            sphereIndex,
            SceneBuffers.LEAF
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
            else -> throw IllegalArgumentException("Unsupported texture for GPU rendering: ${texture::class.java.name}")
        }
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
