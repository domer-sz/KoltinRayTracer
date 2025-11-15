1# Repository Guidelines

## Project Structure & Module Organization
This Maven project keeps code under `src/main/kotlin/rayTraceTypescript`, split into domain-focused packages such as `materials`, `objects`, and `utils`. The entry point is `Main.kt`, which wires up the `Camera`, scene configuration, and invokes `render`, producing `image.ppm` in the repo root. Build artifacts land in `target/`, and `pom.xml` pins Kotlin 2.2.21 plus the Exec plugin that targets `rayTraceTypescript.MainKt`. Keep any future assets (sample renders, textures) in a dedicated top-level folder so they do not pollute `target/`.

## Build, Test, and Development Commands
- `mvn compile` — compiles Kotlin sources with the Kotlin Maven plugin targeting JVM 17.
- `mvn clean package` — recreates the shaded jar under `target/` and is the baseline for CI.
- `mvn exec:java` — runs `MainKt`, generating/overwriting `image.ppm`; commit only curated outputs.
- `mvn test` or `mvn -Dtest=CameraTest test` — executes unit tests (add them under `src/test/kotlin`).

## Coding Style & Naming Conventions
Follow idiomatic Kotlin style: 4-space indentation, `UpperCamelCase` for classes such as `HittableList`, `lowerCamelCase` for functions/properties, and `SCREAMING_SNAKE_CASE` only for constants. Keep packages under `rayTraceTypescript.*` to mirror directories. Favor `val` over `var`, keep mutable lists encapsulated (as in `HittableList`), and document non-obvious math near vector or material logic. Reuse helper extensions (e.g., `Vector.minus`) instead of ad-hoc calculations to preserve consistency.

## Testing Guidelines
Add tests beside the mirrored package in `src/test/kotlin`, using Kotlin Test or JUnit 5 so Maven Surefire can discover them. Name test classes `<Class>Test` and methods `fun rendersBlurredBackground()` so failures read naturally. Test deterministic pieces (vector math, material scatter logic, random helpers with seeded RNG) and gate image regressions by comparing computed pixel statistics rather than raw ppm bytes. Run `mvn test` locally before pushing; target coverage that hits every material branch at least once.

## Commit & Pull Request Guidelines
Git history shows short imperative messages (`add .gitignore`, `finished ray tracing in one weekend`), so keep titles under ~72 characters, starting with a verb (`tune camera depth`). Each PR should describe the scene/camera changes, list commands run (e.g., `mvn clean package`), attach before/after crops of `image.ppm` when visuals change, and link tracking issues. Avoid bundling refactors with feature additions; open follow-up tickets for larger rewrites.

## Rendering Output & Assets
Generated `image.ppm` files can be large; ignore intermediate renders and only commit canonical samples referenced in docs. When tweaking `camera` defaults (aspect ratio, samples, depth), capture the rationale in PR notes so reviewers understand performance vs. fidelity trade-offs. Store environment-specific configs (e.g., higher sample settings) in local profiles rather than changing `Main.kt` defaults.
