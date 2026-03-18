# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin` holds the plugin code: `widget` renders the status-bar widget, `settings` backs the configuration UI/state, and `services` contains the Open-Meteo client plus helpers (weather codes, city list, utilities).
- `src/main/resources/META-INF` contains `plugin.xml` (extension points) and supporting icons; keep new resources alongside these entries.
- Tests live in `src/test/kotlin`; tests mirror the package structure (`services`) and rely on Kotlin’s standard `kotlin.test` plus the IntelliJ test harness.
- Gradle metadata, publishing, and wrapper lives at the repository root (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew*`).

## Build, Test, and Development Commands
- `./gradlew build`: compiles Kotlin, instruments the plugin, and bundles jars needed for release. Run before tagging a new version.
- `./gradlew test`: executes Kotlin smoke tests via JUnit Platform; ensure this succeeds locally before opening a PR.
- `./gradlew runIde`: launches a sandbox IDE instance for manual UI verification (useful when adding widget behavior).
- `./gradlew publishPlugin`: kept for future releases; only run when publishing after bumping `version` in `build.gradle.kts`.

## Coding Style & Naming Conventions
- Follow Kotlin’s official code style (4-space indent, expressive names, `val` over `var` whenever possible).
- Keep packages lower-case and aligned with directory paths; class names are `CamelCase`, functions `lowerCamelCase`, and constants UPPER_SNAKE_CASE only when truly constant.
- Prefer the Kotlin standard library (`buildString`, scopes, `data class`) instead of manual string concatenation or mutable collections.
- Keep UI logic in the widget package and service/state logic separate; use descriptive names such as `WeatherWidgetSettingsState` or `WeatherService`.

## Testing Guidelines
- Add tests under `src/test/kotlin` using `kotlin.test.Test`; they execute through Gradle’s JUnit Platform runner with the IntelliJ test environment.
- Name suites after the class under test, e.g., `WeatherCodeTest` or `CitiesDataTest`.
- Include assertions for both success paths and fallbacks. Run `./gradlew test` before pushing.

## Commit & Pull Request Guidelines
- Write commit messages in the imperative: `Add smoke test for city lookup`, `Fix widget paint disposal`.
- Each pull request should describe the change, mention the commands you ran (especially `./gradlew test`), and link to any relevant issue/bug report.
- Include before/after context when UI behavior changed (screenshots not required unless APIs were altered).

## Configuration & Secrets
- The widget uses Open-Meteo coordinates stored in `WeatherWidgetSettingsState`; defaults point to London. Avoid committing API secrets—none exist here, but treat any future tokens as sensitive.
