package dev.sanson.spacetimedb.transport

import okio.FileSystem

internal actual fun systemFileSystem(): FileSystem {
    throw UnsupportedOperationException(
        "Filesystem access requires explicit configuration on JS. " +
            "Use the CredentialFile constructor with a FileSystem parameter."
    )
}
