package rayTraceTypescript.objects.formats

import rayTraceTypescript.objects.Mesh
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * 3MF - the 3D printing format that replaced STL. The file is a zip whose `3D/3dmodel.model`
 * entry is XML: objects hold a vertex and a triangle list, and the build section places those
 * objects with a 4x3 matrix. Objects assembled from components are read in their own
 * coordinates, since this renderer only needs the geometry.
 */
internal object ThreeMfFormat {

    private class ModelObject {
        val positions = FloatList()
        val faces = mutableListOf<IntArray>()
    }

    fun parse(bytes: ByteArray): Mesh {
        val model = modelEntry(bytes)
        val objects = LinkedHashMap<String, ModelObject>()
        val items = mutableListOf<Pair<String, FloatArray?>>()

        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        val reader: XMLStreamReader = factory.createXMLStreamReader(ByteArrayInputStream(model))
        var current: ModelObject? = null

        while (reader.hasNext()) {
            if (reader.next() != XMLStreamConstants.START_ELEMENT) continue
            when (reader.localName) {
                "object" -> {
                    val id = reader.getAttributeValue(null, "id") ?: objects.size.toString()
                    current = ModelObject().also { objects[id] = it }
                }
                "vertex" -> current?.positions?.add(
                    reader.getAttributeValue(null, "x").toFloat(),
                    reader.getAttributeValue(null, "y").toFloat(),
                    reader.getAttributeValue(null, "z").toFloat()
                )
                "triangle" -> current?.faces?.add(
                    intArrayOf(
                        reader.getAttributeValue(null, "v1").toInt(),
                        reader.getAttributeValue(null, "v2").toInt(),
                        reader.getAttributeValue(null, "v3").toInt()
                    )
                )
                "item" -> {
                    val id = reader.getAttributeValue(null, "objectid") ?: continue
                    items += id to reader.getAttributeValue(null, "transform")?.let(::matrix)
                }
            }
        }
        reader.close()

        val builder = MeshBuilder()
        // Without a build section every object is emitted where it was authored.
        val instances = items.ifEmpty { objects.keys.map { it to null } }
        for ((id, transform) in instances) {
            val target = objects[id] ?: continue
            val placed = if (transform == null) target.positions else transformed(target.positions, transform)
            target.faces.forEach { builder.polygon(placed, it) }
        }

        require(builder.triangleCount > 0) { "3MF file holds no triangles" }
        return builder.build()
    }

    private fun modelEntry(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var best: ByteArray? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.name.lowercase().endsWith(".model")) continue
                val content = zip.readBytes()
                if (entry.name.equals("3D/3dmodel.model", ignoreCase = true)) return content
                if (best == null) best = content
            }
            return best ?: throw IllegalArgumentException("3MF archive has no .model entry")
        }
    }

    /** 3MF stores a 4x3 matrix in row-major order; the last row is the translation. */
    private fun matrix(attribute: String): FloatArray? {
        val values = attribute.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
        return if (values.size == 12) values.toFloatArray() else null
    }

    private fun transformed(positions: FloatList, m: FloatArray): FloatList {
        val moved = FloatList(positions.size)
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            moved.add(
                x * m[0] + y * m[3] + z * m[6] + m[9],
                x * m[1] + y * m[4] + z * m[7] + m[10],
                x * m[2] + y * m[5] + z * m[8] + m[11]
            )
            i += 3
        }
        return moved
    }
}
