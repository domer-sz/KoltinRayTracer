package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh
import rayTraceTypescript.utils.Json
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * glTF 2.0, both the JSON flavour (.gltf, with buffers next to it or inlined as data URIs) and
 * the packed one (.glb). Only geometry is read: every triangle primitive reachable from the
 * scene graph, baked into world space with its node transforms, because a mesh here is a plain
 * triangle soup with one material assigned by the scene.
 */
internal object GltfFormat {

    private const val GLB_MAGIC = 0x46546C67            // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942
    private const val MODE_TRIANGLES = 4

    fun parse(bytes: ByteArray, baseDir: Path?): Mesh {
        val (json, binaryChunk) = if (isGlb(bytes)) readGlb(bytes)
        else Json.obj(Json.parse(bytes.toString(StandardCharsets.UTF_8))) to null

        requireNotNull(json) { "glTF file has no top level object" }
        val buffers = resolveBuffers(json, binaryChunk, baseDir)
        val builder = MeshBuilder()

        val nodes = Json.array(json["nodes"])
        val roots = sceneRoots(json, nodes)
        if (roots != null && nodes != null) {
            roots.forEach { emitNode(it, IDENTITY, json, nodes, buffers, builder) }
        } else {
            // No scene graph: fall back to the meshes as authored.
            Json.array(json["meshes"])?.indices?.forEach { emitMesh(it, IDENTITY, json, buffers, builder) }
        }

        require(builder.triangleCount > 0) { "glTF file holds no triangle geometry" }
        return builder.build()
    }

    private fun isGlb(bytes: ByteArray): Boolean =
        bytes.size >= 12 && ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int == GLB_MAGIC

