# SpacetimeDB Kotlin Multiplatform Client SDK

## Problem Statement

Implement a Kotlin Multiplatform (KMP) client SDK for SpacetimeDB, targeting JVM + JS + Native. The SDK should provide the same functionality as the existing Rust SDK, with idiomatic Kotlin APIs (coroutines/Flows), and pass equivalent integration tests.

## Design Decisions

- **Targets**: JVM + JS + Native (full KMP via Ktor Client)
- **API style**: Coroutine-first with Flows; callback adapters for compat
- **Code generation**: Standalone CLI tool (like `spacetime generate`)
- **Serialization**: kotlinx.serialization with custom BSATN format
- **Build**: Gradle multi-module based on jamiesanson/gradle-template conventions
- **Group ID**: `dev.sanson.spacetimedb`
- **Modules**: `spacetimedb-bsatn`, `spacetimedb-core`, `spacetimedb-codegen` (+ test modules later)
- **Publishing**: Local only for now

### Public API Conventions

- **No sealed classes in public API.** Use `abstract class` with `private constructor()`. Sealed classes break consumers when variants change.
- **No data classes in public API.** Use `@Poko` instead — data classes expose `copy()` and `componentN()` which are problematic for binary compatibility.
- **Parameterless subtypes** of abstract hierarchies should be `data object`s, not classes with no-arg constructors.
- **Single-parameter wrapper types** should use `@JvmInline value class`. If the wrapped type is already `@Serializable`, no custom serializer is needed.
- **Prefer kotlin.time types** — use `kotlin.time.Instant` and `kotlin.time.Duration` over raw Long wrappers for time values.

## Architecture Overview

```
spacetimedb-kotlin/
├── gradle/
│   ├── libs.versions.toml          # Version catalog
│   └── plugins/                    # Convention plugins
├── spacetimedb-bsatn/              # BSATN serialization format
│   └── src/
│       ├── commonMain/kotlin/dev/sanson/spacetimedb/bsatn/
│       │   ├── Bsatn.kt           # BinaryFormat implementation
│       │   ├── BsatnEncoder.kt    # Serializer
│       │   ├── BsatnDecoder.kt    # Deserializer
│       │   └── BigIntegers.kt     # u128, i128, u256, i256
│       └── commonTest/
├── spacetimedb-core/               # Core SDK (types, cache, transport, connection)
│   └── src/
│       ├── commonMain/kotlin/dev/sanson/spacetimedb/
│       │   ├── DbContext.kt        # Main interface
│       │   ├── DbConnectionBuilder.kt
│       │   ├── Identity.kt, ConnectionId.kt, Timestamp.kt, etc.
│       │   ├── Event.kt           # Event sealed class
│       │   ├── SpacetimeError.kt   # Error types
│       │   ├── Table.kt           # Table/TableWithPrimaryKey interfaces
│       │   ├── Subscription.kt    # SubscriptionBuilder, SubscriptionHandle
│       │   ├── ClientCache.kt     # Client-side row cache
│       │   ├── Credentials.kt     # Token persistence
│       │   ├── Compression.kt     # Brotli/gzip decompression
│       │   └── internal/
│       │       ├── WebSocket.kt   # WebSocket transport (Ktor)
│       │       ├── Protocol.kt    # WS v2 message types
│       │       └── Callbacks.kt   # Callback management
│       └── commonTest/
├── spacetimedb-codegen/            # CLI code generator (JVM-only)
│   └── src/main/kotlin/dev/sanson/spacetimedb/codegen/
├── settings.gradle.kts
└── gradle.properties
```

## BSATN Format Specification (from Rust `spacetimedb-sats`)

The wire format is:
- **bool**: 1 byte (0 = false, 1 = true)
- **u8/i8**: 1 byte
- **u16/i16**: 2 bytes, little-endian
- **u32/i32**: 4 bytes, little-endian
- **u64/i64**: 8 bytes, little-endian
- **u128/i128**: 16 bytes, little-endian
- **u256/i256**: 32 bytes, little-endian
- **f32**: 4 bytes as u32 bit pattern, little-endian
- **f64**: 8 bytes as u64 bit pattern, little-endian
- **String**: u32LE length prefix + UTF-8 bytes
- **ByteArray**: u32LE length prefix + raw bytes
- **Array/List**: u32LE element count + each element encoded
- **Product (struct/data class)**: fields concatenated in order, no prefix
- **Sum (sealed class/enum)**: u8 tag + variant payload
- **Option<T>**: sum type — tag 0 = Some(value), tag 1 = None

## Implementation Phases

### Phase 1: BSATN ✅

- [x] Project scaffolding
- [x] BSATN types (U128, I128, U256, I256)
- [x] BSATN encoder
- [x] BSATN decoder
- [x] BSATN format object
- [x] BSATN tests

### Phase 2: Core Types ✅

- [x] Core domain types: `Identity`, `ConnectionId`, `Timestamp`, `TimeDuration`, `ScheduleAt` — all with BSATN `@Serializable`
- [x] `UuidSerializer` for `kotlin.uuid.Uuid` — BSATN serialization via U128 LE
- [x] Error types: `SpacetimeError` abstract class hierarchy
- [x] Event types: `Event` abstract class, `ReducerEvent`, `Status` abstract class

### Phase 3: Client Cache & Tables ✅

- [x] `CallbackId` value class for callback deregistration handles
- [x] Table interfaces: `Table<Row>`, `TableWithPrimaryKey<Row>`, `EventTable<Row>`
- [x] Client cache: `ClientCache` with `TableCache<Row>`, reference counting
- [x] `UniqueIndex<Row, Col>` for efficient column lookups, integrated into `TableCache`

### Phase 4: Transport & Connection ✅

- [x] Callback dispatch + transaction diff types (PR #12)
- [x] WebSocket protocol types v2: `ServerMessage`, `ClientMessage`, `TaggedSumSerializer` (PR #13)
- [x] Transport + credentials: Ktor WebSocket, Okio credential persistence, compression, URI construction (PR #14)
- [x] Connection + subscriptions: `DbConnectionBuilder`, `DbContext`, `SubscriptionBuilder`, `SubscriptionHandle`

### Phase 5: Codegen ✅

- [x] Code generator CLI: reads SpacetimeDB module schema, generates Kotlin source files (row classes, table handles, reducers, `DbConnection`, etc.)
- [x] Gradle plugin: wraps build + codegen into Gradle build pipeline
- [x] Reducer callbacks: `on<Reducer>` / `removeOn<Reducer>` methods on RemoteReducers (PR #30)

### Phase 6: Integration & Example ✅

- [x] End-to-end example project with Rust module + Kotlin client (PR #29)
- [x] Comprehensive test coverage for connection, subscriptions, reducer callbacks

## Notes

- Phase 1 (BSATN) is self-contained and testable in isolation
- Phase 2-4 can be partially parallelized but have dependencies
- Phase 5 (codegen) depends on all core types being stable
- Phase 6 requires a running SpacetimeDB instance
- `expect`/`actual` KMP mechanism needed for: file I/O (credentials), compression libs
- Ktor Client handles WebSocket cross-platform; kotlinx.serialization handles ser/de cross-platform
