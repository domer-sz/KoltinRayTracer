package rayTraceTypescript.gpu

/**
 * The scene, flattened into primitive arrays the OpenCL kernel can read.
 *
 * Layout (see raytracer.cl, which mirrors these strides):
 *  - [nodeBounds]: 6 floats per node - min xyz, max xyz
 *  - [nodeLinks]: 2 ints per node - inner node: child node indices; leaf: (sphere index, [LEAF])
 *  - [spheres]: 8 floats per sphere - center at t=0 (3), center movement (3), radius, unused
 *  - [materialInts]: 2 ints per material - type (0 Lambertian, 1 Metal, 2 Dielectric), texture index
 *  - [materialFloats]: 2 floats per material - fuzz, refraction index
 *  - [textureInts]: 4 ints per texture - type (0 solid, 1 checker, 2 image, 3 missing image) + payload
 *  - [textureFloats]: 4 floats per texture - solid rgb, or the checker's inverted scale
 */
class SceneBuffers(
    val nodeBounds: FloatArray,
    val nodeLinks: IntArray,
    val spheres: FloatArray,
    val sphereMaterials: IntArray,
    val materialInts: IntArray,
    val materialFloats: FloatArray,
    val textureInts: IntArray,
    val textureFloats: FloatArray,
    val textureImages: ByteArray,
    val rootNode: Int,
    val maxDepth: Int
) {
    val nodeCount: Int get() = nodeLinks.size / NODE_LINK_STRIDE
    val sphereCount: Int get() = sphereMaterials.size

    companion object {
        const val NODE_BOUNDS_STRIDE = 6
        const val NODE_LINK_STRIDE = 2
        const val SPHERE_STRIDE = 8
        const val MATERIAL_INT_STRIDE = 2
        const val MATERIAL_FLOAT_STRIDE = 2
        const val TEXTURE_INT_STRIDE = 4
        const val TEXTURE_FLOAT_STRIDE = 4

        /** Marks a node as a leaf; its first link is then a sphere index. */
        const val LEAF = -1

        /** Depth of the traversal stack reserved in the kernel. */
        const val MAX_TRAVERSAL_DEPTH = 64
    }
}
