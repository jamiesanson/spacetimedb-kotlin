package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema

// SDK types for module wiring
private val SPACETIMEDB_CONNECTION = ClassName("dev.sanson.spacetimedb", "SpacetimeDbConnection")
private val SPACETIMEDB_CONNECTION_BUILDER = ClassName("dev.sanson.spacetimedb", "SpacetimeDbConnectionBuilder")
private val DB_CONTEXT = ClassName("dev.sanson.spacetimedb", "DbContext")
private val IDENTITY = ClassName("dev.sanson.spacetimedb", "Identity")
private val CONNECTION_ID = ClassName("dev.sanson.spacetimedb", "ConnectionId")
private val SPACETIME_ERROR = ClassName("dev.sanson.spacetimedb", "SpacetimeError")
private val SUBSCRIPTION_BUILDER = ClassName("dev.sanson.spacetimedb", "SubscriptionBuilder")
private val COMPRESSION = ClassName("dev.sanson.spacetimedb.protocol", "Compression")
private val HTTP_CLIENT = ClassName("io.ktor.client", "HttpClient")
private val COROUTINE_SCOPE = ClassName("kotlinx.coroutines", "CoroutineScope")
private val K_SERIALIZER = ClassName("kotlinx.serialization", "KSerializer")

/**
 * Generates module-level wiring that ties generated types, table handles,
 * and reducer wrappers together with the core SDK.
 *
 * Produces:
 * - `tableDeserializerMap()` with serializer entries for all public tables
 * - `withModuleDeserializers()` extension on `SpacetimeDbConnectionBuilder`
 * - `DbConnection` — generated per-module class implementing `DbContext`
 * - `DbConnectionBuilder` — fluent builder that wraps `SpacetimeDbConnectionBuilder`
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
     * Generate a `withModuleDeserializers()` extension on `SpacetimeDbConnectionBuilder`
     * that calls `withTableDeserializers(tableDeserializerMap())`.
     */
    public fun generateBuilderExtensionFile(): FileSpec {
        val funSpec = FunSpec.builder("withModuleDeserializers")
            .addModifiers(KModifier.PUBLIC)
            .receiver(SPACETIMEDB_CONNECTION_BUILDER)
            .returns(SPACETIMEDB_CONNECTION_BUILDER)
            .addCode("return withTableDeserializers(tableDeserializerMap())\n")
            .build()

        return FileSpec.builder(targetPackage, "BuilderExtensions")
            .addFunction(funSpec)
            .build()
    }

    /**
     * Generate the per-module `DbConnection` class.
     *
     * Wraps [SpacetimeDbConnection] and implements `DbContext<RemoteTables, RemoteReducers>`.
     */
    public fun generateDbConnectionFile(): FileSpec {
        val remoteTables = ClassName(targetPackage, "RemoteTables")
        val remoteReducers = ClassName(targetPackage, "RemoteReducers")
        val remoteTablesImpl = ClassName(targetPackage, "RemoteTablesImpl")
        val remoteReducersImpl = ClassName(targetPackage, "RemoteReducersImpl")
        val dbConnectionBuilder = ClassName(targetPackage, "DbConnectionBuilder")
        val dbContextType = DB_CONTEXT.parameterizedBy(remoteTables, remoteReducers)

        val classBuilder = TypeSpec.classBuilder("DbConnection")
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(dbContextType)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("connection", SPACETIMEDB_CONNECTION)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("connection", SPACETIMEDB_CONNECTION)
                    .initializer("connection")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            // db: RemoteTables
            .addProperty(
                PropertySpec.builder("db", remoteTables)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .initializer("%T(connection.cache, connection.callbacks)", remoteTablesImpl)
                    .build()
            )
            // reducers: RemoteReducers
            .addProperty(
                PropertySpec.builder("reducers", remoteReducers)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .initializer("%T(connection)", remoteReducersImpl)
                    .build()
            )
            // identity
            .addProperty(
                PropertySpec.builder("identity", IDENTITY.copy(nullable = true))
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addCode("return connection.identity").build())
                    .build()
            )
            // connectionId
            .addProperty(
                PropertySpec.builder("connectionId", CONNECTION_ID.copy(nullable = true))
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addCode("return connection.connectionId").build())
                    .build()
            )
            // isActive
            .addProperty(
                PropertySpec.builder("isActive", Boolean::class)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addCode("return connection.isActive").build())
                    .build()
            )
            // subscriptionBuilder()
            .addFunction(
                FunSpec.builder("subscriptionBuilder")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .returns(SUBSCRIPTION_BUILDER)
                    .addCode("return connection.subscriptionBuilder()")
                    .build()
            )
            // disconnect()
            .addFunction(
                FunSpec.builder("disconnect")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .addCode("connection.disconnect()")
                    .build()
            )
            // companion object with builder()
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addFunction(
                        FunSpec.builder("builder")
                            .addModifiers(KModifier.PUBLIC)
                            .returns(dbConnectionBuilder)
                            .addCode("return %T()", dbConnectionBuilder)
                            .build()
                    )
                    .build()
            )

        return FileSpec.builder(targetPackage, "DbConnection")
            .addType(classBuilder.build())
            .build()
    }

    /**
     * Generate the per-module `DbConnectionBuilder` class.
     *
     * Wraps [SpacetimeDbConnectionBuilder], auto-configures table deserializers,
     * and returns a generated [DbConnection] from `build()`.
     */
    public fun generateDbConnectionBuilderFile(): FileSpec {
        val dbConnection = ClassName(targetPackage, "DbConnection")
        val dbConnectionBuilder = ClassName(targetPackage, "DbConnectionBuilder")

        val onConnectType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("identity", IDENTITY).build(),
                ParameterSpec.builder("token", String::class).build(),
                ParameterSpec.builder("connectionId", CONNECTION_ID).build(),
            ),
            returnType = UNIT,
        )
        val onDisconnectType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("error", SPACETIME_ERROR.copy(nullable = true)).build(),
            ),
            returnType = UNIT,
        )
        val onConnectErrorType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("error", SPACETIME_ERROR).build(),
            ),
            returnType = UNIT,
        )

        val classBuilder = TypeSpec.classBuilder("DbConnectionBuilder")
            .addModifiers(KModifier.PUBLIC)
            .addProperty(
                PropertySpec.builder("inner", SPACETIMEDB_CONNECTION_BUILDER)
                    .initializer("%T()", SPACETIMEDB_CONNECTION_BUILDER)
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            // withUri
            .addFunction(
                builderDelegate("withUri", dbConnectionBuilder,
                    ParameterSpec.builder("uri", String::class).build(),
                    "inner.withUri(uri)")
            )
            // withDatabaseName
            .addFunction(
                builderDelegate("withDatabaseName", dbConnectionBuilder,
                    ParameterSpec.builder("name", String::class).build(),
                    "inner.withDatabaseName(name)")
            )
            // withToken
            .addFunction(
                builderDelegate("withToken", dbConnectionBuilder,
                    ParameterSpec.builder("token", String::class.asTypeName().copy(nullable = true)).build(),
                    "inner.withToken(token)")
            )
            // withCompression
            .addFunction(
                builderDelegate("withCompression", dbConnectionBuilder,
                    ParameterSpec.builder("compression", COMPRESSION).build(),
                    "inner.withCompression(compression)")
            )
            // withHttpClient
            .addFunction(
                builderDelegate("withHttpClient", dbConnectionBuilder,
                    ParameterSpec.builder("client", HTTP_CLIENT).build(),
                    "inner.withHttpClient(client)")
            )
            // onConnect
            .addFunction(
                FunSpec.builder("onConnect")
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter("callback", onConnectType)
                    .returns(dbConnectionBuilder)
                    .addCode("inner.onConnect(callback)\nreturn this")
                    .build()
            )
            // onDisconnect
            .addFunction(
                FunSpec.builder("onDisconnect")
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter("callback", onDisconnectType)
                    .returns(dbConnectionBuilder)
                    .addCode("inner.onDisconnect(callback)\nreturn this")
                    .build()
            )
            // onConnectError
            .addFunction(
                FunSpec.builder("onConnectError")
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter("callback", onConnectErrorType)
                    .returns(dbConnectionBuilder)
                    .addCode("inner.onConnectError(callback)\nreturn this")
                    .build()
            )
            // build — auto-configures deserializers and wraps result
            .addFunction(
                FunSpec.builder("build")
                    .addModifiers(KModifier.PUBLIC, KModifier.SUSPEND)
                    .addParameter("scope", COROUTINE_SCOPE)
                    .returns(dbConnection)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement("inner.withModuleDeserializers()")
                            .addStatement("val connection = inner.build(scope)")
                            .addStatement("return %T(connection)", dbConnection)
                            .build()
                    )
                    .build()
            )

        return FileSpec.builder(targetPackage, "DbConnectionBuilder")
            .addType(classBuilder.build())
            .build()
    }

    private fun builderDelegate(
        name: String,
        returnType: ClassName,
        param: ParameterSpec,
        delegateCode: String,
    ): FunSpec =
        FunSpec.builder(name)
            .addModifiers(KModifier.PUBLIC)
            .addParameter(param)
            .returns(returnType)
            .addCode("%L\nreturn this", delegateCode)
            .build()
}
