package rayTraceTypescript.gpu

/**
 * The scene, flattened into primitive arrays the OpenCL kernel can read.
 *
 * Layout (see raytracer.cl, which mirrors these strides):
 *  - [nodeBounds]: 6 floats per node - min xyz, max xyz
 *  - [nodeLinks]: 2 ints per node - inner node: child node indices; leaf: the primitive index
 *    plus [LEAF_SPHERE] or [LEAF_TRIANGLE]
 *  - [spheres]: 8 floats per sphere - center at t=0 (3), center movement (3), radius, unused
 *  - [triangles]: 12 floats per facet - first vertex (3), two edges (3 + 3), unused (3)
 *  - [quads]: 16 floats - corner (3), the two edges (3 + 3), the basis vector w (3),
 *    the plane normal (3) and its offset
 *  - [materialInts]: 2 ints per material - type (0 Lambertian, 1 Metal, 2 Dielectric,
 *    3 DiffuseLight, 4 Isotropic), texture index
 *  - [mediumInts] / [mediumFloats]: per volume - the node its boundary starts at, the phase
 *    function's material, and the negated inverse density
 *  - [lights]: 2 ints per light - which primitive buffer it lives in and its index there
 *  - [materialFloats]: 2 floats per material - fuzz, refraction index
 *  - [textureInts]: 4 ints per texture - type (0 solid, 1 checker, 2 image, 3 missing image) + payload
 *  - [textureFloats]: 4 floats per texture - solid rgb, the checker's inverted scale, or a
 *    noise texture's scale
 *  - [perlinVectors] / [perlinPermutations]: one 256-entry lattice per Perlin instance, copied
 *    from the CPU tables so both renderers sample the same noise field
 */
class SceneBuffers(
    val nodeBounds: FloatArray,
    val nodeLinks: IntArray,
    val spheres: FloatArray,
    val sphereMaterials: IntArray,
    val triangles: FloatArray,
    val triangleMaterials: IntArray,
    val quads: FloatArray,
    val quadMaterials: IntArray,
    val mediumInts: IntArray,
    val mediumFloats: FloatArray,
    val lights: IntArray,
    val materialInts: IntArray,
    val materialFloats: FloatArray,
    val textureInts: IntArray,
    val textureFloats: FloatArray,
    val textureImages: ByteArray,
    val perlinVectors: FloatArray,
    val perlinPermutations: IntArray,
    val rootNode: Int,
    val maxDepth: Int
) {
    val nodeCount: Int get() = nodeLinks.size / NODE_LINK_STRIDE
    val sphereCount: Int get() = sphereMaterials.size
    val triangleCount: Int get() = triangleMaterials.size
    val quadCount: Int get() = quadMaterials.size
    val mediumCount: Int get() = mediumFloats.size
    val lightCount: Int get() = lights.size / 2

    companion object {
        const val NODE_BOUNDS_STRIDE = 6
        const val NODE_LINK_STRIDE = 2
        const val SPHERE_STRIDE = 8
        const val TRIANGLE_STRIDE = 12
        const val QUAD_STRIDE = 16
        const val MEDIUM_INT_STRIDE = 2
        const val MATERIAL_INT_STRIDE = 2
        const val MATERIAL_FLOAT_STRIDE = 2
        const val TEXTURE_INT_STRIDE = 4
        const val TEXTURE_FLOAT_STRIDE = 4

        /** 256 lattice vectors of x/y/z, and three 256-entry permutations, per Perlin instance. */
        const val PERLIN_BLOCK = 768

        /** Marks a node as a leaf; its first link is then an index into that primitive's buffer. */
        const val LEAF_SPHERE = -1
        const val LEAF_TRIANGLE = -2
        const val LEAF_QUAD = -3
        const val LEAF_MEDIUM = -4

        /** Depth of the traversal stack reserved in the kernel. */
        const val MAX_TRAVERSAL_DEPTH = 64
    }
}