    private fun readGlb(bytes: ByteArray): Pair<Map<String, Any?>?, ByteArray?> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)                              // magic, version, total length
        var json: Map<String, Any?>? = null
        var binary: ByteArray? = null
        while (buffer.remaining() >= 8) {
            val length = buffer.int
            val type = buffer.int
            val chunk = ByteArray(length)
            buffer.get(chunk)
            when (type) {
                CHUNK_JSON -> json = Json.obj(Json.parse(chunk.toString(StandardCharsets.UTF_8)))
                CHUNK_BIN -> binary = chunk
            }
        }
        return json to binary
    }

    private fun resolveBuffers(json: Map<String, Any?>, binaryChunk: ByteArray?, baseDir: Path?): List<ByteArray> =
        Json.array(json["buffers"]).orEmpty().map { entry ->
            val uri = Json.obj(entry)?.get("uri") as? String
            when {
                uri == null -> binaryChunk ?: throw IllegalArgumentException("glTF buffer without a uri or binary chunk")
                uri.startsWith("data:") -> Base64.getDecoder().decode(uri.substringAfter("base64,"))
                else -> {
                    val relative = URLDecoder.decode(uri, StandardCharsets.UTF_8)
                    val file = (baseDir ?: Path.of(".")).resolve(relative)
                    require(Files.isRegularFile(file)) { "glTF buffer file not found: $file" }
                    Files.readAllBytes(file)
                }
            }
        }

    private fun sceneRoots(json: Map<String, Any?>, nodes: List<Any?>?): List<Int>? {
        if (nodes == null) return null
        val scenes = Json.array(json["scenes"]) ?: return nodes.indices.toList()
        val index = Json.int(json["scene"]) ?: 0
        val scene = Json.obj(scenes.getOrNull(index) ?: scenes.firstOrNull()) ?: return nodes.indices.toList()
        return Json.array(scene["nodes"])?.mapNotNull { Json.int(it) } ?: nodes.indices.toList()
    }

    private fun emitNode(
        index: Int,
        parent: FloatArray,
        json: Map<String, Any?>,
        nodes: List<Any?>,
        buffers: List<ByteArray>,
        builder: MeshBuilder
    ) {
        val node = Json.obj(nodes.getOrNull(index)) ?: return
        val world = multiply(parent, localMatrix(node))
        Json.int(node["mesh"])?.let { emitMesh(it, world, json, buffers, builder) }
        Json.array(node["children"])?.forEach { child ->
            Json.int(child)?.let { emitNode(it, world, json, nodes, buffers, builder) }
        }
    }

    private fun emitMesh(
        index: Int,
        world: FloatArray,
        json: Map<String, Any?>,
        buffers: List<ByteArray>,
        builder: MeshBuilder
    ) {
        val mesh = Json.obj(Json.array(json["meshes"])?.getOrNull(index)) ?: return
        for (entry in Json.array(mesh["primitives"]).orEmpty()) {
            val primitive = Json.obj(entry) ?: continue
            if ((Json.int(primitive["mode"]) ?: MODE_TRIANGLES) != MODE_TRIANGLES) continue

            val positionAccessor = Json.int(Json.obj(primitive["attributes"])?.get("POSITION")) ?: continue
            val raw = readAccessor(positionAccessor, json, buffers)
            val positions = FloatList(raw.size)
            var i = 0
            while (i + 2 < raw.size) {
                val x = raw[i]; val y = raw[i + 1]; val z = raw[i + 2]
                positions.add(
                    world[0] * x + world[4] * y + world[8] * z + world[12],
                    world[1] * x + world[5] * y + world[9] * z + world[13],
                    world[2] * x + world[6] * y + world[10] * z + world[14]
                )
                i += 3
            }

            val indices = Json.int(primitive["indices"])
                ?.let { accessor -> readAccessor(accessor, json, buffers).map { it.toInt() } }
                ?: (0 until positions.tripleCount).toList()

            var corner = 0
            while (corner + 2 < indices.size) {
                builder.polygon(positions, intArrayOf(indices[corner], indices[corner + 1], indices[corner + 2]))
                corner += 3
            }
        }
    }

    /** Component values of one accessor, flattened; enough for VEC3 positions and scalar indices. */
    private fun readAccessor(index: Int, json: Map<String, Any?>, buffers: List<ByteArray>): FloatArray {
        val accessor = Json.obj(Json.array(json["accessors"])?.getOrNull(index))
            ?: throw IllegalArgumentException("glTF accessor $index is missing")
        val count = Json.int(accessor["count"]) ?: 0
        val componentType = Json.int(accessor["componentType"]) ?: 5126
        val components = when (val type = accessor["type"] as? String) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            else -> throw IllegalArgumentException("Unsupported accessor type '$type'")
        }
        val componentSize = when (componentType) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            5125, 5126 -> 4
            else -> throw IllegalArgumentException("Unsupported component type $componentType")
        }

        val view = Json.obj(Json.array(json["bufferViews"])?.getOrNull(Json.int(accessor["bufferView"]) ?: -1))
            ?: return FloatArray(count * components)      // sparse or zero-filled accessor
        val buffer = buffers[Json.int(view["buffer"]) ?: 0]
        val viewOffset = Json.int(view["byteOffset"]) ?: 0
        val accessorOffset = Json.int(accessor["byteOffset"]) ?: 0
        val stride = (Json.int(view["byteStride"]) ?: 0).takeIf { it > 0 } ?: (components * componentSize)

        val data = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val values = FloatArray(count * components)
        for (element in 0 until count) {
            val base = viewOffset + accessorOffset + element * stride
            for (component in 0 until components) {
                val at = base + component * componentSize
                values[element * components + component] = when (componentType) {
                    5120 -> data.get(at).toFloat()
                    5121 -> (data.get(at).toInt() and 0xFF).toFloat()
                    5122 -> data.getShort(at).toFloat()
                    5123 -> (data.getShort(at).toInt() and 0xFFFF).toFloat()
                    5125 -> (data.getInt(at).toLong() and 0xFFFFFFFFL).toFloat()
                    else -> data.getFloat(at)
                }
            }
        }
        return values
    }

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /** glTF stores matrices column-major, or translation/rotation/scale to be combined as T*R*S. */
    private fun localMatrix(node: Map<String, Any?>): FloatArray {
        Json.array(node["matrix"])?.let { values ->
            return FloatArray(16) { (values[it] as Double).toFloat() }
        }

        val translation = Json.array(node["translation"])?.map { (it as Double).toFloat() } ?: listOf(0f, 0f, 0f)
        val rotation = Json.array(node["rotation"])?.map { (it as Double).toFloat() } ?: listOf(0f, 0f, 0f, 1f)
        val scale = Json.array(node["scale"])?.map { (it as Double).toFloat() } ?: listOf(1f, 1f, 1f)

        val (x, y, z, w) = listOf(rotation[0], rotation[1], rotation[2], rotation[3])
        val matrix = floatArrayOf(
            (1 - 2 * (y * y + z * z)) * scale[0], (2 * (x * y + z * w)) * scale[0], (2 * (x * z - y * w)) * scale[0], 0f,
            (2 * (x * y - z * w)) * scale[1], (1 - 2 * (x * x + z * z)) * scale[1], (2 * (y * z + x * w)) * scale[1], 0f,
            (2 * (x * z + y * w)) * scale[2], (2 * (y * z - x * w)) * scale[2], (1 - 2 * (x * x + y * y)) * scale[2], 0f,
            translation[0], translation[1], translation[2], 1f
        )
        return matrix
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[column * 4 + k]
                result[column * 4 + row] = sum
            }
        }
        return result
    }
}
