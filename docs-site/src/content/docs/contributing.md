---
title: Contributing
description: Development setup, building, and module structure.
---

## Dependencies

- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — Serialization framework (with custom BSATN format)
- [Ktor](https://ktor.io/) — WebSocket client
- [Okio](https://square.github.io/okio/) — Cross-platform file I/O
- [KotlinPoet](https://square.github.io/kotlinpoet/) — Kotlin source generation (codegen only)

## Building

```bash
./gradlew check                          # full build + tests
./gradlew :spacetimedb-codegen:test      # codegen tests only
./gradlew :spacetimedb-gradle-plugin:test # plugin tests only
./gradlew publishToMavenLocal            # publish all artifacts to ~/.m2
```

## Modules

| Module | Coordinates | Description |
|--------|-------------|-------------|
| `spacetimedb-bsatn` | `dev.sanson.spacetimedb:spacetimedb-bsatn` | BSATN binary serialization (`kotlinx.serialization` format) |
| `spacetimedb-core` | `dev.sanson.spacetimedb:spacetimedb-core` | Connection, client cache, subscriptions, protocol, events |
| `spacetimedb-codegen` | `dev.sanson.spacetimedb:spacetimedb-codegen` | Schema → Kotlin source generation (KotlinPoet) |
| `spacetimedb-gradle-plugin` | Plugin ID: `dev.sanson.spacetimedb` | Gradle plugin wrapping build + codegen |

## Architecture

```
┌─────────────────────┐     ┌──────────────────────┐
│  spacetimedb-bsatn  │     │  spacetimedb-codegen  │
│  (serialization)    │     │  (code generation)    │
└────────┬────────────┘     └──────────┬────────────┘
         │                             │
         ▼                             ▼
┌─────────────────────┐     ┌──────────────────────────┐
│  spacetimedb-core   │     │  spacetimedb-gradle-plugin│
│  (connection, cache)│     │  (build + generate)       │
└─────────────────────┘     └──────────────────────────┘
         ▲                             │
         │                             │
         └─────── generated code ──────┘
```
