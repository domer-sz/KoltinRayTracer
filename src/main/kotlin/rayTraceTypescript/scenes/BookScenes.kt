package rayTraceTypescript.scenes

import rayTraceTypescript.Camera
import rayTraceTypescript.Color
import rayTraceTypescript.Point
import rayTraceTypescript.Vector
import rayTraceTypescript.materials.Dielectric
import rayTraceTypescript.materials.DiffuseLight
import rayTraceTypescript.materials.Metal
import rayTraceTypescript.materials.Lambertian
import rayTraceTypescript.materials.Material
import rayTraceTypescript.objects.BvhNode
import rayTraceTypescript.objects.HittableList
import rayTraceTypescript.objects.Hittable
import rayTraceTypescript.objects.MeshLoader
import rayTraceTypescript.objects.ConstantMedium
import rayTraceTypescript.objects.Quad
import rayTraceTypescript.objects.RotateY
import rayTraceTypescript.objects.Translate
import rayTraceTypescript.objects.box
import rayTraceTypescript.objects.Sphere
import rayTraceTypescript.randomWorld
import rayTraceTypescript.utils.randomFloat
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
        "cornell-blocks" to { cornellBlocks() },
        "cornell-smoke" to { cornellSmoke() },
        "final-week" to { finalWeek() },
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

    /** The Next Week, chapter 8.2: the Cornell box with two rotated, translated blocks. */
    fun cornellBlocks(samples: Int = 200, width: Int = 600): SceneDefinition {
        val white = Lambertian(Color(0.73f, 0.73f, 0.73f))
        val tall = Translate(
            RotateY(box(Point(0f, 0f, 0f), Point(165f, 330f, 165f), white), 15.0f),
            Vector(265f, 0f, 295f)
        )
        val short = Translate(
            RotateY(box(Point(0f, 0f, 0f), Point(165f, 165f, 165f), white), -18.0f),
            Vector(130f, 0f, 65f)
        )
        return SceneDefinition(
            name = "cornell-blocks",
            world = cornellBox(listOf(tall, short), samples, width).world,
            camera = cornellBox(listOf(), samples, width).camera
        )
    }

    /** The Next Week, chapter 9.2: the same room, with the blocks replaced by smoke. */
    fun cornellSmoke(samples: Int = 200, width: Int = 600): SceneDefinition {
        val red = Lambertian(Color(0.65f, 0.05f, 0.05f))
        val white = Lambertian(Color(0.73f, 0.73f, 0.73f))
        val green = Lambertian(Color(0.12f, 0.45f, 0.15f))
        val light = DiffuseLight(Color(7f, 7f, 7f))

        val tall = Translate(
            RotateY(box(Point(0f, 0f, 0f), Point(165f, 330f, 165f), white), 15.0f),
            Vector(265f, 0f, 295f)
        )
        val short = Translate(
            RotateY(box(Point(0f, 0f, 0f), Point(165f, 165f, 165f), white), -18.0f),
            Vector(130f, 0f, 65f)
        )

        return SceneDefinition(
            name = "cornell-smoke",
            world = HittableList(
                mutableListOf(
                    Quad(Point(555f, 0f, 0f), Vector(0f, 555f, 0f), Vector(0f, 0f, 555f), green),
                    Quad(Point(0f, 0f, 0f), Vector(0f, 555f, 0f), Vector(0f, 0f, 555f), red),
                    // A wider, dimmer lamp than the solid box scene uses.
                    Quad(Point(113f, 554f, 127f), Vector(330f, 0f, 0f), Vector(0f, 0f, 305f), light),
                    Quad(Point(0f, 555f, 0f), Vector(555f, 0f, 0f), Vector(0f, 0f, 555f), white),
                    Quad(Point(0f, 0f, 0f), Vector(555f, 0f, 0f), Vector(0f, 0f, 555f), white),
                    Quad(Point(0f, 0f, 555f), Vector(555f, 0f, 0f), Vector(0f, 555f, 0f), white),
                    ConstantMedium(tall, 0.01f, Color(0f, 0f, 0f)),
                    ConstantMedium(short, 0.01f, Color(1f, 1f, 1f))
                )
            ),
            camera = cornellBox(listOf(), samples, width).camera
        )
    }

    /**
     * The Next Week, chapter 10: every feature of the book in one room - a floor of boxes of
     * random height, a moving sphere, glass, metal, a glass ball filled with blue fog, a thin
     * white haze over the whole scene, the earth, marble, and a block of a thousand spheres
     * rotated into place.
     *
     * The book renders this at 10000 samples; the default here is what a machine can finish in
     * a sitting. Without ./earthmap.jpg (which this repository does not carry) the globe falls
     * back to the cyan ImageTexture uses for a missing file.
     */
    fun finalWeek(samples: Int = 400, width: Int = 600): SceneDefinition {
        val ground = Lambertian(Color(0.48f, 0.83f, 0.53f))
        val floorBoxes = mutableListOf<Hittable>()
        val boxesPerSide = 20
        for (i in 0 until boxesPerSide) {
            for (j in 0 until boxesPerSide) {
                val w = 100.0f
                val x0 = -1000.0f + i * w
                val z0 = -1000.0f + j * w
                val y1 = randomFloat(1.0f, 101.0f)
                floorBoxes.add(box(Point(x0, 0.0f, z0), Point(x0 + w, y1, z0 + w), ground))
            }
        }

        val spheresInBlock = mutableListOf<Hittable>()
        val white = Lambertian(Color(0.73f, 0.73f, 0.73f))
        repeat(1000) {
            spheresInBlock.add(Sphere(Vector.random(0.0f, 165.0f).toPoint(), 10.0f, white))
        }

        val blueFogBoundary = Sphere(Point(360f, 150f, 145f), 70f, Dielectric(1.5f))
        val hazeBoundary = Sphere(Point(0f, 0f, 0f), 5000f, Dielectric(1.5f))

        val world = HittableList(
            mutableListOf(
                BvhNode(HittableList(floorBoxes)),
                Quad(Point(123f, 554f, 147f), Vector(300f, 0f, 0f), Vector(0f, 0f, 265f), DiffuseLight(Color(7f, 7f, 7f))),
                Sphere(Point(400f, 400f, 200f), Point(430f, 400f, 200f), 50f, Lambertian(Color(0.7f, 0.3f, 0.1f))),
                Sphere(Point(260f, 150f, 45f), 50f, Dielectric(1.5f)),
                Sphere(Point(0f, 150f, 145f), 50f, Metal(Color(0.8f, 0.8f, 0.9f), 1.0f)),
                blueFogBoundary,
                ConstantMedium(blueFogBoundary, 0.2f, Color(0.2f, 0.4f, 0.9f)),
                ConstantMedium(hazeBoundary, 0.0001f, Color(1f, 1f, 1f)),
                Sphere(Point(400f, 200f, 400f), 100f, Lambertian(ImageTexture("earthmap.jpg"))),
                Sphere(Point(220f, 280f, 300f), 80f, Lambertian(NoiseTexture(0.2f))),
                Translate(RotateY(BvhNode(HittableList(spheresInBlock)), 15.0f), Vector(-100f, 270f, 395f))
            )
        )

        return SceneDefinition(
            name = "final-week",
            world = world,
            camera = Camera().apply {
                aspectRatio = 1.0f
                imageWidth = width
                samplesPerPixel = samples
                maxReflectionDepth = 40
                background = Color(0f, 0f, 0f)
                vfov = 40.0f
                lookFrom = Point(478f, 278f, -600f)
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
