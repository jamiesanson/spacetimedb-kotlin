# SpacetimeDB Kotlin SDK — Example

End-to-end example using the SpacetimeDB Kotlin SDK with the Gradle plugin.

This project uses a Gradle **included build** (`includeBuild("../")`) to reference the SDK
directly from source, so there's no need to publish to Maven Local first.

## Prerequisites

- [SpacetimeDB CLI](https://spacetimedb.com) (`spacetime` on your PATH)
- Rust toolchain with `wasm32-unknown-unknown` target (`rustup target add wasm32-unknown-unknown`)
- JDK 17+

## Run

### 1. Start SpacetimeDB locally

```bash
spacetime start
```

### 2. Build the module and publish it

```bash
spacetime publish --skip-clippy -p server example
```

### 3. Run the Kotlin example

```bash
./gradlew run
```

This builds the Rust module, generates typed Kotlin bindings, and runs `Main.kt`
which connects to the local SpacetimeDB instance, adds players, sends messages,
and queries the client cache.

## Project structure

```
example/
├── server/                   # Rust SpacetimeDB module
│   ├── Cargo.toml
│   └── src/lib.rs            # Tables (Player, Message) and reducers
├── build.gradle.kts          # Applies dev.sanson.spacetimedb plugin
├── settings.gradle.kts       # includeBuild("..") for SDK source
└── src/main/kotlin/
    └── com/example/game/
        └── Main.kt           # Kotlin client using generated bindings
```

## What the plugin does

When you run `./gradlew run`, the `dev.sanson.spacetimedb` Gradle plugin:

1. **Builds** the Rust module (`spacetime build -p server`)
2. **Extracts** the schema from the compiled WASM
3. **Generates** typed Kotlin bindings into `build/generated/spacetimedb/kotlin/`:
   - `Player.kt`, `Message.kt` — `@Serializable` row types
   - `PlayerTableHandle.kt`, `MessageTableHandle.kt` — typed table access
   - `RemoteTables.kt`, `RemoteReducers.kt` — aggregate interfaces
   - `DbConnection.kt`, `DbConnectionBuilder.kt` — connection entry point
4. **Compiles** everything together with `spacetimedb-core` on the classpath
