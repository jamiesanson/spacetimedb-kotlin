package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName

// Serialization annotations used across generators
internal val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
internal val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
