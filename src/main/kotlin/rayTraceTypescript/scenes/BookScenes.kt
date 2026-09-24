package rayTraceTypescript.scenes

import rayTraceTypescript.Camera
import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.MeshLoader
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.randomWorld
import rayTraceTypescript.textures.CheckerTexture
import rayTraceTypescript.textures.ImageTexture
import rayTraceTypescript.textures.NoiseTexture

/**
 * The scenes from the books, each built the way its chapter describes. Pick one with
 * `-Drt.scene=<name>`; [byName] also accepts the numbers the earlier scene switch used.
 */
object BookScenes {

    private val scenes: Map<String, () -> SceneDefinition> = linkedMapOf(
        "bouncing-spheres" to { bouncingSpheres() },
        "earth" to { earth() },
        "perlin-spheres" to { perlinSpheres() },
        "quads" to { quads() },
        "simple-light" to { simpleLight() },
        "cornell-box" to { cornellBox() },
        "model" to { model() }
    )

    val names: List<String> get() = scenes.keys.toList()

    fun byName(name: String): SceneDefinition {
        val key = when (name.trim().lowercase()) {
            "1" -> "bouncing-spheres"
            "2" -> "earth"
            "3" -> "model"
            else -> name.trim().lowercase()
        }
        val builder = scenes[key]
            ?: throw IllegalArgumentException("Unknown scene '$name'; available: ${names.joinToString()}")
        return builder()
    }

    /** The Next Week, chapter 2: the One Weekend finale with motion blur and a checkered floor. */
    fun bouncingSpheres() = SceneDefinition(
        name = "bouncing-spheres",
        world = HittableList(mutableListOf(BvhNode(randomWorld()))),
        camera = Camera().apply {
            aspectRatio = 16.0f / 9.0f
            imageWidth = 500
            samplesPerPixel = 90
            maxReflectionDepth = 20
            vfov = 20.0f
            lookFrom = Point(13.0f, 2.0f, 3.0f)
            lookAt = Point(0.0f, 0.0f, 0.0f)
            vUp = Vector(0.0f, 1.0f, 0.0f)
            defocusAngle = 0.6f
            focusDistance = 10.0f
        }
    )

    /** The Next Week, chapter 5: two spheres sharing one marble texture built from turbulence. */
    fun perlinSpheres(): SceneDefinition {
        val marble = NoiseTexture(4.0f)
        return SceneDefinition(
            name = "perlin-spheres",
            world = HittableList(
                mutableListOf(
                    Sphere(Point(0f, -1000f, 0f), 1000f, Lambertian(marble)),
                    Sphere(Point(0f, 2f, 0f), 2f, Lambertian(marble))
                )
            ),
            camera = Camera().apply {
                aspectRatio = 16.0f / 9.0f
                imageWidth = 400
                samplesPerPixel = 100
                maxReflectionDepth = 50
                vfov = 20.0f
                lookFrom = Point(13f, 2f, 3f)
                lookAt = Point(0f, 0f, 0f)
                vUp = Vector(0f, 1f, 0f)
                defocusAngle = 0f
            }
        )
    }

    /** The Next Week, chapter 6: five quads seen from inside the box they half enclose. */
    fun quads(): SceneDefinition {
        val red = Lambertian(Color(1.0f, 0.2f, 0.2f))
        val green = Lambertian(Color(0.2f, 1.0f, 0.2f))
        val blue = Lambertian(Color(0.2f, 0.2f, 1.0f))
        val orange = Lambertian(Color(1.0f, 0.5f, 0.0f))
        val teal = Lambertian(Color(0.2f, 0.8f, 0.8f))

        return SceneDefinition(
            name = "quads",
            world = HittableList(
                mutableListOf(
                    Quad(Point(-3f, -2f, 5f), Vector(0f, 0f, -4f), Vector(0f, 4f, 0f), red),
                    Quad(Point(-2f, -2f, 0f), Vector(4f, 0f, 0f), Vector(0f, 4f, 0f), green),
                    Quad(Point(3f, -2f, 1f), Vector(0f, 0f, 4f), Vector(0f, 4f, 0f), blue),
                    Quad(Point(-2f, 3f, 1f), Vector(4f, 0f, 0f), Vector(0f, 0f, 4f), orange),
                    Quad(Point(-2f, -3f, 5f), Vector(4f, 0f, 0f), Vector(0f, 0f, -4f), teal)
                )
            ),
            camera = Camera().apply {
                aspectRatio = 1.0f
                imageWidth = 400
                samplesPerPixel = 100
                maxReflectionDepth = 50
                vfov = 80.0f
                lookFrom = Point(0f, 0f, 9f)
                lookAt = Point(0f, 0f, 0f)
                vUp = Vector(0f, 1f, 0f)
                defocusAngle = 0f
            }
        )
    }

