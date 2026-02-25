package dev.sanson.spacetimedb.transport

import okio.Path
import okio.Path.Companion.toPath

internal actual fun defaultHomeDirectory(): Path? =
    try {
        js("require('os').homedir()").unsafeCast<String>().toPath()
    } catch (_: Throwable) {
        // Browser environment — no home directory available
        null
    }
