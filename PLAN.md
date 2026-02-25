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

### Phase 2: Core Types

- [ ] Core domain types: `Identity`, `ConnectionId`, `Timestamp`, `TimeDuration`, `ScheduleAt`, `Uuid` — all with BSATN `@Serializable`
- [ ] Error types: `SpacetimeError` sealed class hierarchy
- [ ] Event types: `Event` sealed class, `ReducerEvent`, `Status` sealed class

### Phase 3: Client Cache & Tables

- [ ] Table interfaces: `Table<Row>`, `TableWithPrimaryKey<Row>`, `EventTable<Row>`
- [ ] Client cache: `ClientCache` with `TableCache<Row>`, reference counting, unique index support

### Phase 4: Transport & Connection

- [ ] WebSocket protocol types (v2): `ServerMessage`, `ClientMessage`, etc.
- [ ] WebSocket transport: Ktor WebSocket client wrapper with compression
- [ ] `DbConnectionBuilder` & `DbContext` implementation
- [ ] Subscription management: `SubscriptionBuilder`, `SubscriptionHandle`
- [ ] Credentials: File-based token persistence (platform-specific)

### Phase 5: Codegen

- [ ] Code generator CLI: reads SpacetimeDB module schema, generates Kotlin source files (row classes, `DbView`, `Reducers`, `DbConnection`, etc.)

### Phase 6: Integration Tests

- [ ] Integration test harness (port Rust test-client pattern)
- [ ] Port all Rust SDK tests (~50+ tests)

## Notes

- Phase 1 (BSATN) is self-contained and testable in isolation
- Phase 2-4 can be partially parallelized but have dependencies
- Phase 5 (codegen) depends on all core types being stable
- Phase 6 requires a running SpacetimeDB instance
- `expect`/`actual` KMP mechanism needed for: file I/O (credentials), compression libs
- Ktor Client handles WebSocket cross-platform; kotlinx.serialization handles ser/de cross-platform