    /** The Next Week, chapter 7: the first scene lit by its own geometry rather than the sky. */
    fun simpleLight(): SceneDefinition {
        val marble = Lambertian(NoiseTexture(4.0f))
        val light = DiffuseLight(Color(4f, 4f, 4f))     // brighter than white, so it lights the room
        return SceneDefinition(
            name = "simple-light",
            world = HittableList(
                mutableListOf(
                    Sphere(Point(0f, -1000f, 0f), 1000f, marble),
                    Sphere(Point(0f, 2f, 0f), 2f, marble),
                    Sphere(Point(0f, 7f, 0f), 2f, light),
                    Quad(Point(3f, 1f, 0f), Vector(2f, 0f, 0f), Vector(0f, 2f, 0f), light)
                )
            ),
            camera = Camera().apply {
                aspectRatio = 16.0f / 9.0f
                imageWidth = 400
                samplesPerPixel = 100
                maxReflectionDepth = 50
                background = Color(0f, 0f, 0f)
                vfov = 20.0f
                lookFrom = Point(26f, 3f, 6f)
                lookAt = Point(0f, 2f, 0f)
                vUp = Vector(0f, 1f, 0f)
                defocusAngle = 0f
            }
        )
    }

    /** The Next Week, chapter 7.4: the Cornell box, empty for now. */
    fun cornellBox(
        contents: List<Hittable> = emptyList(),
        samples: Int = 200,
        width: Int = 600
    ): SceneDefinition {
        val red = Lambertian(Color(0.65f, 0.05f, 0.05f))
        val white = Lambertian(Color(0.73f, 0.73f, 0.73f))
        val green = Lambertian(Color(0.12f, 0.45f, 0.15f))
        val light = DiffuseLight(Color(15f, 15f, 15f))

        val walls = mutableListOf<Hittable>(
            Quad(Point(555f, 0f, 0f), Vector(0f, 555f, 0f), Vector(0f, 0f, 555f), green),
            Quad(Point(0f, 0f, 0f), Vector(0f, 555f, 0f), Vector(0f, 0f, 555f), red),
            Quad(Point(343f, 554f, 332f), Vector(-130f, 0f, 0f), Vector(0f, 0f, -105f), light),
            Quad(Point(0f, 0f, 0f), Vector(555f, 0f, 0f), Vector(0f, 0f, 555f), white),
            Quad(Point(555f, 555f, 555f), Vector(-555f, 0f, 0f), Vector(0f, 0f, -555f), white),
            Quad(Point(0f, 0f, 555f), Vector(555f, 0f, 0f), Vector(0f, 555f, 0f), white)
        )
        walls.addAll(contents)

        return SceneDefinition(
            name = "cornell-box",
            world = HittableList(walls),
            camera = Camera().apply {
                aspectRatio = 1.0f
                imageWidth = width
                samplesPerPixel = samples
                maxReflectionDepth = 50
                background = Color(0f, 0f, 0f)
                vfov = 40.0f
                lookFrom = Point(278f, 278f, -800f)
                lookAt = Point(278f, 278f, 0f)
                vUp = Vector(0f, 1f, 0f)
                defocusAngle = 0f
            }
        )
    }

    /** The Next Week, chapter 4: an image texture wrapped around a globe. */
    fun earth() = SceneDefinition(
        name = "earth",
        world = HittableList(mutableListOf(Sphere(Point(0f, 0f, 0f), 2f, Lambertian(ImageTexture("earthmap.jpg"))))),
        camera = Camera().apply {
            aspectRatio = 16.0f / 9.0f
            imageWidth = 400
            samplesPerPixel = 100
            maxReflectionDepth = 50
            vfov = 20f
            lookFrom = Point(0f, 0f, 12f)
            lookAt = Point(0f, 0f, 0f)
            vUp = Vector(0f, 1f, 0f)
            defocusAngle = 0f
        }
    )

    /**
     * Not from the books: a model file standing on the checkered floor. The path comes from
     * -Drt.model=<path>, the MODEL_FILE environment variable, or ./model.stl.
     */
    fun model(
        path: String = System.getProperty("rt.model")
            ?: System.getProperty("rt.stl")
            ?: System.getenv("MODEL_FILE")
            ?: System.getenv("STL_FILE")
            ?: "model.stl",
        material: Material = Lambertian(Color(0.72f, 0.38f, 0.22f))
    ) = SceneDefinition(
        name = "model",
        world = modelWorld(path, material),
        camera = Camera().apply {
            aspectRatio = 16.0f / 9.0f
            imageWidth = 800
            samplesPerPixel = 150
            maxReflectionDepth = 20
            vfov = 24.0f
            lookFrom = Point(3.2f, 2.2f, 6.0f)
            lookAt = Point(0.0f, 0.9f, 0.0f)
            vUp = Vector(0.0f, 1.0f, 0.0f)
            defocusAngle = 0.2f
            focusDistance = 6.8f
        }
    )

    /**
     * The default model setup: the checkered ground of [bouncingSpheres], with the mesh scaled
     * to a usable size and resting on it. Every facet goes through a BVH, otherwise a mesh of
     * any size would be traversed one triangle at a time.
     */
    fun modelWorld(
        path: String,
        material: Material = Lambertian(Color(0.72f, 0.38f, 0.22f))
    ): HittableList {
        val checker = CheckerTexture(.32F, Color(.2, .3, .1), Color(.9, .9, .9))
        val objects: MutableList<Hittable> = mutableListOf(
            Sphere(Point(0.0f, -1000.0f, 0.0f), 1000.0F, Lambertian(checker))
        )
        val mesh = MeshLoader.load(path).standingOnFloor()
        println("Loaded ${mesh.triangleCount} triangles from $path")
        objects.addAll(mesh.toHittables(material))
        return HittableList(mutableListOf(BvhNode(HittableList(objects))))
    }
}
