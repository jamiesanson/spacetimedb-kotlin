# Copilot Instructions

## Public API Conventions

- **No sealed classes in public API.** Use `abstract class` with a `private constructor()` instead. Sealed classes break consumers when variants are added or removed.
- **No data classes in public API.** Use `@Poko` instead. Data classes expose `copy()` and `componentN()` which are problematic for binary compatibility.
- **Parameterless subtypes** of abstract hierarchies should be `data object`s, not classes with no-arg constructors.
- **Single-parameter wrapper types** should use `@JvmInline value class` (with `import kotlin.jvm.JvmInline` in common code). If the wrapped type is already `@Serializable`, the custom serializer can be dropped.

## Kotlin Multiplatform

- **`@JvmInline`** requires an explicit `import kotlin.jvm.JvmInline` in KMP common source sets — it is not auto-imported on non-JVM targets.
- **Native test naming**: Kotlin Native targets don't support commas in backtick test names — use "and" instead.
- **Gradle config cache**: After renaming modules, delete `.gradle/` to clear stale configuration cache.
- **API compatibility**: Run `./gradlew :module:apiDump` after any public API change, then `./gradlew check` to verify.

## PR Review Comments

- When replying to PR review comment threads, prefix the response with "Copilot:".
