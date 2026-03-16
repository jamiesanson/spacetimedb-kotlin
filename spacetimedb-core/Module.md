# Module spacetimedb-core

Core client SDK for SpacetimeDB. Provides connection management, subscription tracking,
client-side caching, and callback dispatch for real-time interaction with SpacetimeDB modules.

Use [SpacetimeDbConnectionBuilder][dev.sanson.spacetimedb.SpacetimeDbConnectionBuilder] to establish
a WebSocket connection and interact with tables, reducers, and subscriptions through a typed
[DbContext][dev.sanson.spacetimedb.DbContext].

# Package dev.sanson.spacetimedb

Primary API surface for the SpacetimeDB Kotlin SDK. Contains the connection builder, database
context, table and subscription interfaces, event types, and identity primitives.

# Package dev.sanson.spacetimedb.protocol

Internal SpacetimeDB client/server protocol messages. Defines the serializable message types
exchanged over the WebSocket connection.

# Package dev.sanson.spacetimedb.transport

WebSocket transport layer and credential management. Handles connection lifecycle, message framing,
and persistent credential storage.
