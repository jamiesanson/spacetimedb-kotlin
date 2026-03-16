# Module spacetimedb-bsatn

BSATN (Binary SpacetimeDB Algebraic Type Notation) serialization for Kotlin. Provides a compact
binary format compatible with the SpacetimeDB wire protocol, built on kotlinx.serialization.

Supports encoding and decoding of all SpacetimeDB algebraic types including products (data classes),
sums (sealed hierarchies), and extended integer types (`U128`, `I128`, `U256`, `I256`).

# Package dev.sanson.spacetimedb.bsatn

Core BSATN serialization API. Use [Bsatn][dev.sanson.spacetimedb.bsatn.Bsatn] to encode and decode
`@Serializable` types to and from the BSATN binary format.
