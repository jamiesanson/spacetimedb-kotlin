# SpacetimeDB Kotlin Multiplatform SDK

A Kotlin Multiplatform client SDK for [SpacetimeDB](https://spacetimedb.com), targeting JVM, JS, and Native platforms.

> **Status**: Work in progress — not yet ready for production use.

## Modules

| Module | Description |
|--------|-------------|
| `spacetimedb-bsatn` | BSATN binary serialization format (encoder, decoder, big integer types) |
| `spacetimedb-core` | Core SDK: types, client cache, protocol, transport, credentials |

## Platform Support

### Transport & Compression

The SDK communicates with SpacetimeDB over WebSocket using the v2 BSATN protocol. Server messages may be compressed; the client specifies its preferred compression when connecting.

| Feature | JVM | JS (Node.js) | JS (Browser) | Native |
|---------|-----|--------------|--------------|--------|
| WebSocket transport | ✅ | ✅ | ✅ | ✅ |
| Compression: None | ✅ | ✅ | ✅ | ✅ |
| Compression: Gzip | ✅ | ❌ | ❌ | ❌ |
| Compression: Brotli | ❌ | ❌ | ❌ | ❌ |

> **Note**: Brotli is the default compression in the Rust SDK. JVM Brotli support is planned. On platforms without compression support, use `Compression.None` when connecting.

### Credential Persistence

The SDK can persist authentication tokens to disk at `~/.spacetimedb_client_credentials/`.

| Feature | JVM | JS (Node.js) | JS (Browser) | Native |
|---------|-----|--------------|--------------|--------|
| `CredentialFile.create()` | ✅ | ❌ | ❌ | ✅ |
| `CredentialFile(key, fs, dir)` | ✅ | ✅¹ | ❌ | ✅ |

¹ Node.js users can pass an Okio `FileSystem` instance directly to the `CredentialFile` constructor.

Browser JS has no filesystem access. On Node.js, the convenience `CredentialFile.create()` factory is unavailable because `okio-nodefilesystem` breaks browser compilation; pass your own `FileSystem` instead.

## Dependencies

- [Okio](https://square.github.io/okio/) — Cross-platform file I/O
- [Ktor](https://ktor.io/) — WebSocket client
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — Serialization framework (with custom BSATN format)

## Building

```bash
./gradlew check
```

## License

TBD
