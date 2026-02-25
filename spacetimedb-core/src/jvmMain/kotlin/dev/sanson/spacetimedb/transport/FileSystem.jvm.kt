package dev.sanson.spacetimedb.transport

import okio.FileSystem

internal actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM
