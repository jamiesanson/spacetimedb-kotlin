package dev.sanson.spacetimedb.transport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun defaultHomeDirectory(): Path? =
    getenv("HOME")?.toKString()?.toPath()
