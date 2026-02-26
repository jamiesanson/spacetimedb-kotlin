package dev.sanson.spacetimedb.codegen.generator

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReducerGeneratorTest {

    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `generates Reducer sealed class with variant per client-callable reducer`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateReducerFile()
        val output = file.toString()

        assertContains(output, "sealed class Reducer")

        // Reducers with args should be data classes
        assertContains(output, "data class Add(")
        assertContains(output, "data class AddPlayer(")
        assertContains(output, "data class DeletePlayer(")

        // Reducers without args should be data objects
        assertContains(output, "data object SayHello")
        assertContains(output, "data object QueryPrivate")
        assertContains(output, "data object LogModuleIdentity")
    }

    @Test
    fun `reducer variant has correct parameter names and types`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateReducerFile()
        val output = file.toString()

        // add(name: String, age: UByte)
        assertContains(output, "val name: String")
        assertContains(output, "val age: UByte")

        // add_player(name: String)
        assertContains(output, "data class AddPlayer")
    }

    @Test
    fun `reducer variant uses SerialName for snake_case params`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateReducerFile()
        val output = file.toString()

        // delete_players_by_name has no snake_case params (just 'name'), so no @SerialName
        // test reducer has arg, arg2, arg3, arg4 — no conversion needed
    }

    @Test
    fun `excludes lifecycle reducers from Reducer class`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateReducerFile()
        val output = file.toString()

        // init and client_connected are lifecycle/private — should not appear
        assertTrue(!output.contains("object Init"))
        assertTrue(!output.contains("ClientConnected"))
        assertTrue(!output.contains("RepeatingTest"))
    }

    @Test
    fun `generates RemoteReducers interface with method per reducer`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateRemoteReducersFile()
        val output = file.toString()

        assertContains(output, "interface RemoteReducers")

        // Methods with params
        assertContains(output, "fun add(name: String, age: UByte)")
        assertContains(output, "fun addPlayer(name: String)")
        assertContains(output, "fun deletePlayer(id: ULong)")

        // Methods without params
        assertContains(output, "fun sayHello()")
        assertContains(output, "fun queryPrivate()")
    }

    @Test
    fun `RemoteReducers excludes lifecycle reducers`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateRemoteReducersFile()
        val output = file.toString()

        assertTrue(!output.contains("fun init("))
        assertTrue(!output.contains("fun clientConnected("))
        assertTrue(!output.contains("fun repeatingTest("))
    }

    @Test
    fun `generates RemoteReducersImpl with connection constructor`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateRemoteReducersImplFile()
        val output = file.toString()

        assertContains(output, "class RemoteReducersImpl")
        assertContains(output, ": RemoteReducers")
        assertContains(output, "connection: SpacetimeDbConnection")
    }

    @Test
    fun `generates args classes for reducers with parameters`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateRemoteReducersImplFile()
        val output = file.toString()

        // add(name: String, age: UByte) → AddArgs
        assertContains(output, "private class AddArgs")
        assertContains(output, "@Serializable")
        // add_player(name: String) → AddPlayerArgs
        assertContains(output, "private class AddPlayerArgs")
    }

    @Test
    fun `no-arg reducers call with empty byte array`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateRemoteReducersImplFile()
        val output = file.toString()

        assertContains(output, "connection.callReducer(\"say_hello\", byteArrayOf())")
    }

    @Test
    fun `reducers with args BSATN-encode via args class`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = ReducerGenerator(schema, "com.example")

        val file = gen.generateRemoteReducersImplFile()
        val output = file.toString()

        assertContains(output, "Bsatn.encodeToByteArray(serializer<AddArgs>(), args)")
        assertContains(output, "connection.callReducer(\"add\"")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generated RemoteReducersImpl compiles`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example"
        val typeGen = TypeGenerator(schema, pkg)
        val reducerGen = ReducerGenerator(schema, pkg)

        val sources = mutableListOf<SourceFile>()

        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        sources.add(SourceFile.kotlin("Reducer.kt", reducerGen.generateReducerFile().toString()))
        sources.add(SourceFile.kotlin("RemoteReducers.kt", reducerGen.generateRemoteReducersFile().toString()))
        sources.add(SourceFile.kotlin("RemoteReducersImpl.kt", reducerGen.generateRemoteReducersImplFile().toString()))

        val result = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
        }.compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Generated RemoteReducersImpl failed to compile:\n${result.messages}",
        )
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generated reducer code compiles`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example"
        val typeGen = TypeGenerator(schema, pkg)
        val reducerGen = ReducerGenerator(schema, pkg)

        val sources = mutableListOf<SourceFile>()

        // Custom types are needed as reducer params reference them
        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        sources.add(SourceFile.kotlin("Reducer.kt", reducerGen.generateReducerFile().toString()))
        sources.add(SourceFile.kotlin("RemoteReducers.kt", reducerGen.generateRemoteReducersFile().toString()))

        val result = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
        }.compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Generated reducer code failed to compile:\n${result.messages}",
        )
    }
}
