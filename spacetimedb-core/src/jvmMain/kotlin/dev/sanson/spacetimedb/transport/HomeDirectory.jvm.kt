package dev.sanson.spacetimedb.transport

import okio.Path
import okio.Path.Companion.toPath

internal actual fun defaultHomeDirectory(): Path? = System.getProperty("user.home")?.toPath()
