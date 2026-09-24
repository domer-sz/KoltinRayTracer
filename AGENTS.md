1# Repository Guidelines

## Project Structure & Module Organization
This Maven project keeps code under `src/main/kotlin/rayTraceTypescript`, split into domain-focused packages such as `materials`, `objects`, and `utils`. The entry point is `Main.kt`, which wires up the `Camera`, scene configuration, and invokes `render`, producing `image.ppm` in the repo root. Build artifacts land in `target/`, and `pom.xml` pins Kotlin 2.2.21 plus the Exec plugin that targets `rayTraceTypescript.MainKt`. Keep any future assets (sample renders, textures) in a dedicated top-level folder so they do not pollute `target/`.

## Rendering Backends
Ray tracing runs on the GPU through OpenCL (LWJGL bindings, kernel in `src/main/resources/kernels/raytracer.cl`). `Camera.initialize()` produces the shared `CameraSetup`, `gpu/SceneFlattener` lays the scene out as flat buffers, and the kernel does sampling, BVH traversal, materials and textures; the CPU only writes the PPM through `render/PpmWriter`. `-Drt.renderer=cpu` forces the reference CPU path (`render/CpuRenderer`), `-Drt.renderer=gpu` forces OpenCL and fails loudly, and the default `auto` falls back to the CPU when no device or driver is present. `RT_OPENCL_DEVICE=<substring>` picks a specific device by name; without it the first GPU wins. Because the GPU draws its random numbers per pixel and sample, renders match the CPU in geometry, shading and brightness but carry a different noise pattern - compare images statistically (as `GpuCpuComparisonTest` does), never byte for byte.

## Meshes and 3D File Formats
`objects/MeshLoader` reads a model file into a `Mesh` - a flat triangle soup - picking a parser in `objects/formats` by extension: STL (binary and ASCII, told apart by file size rather than the `solid` keyword), OBJ, PLY (ASCII and both binary byte orders), OFF, glTF 2.0 (`.gltf` with external or data-URI buffers, and packed `.glb`), and 3MF. Only geometry is read; materials come from the scene. Faces with more than three corners are fanned into triangles, glTF node transforms are baked into world space, and glTF needs a JSON reader that `utils/Json` provides, because the project has no JSON dependency. `Mesh.toHittables` turns each facet into a `Triangle` - Moller-Trumbore intersection, barycentric u/v, and a normal taken from the winding rather than from the file, since mesh files frequently carry wrong ones.

Model files agree on no units or origin, so `standingOnFloor()` is the default placement: the model is centred over the origin in x/z, rests on y=0 - the top of the ground sphere every scene uses - and is scaled to `DEFAULT_TARGET_HEIGHT`; pass `targetHeight = null` to keep the authored size. `modelWorld(path, material)` in `Main.kt` assembles that default scene (checkered ground + model) and always wraps it in a `BvhNode`. Run it with `mvn exec:java -Drt.scene=3 -Drt.model=<path>`; `-Drt.scene` picks 1 spheres, 2 earth (default), 3 a model, and the path also comes from `MODEL_FILE` or `./model.stl`. Triangles get their own leaf kind in the GPU buffers (`SceneBuffers.LEAF_TRIANGLE`) and their bounding boxes are padded through `Aabb.padded()`, since a facet in an axis-aligned plane would otherwise have a box no ray can enter.

## Build, Test, and Development Commands
- `mvn compile` — compiles Kotlin sources with the Kotlin Maven plugin targeting JVM 17.
- `mvn clean package` — recreates the shaded jar under `target/` and is the baseline for CI.
- `mvn exec:java` — runs `MainKt`, generating/overwriting `image.ppm`; commit only curated outputs.
- `mvn test` or `mvn -Dtest=CameraTest test` — executes unit tests (add them under `src/test/kotlin`).
- `GpuCpuComparisonTest` needs an OpenCL device and skips itself without one; `pocl-opencl-icd` provides a CPU device for machines with no GPU.

## Coding Style & Naming Conventions
Follow idiomatic Kotlin style: 4-space indentation, `UpperCamelCase` for classes such as `HittableList`, `lowerCamelCase` for functions/properties, and `SCREAMING_SNAKE_CASE` only for constants. Keep packages under `rayTraceTypescript.*` to mirror directories. Favor `val` over `var`, keep mutable lists encapsulated (as in `HittableList`), and document non-obvious math near vector or material logic. Reuse helper extensions (e.g., `Vector.minus`) instead of ad-hoc calculations to preserve consistency.

## Testing Guidelines
Add tests beside the mirrored package in `src/test/kotlin`, using Kotlin Test or JUnit 5 so Maven Surefire can discover them. Name test classes `<Class>Test` and methods `fun rendersBlurredBackground()` so failures read naturally. Test deterministic pieces (vector math, material scatter logic, random helpers with seeded RNG) and gate image regressions by comparing computed pixel statistics rather than raw ppm bytes. Run `mvn test` locally before pushing; target coverage that hits every material branch at least once.

## Commit & Pull Request Guidelines
Git history shows short imperative messages (`add .gitignore`, `finished ray tracing in one weekend`), so keep titles under ~72 characters, starting with a verb (`tune camera depth`). Each PR should describe the scene/camera changes, list commands run (e.g., `mvn clean package`), attach before/after crops of `image.ppm` when visuals change, and link tracking issues. Avoid bundling refactors with feature additions; open follow-up tickets for larger rewrites.

## Rendering Output & Assets
Generated `image.ppm` files can be large; ignore intermediate renders and only commit canonical samples referenced in docs. When tweaking `camera` defaults (aspect ratio, samples, depth), capture the rationale in PR notes so reviewers understand performance vs. fidelity trade-offs. Store environment-specific configs (e.g., higher sample settings) in local profiles rather than changing `Main.kt` defaults.
