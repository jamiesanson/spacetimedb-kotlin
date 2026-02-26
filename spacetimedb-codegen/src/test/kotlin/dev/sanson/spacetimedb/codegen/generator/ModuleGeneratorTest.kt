package dev.sanson.spacetimedb.codegen.generator

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleGeneratorTest {

    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `generates deserializer map with entry per public table`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ModuleGenerator(schema, "com.example")

        val file = gen.generateDeserializerMapFile()
        val output = file.toString()

        assertContains(output, "fun tableDeserializerMap()")
        // Should have serializer entries for all public tables
        assertContains(output, "\"logged_out_player\" to serializer<LoggedOutPlayer>()")
        assertContains(output, "\"person\" to serializer<Person>()")
        assertContains(output, "\"player\" to serializer<Player>()")
        assertContains(output, "\"test_d\" to serializer<TestD>()")
        assertContains(output, "\"test_f\" to serializer<TestF>()")
    }

    @Test
    fun `generates builder extension function`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ModuleGenerator(schema, "com.example")

        val file = gen.generateBuilderExtensionFile()
        val output = file.toString()

        assertContains(output, "fun SpacetimeDbConnectionBuilder.withModuleDeserializers()")
        assertContains(output, "SpacetimeDbConnectionBuilder")
        assertContains(output, "withTableDeserializers(tableDeserializerMap())")
        assertContains(output, "withPkExtractors(tablePkExtractorMap())")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generated module-level code compiles`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example"
        val typeGen = TypeGenerator(schema, pkg)
        val moduleGen = ModuleGenerator(schema, pkg)

        val sources = mutableListOf<SourceFile>()

        // Row types needed by deserializer map
        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        for (table in schema.publicTables) {
            val productType = schema.tableProductType(table)
            val file = typeGen.generateTableRowFile(table.sourceName, productType)
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        // Module wiring
        sources.add(SourceFile.kotlin("TableDeserializerMap.kt", moduleGen.generateDeserializerMapFile().toString()))
        sources.add(SourceFile.kotlin("TablePkExtractorMap.kt", moduleGen.generatePkExtractorMapFile().toString()))
        sources.add(SourceFile.kotlin("BuilderExtensions.kt", moduleGen.generateBuilderExtensionFile().toString()))

        val result = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
        }.compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Generated module-level code failed to compile:\n${result.messages}",
        )
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `all generated code compiles together end-to-end`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example.module"
        val typeGen = TypeGenerator(schema, pkg)
        val tableHandleGen = TableHandleGenerator(schema, pkg)
        val reducerGen = ReducerGenerator(schema, pkg)
        val moduleGen = ModuleGenerator(schema, pkg)

        val sources = mutableListOf<SourceFile>()

        // Custom types
        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        // Table row types
        for (table in schema.publicTables) {
            val productType = schema.tableProductType(table)
            val file = typeGen.generateTableRowFile(table.sourceName, productType)
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        // Table handles + RemoteTables
        for (file in tableHandleGen.generateTableHandleFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        sources.add(SourceFile.kotlin("RemoteTables.kt", tableHandleGen.generateRemoteTablesFile().toString()))

        // Table handle impls + RemoteTablesImpl
        for (file in tableHandleGen.generateTableHandleImplFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        sources.add(SourceFile.kotlin("RemoteTablesImpl.kt", tableHandleGen.generateRemoteTablesImplFile().toString()))

        // Reducers
        sources.add(SourceFile.kotlin("Reducer.kt", reducerGen.generateReducerFile().toString()))
        sources.add(SourceFile.kotlin("RemoteReducers.kt", reducerGen.generateRemoteReducersFile().toString()))
        sources.add(SourceFile.kotlin("RemoteReducersImpl.kt", reducerGen.generateRemoteReducersImplFile().toString()))

        // Module wiring
        sources.add(SourceFile.kotlin("TableDeserializerMap.kt", moduleGen.generateDeserializerMapFile().toString()))
        sources.add(SourceFile.kotlin("TablePkExtractorMap.kt", moduleGen.generatePkExtractorMapFile().toString()))
        sources.add(SourceFile.kotlin("BuilderExtensions.kt", moduleGen.generateBuilderExtensionFile().toString()))
        sources.add(SourceFile.kotlin("DbConnection.kt", moduleGen.generateDbConnectionFile().toString()))
        sources.add(SourceFile.kotlin("DbConnectionBuilder.kt", moduleGen.generateDbConnectionBuilderFile().toString()))

        val result = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
        }.compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Full end-to-end generated code failed to compile:\n${result.messages}",
        )
    }

    @Test
    fun `generates DbConnection implementing DbContext`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ModuleGenerator(schema, "com.example")

        val file = gen.generateDbConnectionFile()
        val output = file.toString()

        assertContains(output, "class DbConnection")
        assertContains(output, "DbContext<RemoteTables, RemoteReducers>")
        assertContains(output, "override val db: RemoteTables")
        assertContains(output, "override val reducers: RemoteReducers")
        assertContains(output, "override val identity: Identity?")
        assertContains(output, "override val connectionId: ConnectionId?")
        assertContains(output, "override val isActive: Boolean")
        assertContains(output, "override fun subscriptionBuilder()")
        assertContains(output, "override fun disconnect()")
        assertContains(output, "fun builder()")
    }

    @Test
    fun `generates DbConnectionBuilder with fluent API`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ModuleGenerator(schema, "com.example")

        val file = gen.generateDbConnectionBuilderFile()
        val output = file.toString()

        assertContains(output, "class DbConnectionBuilder")
        assertContains(output, "fun withUri(uri: String): DbConnectionBuilder")
        assertContains(output, "fun withDatabaseName(name: String): DbConnectionBuilder")
        assertContains(output, "fun withToken(token: String?): DbConnectionBuilder")
        assertContains(output, "fun onConnect(")
        assertContains(output, "fun onDisconnect(")
        assertContains(output, "fun onConnectError(")
        assertContains(output, "fun build(scope: CoroutineScope): DbConnection")
        // Auto-configures deserializers
        assertContains(output, "withModuleDeserializers()")
    }
}
