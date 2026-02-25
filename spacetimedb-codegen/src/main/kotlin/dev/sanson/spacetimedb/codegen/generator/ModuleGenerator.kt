package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.WildcardTypeName
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema

// SDK types for module wiring
private val DB_CONNECTION = ClassName("dev.sanson.spacetimedb", "DbConnection")
private val DB_CONNECTION_BUILDER = ClassName("dev.sanson.spacetimedb", "DbConnectionBuilder")
private val K_SERIALIZER = ClassName("kotlinx.serialization", "KSerializer")

/**
 * Generates module-level wiring that ties generated types, table handles,
 * and reducer wrappers together with the SDK's [DbConnection].
 *
 * Produces:
 * - `DbConnection` extension function `remoteReducers` / `remoteTables` (typed accessors)
 * - `tableDeserializerMap()` with serializer entries for all public tables
 * - `configureDeserializers()` extension on `DbConnectionBuilder`
 */
public class ModuleGenerator(
    private val schema: ModuleSchema,
    private val targetPackage: String,
) {

    /**
     * Generate the table deserializer map file.
     *
     * Produces a top-level function `tableDeserializerMap()` returning
     * `Map<String, KSerializer<out Any>>` with one entry per public table.
     */
    public fun generateDeserializerMapFile(): FileSpec {
        val mapType = ClassName("kotlin.collections", "Map")
            .parameterizedBy(
                ClassName("kotlin", "String"),
                K_SERIALIZER.parameterizedBy(WildcardTypeName.producerOf(ClassName("kotlin", "Any"))),
            )

        val mapEntries = CodeBlock.builder()
            .add("mapOf(\n")
            .indent()

        for ((i, table) in schema.publicTables.withIndex()) {
            val rowClass = ClassName(targetPackage, table.sourceName.toPascalCase())
            val serializerMember = MemberName("kotlinx.serialization", "serializer")
            mapEntries.add("%S to %M<%T>()", table.sourceName, serializerMember, rowClass)
            if (i < schema.publicTables.size - 1) {
                mapEntries.add(",")
            }
            mapEntries.add("\n")
        }

        mapEntries.unindent().add(")")

        val funSpec = FunSpec.builder("tableDeserializerMap")
            .addModifiers(KModifier.PUBLIC)
            .returns(mapType)
            .addCode("return %L\n", mapEntries.build())
            .build()

        return FileSpec.builder(targetPackage, "TableDeserializerMap")
            .addFunction(funSpec)
            .build()
    }

    /**
     * Generate a `configureDeserializers()` extension on `DbConnectionBuilder`
     * that calls `withTableDeserializers(tableDeserializerMap())`.
     */
    public fun generateBuilderExtensionFile(): FileSpec {
        val funSpec = FunSpec.builder("withModuleDeserializers")
            .addModifiers(KModifier.PUBLIC)
            .receiver(DB_CONNECTION_BUILDER)
            .returns(DB_CONNECTION_BUILDER)
            .addCode("return withTableDeserializers(tableDeserializerMap())\n")
            .build()

        return FileSpec.builder(targetPackage, "BuilderExtensions")
            .addFunction(funSpec)
            .build()
    }
}
