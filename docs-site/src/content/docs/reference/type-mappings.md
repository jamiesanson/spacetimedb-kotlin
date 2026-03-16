---
title: Type Mappings
description: How SpacetimeDB types map to Kotlin types in generated code.
---


How SpacetimeDB types map to Kotlin types in generated code.

## Primitive types

| SpacetimeDB (Rust) | Kotlin | Notes |
|---------------------|--------|-------|
| `bool` | `Boolean` | |
| `i8` | `Byte` | |
| `u8` | `UByte` | |
| `i16` | `Short` | |
| `u16` | `UShort` | |
| `i32` | `Int` | |
| `u32` | `UInt` | |
| `i64` | `Long` | |
| `u64` | `ULong` | |
| `i128` | `I128` | `dev.sanson.spacetimedb.bsatn.I128` |
| `u128` | `U128` | `dev.sanson.spacetimedb.bsatn.U128` |
| `i256` | `I256` | `dev.sanson.spacetimedb.bsatn.I256` |
| `u256` | `U256` | `dev.sanson.spacetimedb.bsatn.U256` |
| `f32` | `Float` | |
| `f64` | `Double` | |
| `String` | `String` | |

## Collection types

| SpacetimeDB (Rust) | Kotlin | Notes |
|---------------------|--------|-------|
| `Vec<T>` | `List<T>` | Generic list |
| `Vec<u8>` | `ByteArray` | Special case for efficiency |
| `Option<T>` | `T?` | Nullable type |

## SpacetimeDB built-in types

These are detected by the codegen from their field structure and mapped to SDK types automatically:

| SpacetimeDB type | Kotlin type | Package |
|------------------|-------------|---------|
| `Identity` | `Identity` | `dev.sanson.spacetimedb` |
| `ConnectionId` | `ConnectionId` | `dev.sanson.spacetimedb` |
| `Timestamp` | `Timestamp` | `dev.sanson.spacetimedb` |
| `TimeDuration` | `TimeDuration` | `dev.sanson.spacetimedb` |
| `ScheduleAt` | `ScheduleAt` | `dev.sanson.spacetimedb` |

## Algebraic types

| SpacetimeDB concept | Kotlin representation |
|---------------------|----------------------|
| Product type (struct) | `@Serializable class` with constructor properties |
| Sum type (all unit variants) | `@Serializable enum class` |
| Sum type (mixed variants) | `@Serializable sealed class` with data class/data object subtypes |

### Field naming

Rust `snake_case` fields are converted to Kotlin `camelCase`. The original name is preserved via `@SerialName`:

```kotlin
@Serializable
public class Player(
    @SerialName("player_id")
    public val playerId: ULong,
    public val name: String,  // no @SerialName needed — same in both conventions
)
```

### Sum type examples

**Simple enum** (all unit variants):

```rust
// Rust module
enum Status { Active, Inactive, Banned }
```

```kotlin
// Generated Kotlin
@Serializable
public enum class Status { Active, Inactive, Banned }
```

**Sealed class** (variants with data):

```rust
// Rust module
enum Shape {
    Circle { radius: f64 },
    Rectangle { width: f64, height: f64 },
    None,
}
```

```kotlin
// Generated Kotlin
@Serializable
public sealed class Shape {
    public data class Circle(val radius: Double) : Shape()
    public data class Rectangle(val width: Double, val height: Double) : Shape()
    public data object None : Shape()
}
```
