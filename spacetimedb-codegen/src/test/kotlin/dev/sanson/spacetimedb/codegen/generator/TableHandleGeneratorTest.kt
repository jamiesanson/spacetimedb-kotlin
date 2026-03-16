package dev.sanson.spacetimedb.codegen.generator

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

class TableHandleGeneratorTest {

    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `generates table handle interface for table with primary key`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        // player table has PK and unique constraints
        val playerTable = schema.publicTables.first { it.sourceName == "player" }
        val productType = schema.tableProductType(playerTable)
        val file = gen.generateTableHandleFile(playerTable, productType)
        val output = file.toString()

        assertContains(output, "interface PlayerTableHandle")
        assertContains(output, "TableWithPrimaryKey<Player>")
        // Should have findBy for each unique constraint column
        assertContains(output, "findByIdentity")
        assertContains(output, "findByPlayerId")
        assertContains(output, "findByName")
    }

    @Test
    fun `generates table handle interface for table without primary key`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        // test_f has no PK and no unique constraints
        val testFTable = schema.publicTables.first { it.sourceName == "test_f" }
        val productType = schema.tableProductType(testFTable)
        val file = gen.generateTableHandleFile(testFTable, productType)
        val output = file.toString()

        assertContains(output, "interface TestFTableHandle")
        assertContains(output, "Table<TestF>")
        // No findBy methods since no unique constraints
        assertTrue(!output.contains("findBy"))
    }

    @Test
    fun `generates table handle with correct return types for findBy`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        val personTable = schema.publicTables.first { it.sourceName == "person" }
        val productType = schema.tableProductType(personTable)
        val file = gen.generateTableHandleFile(personTable, productType)
        val output = file.toString()

        assertContains(output, "findById")
        assertContains(output, "Person?")
    }

    @Test
    fun `generates RemoteTables interface with property per public table`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        val file = gen.generateRemoteTablesFile()
        val output = file.toString()

        assertContains(output, "interface RemoteTables")
        // Public tables: logged_out_player, person, player, test_d, test_f
        assertContains(output, "loggedOutPlayer: LoggedOutPlayerTableHandle")
        assertContains(output, "person: PersonTableHandle")
        assertContains(output, "player: PlayerTableHandle")
        assertContains(output, "testD: TestDTableHandle")
        assertContains(output, "testF: TestFTableHandle")
    }

    @Test
    fun `generates table handles for all public tables`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")
        val files = gen.generateTableHandleFiles()

        assertEquals(schema.publicTables.size, files.size)
        for (file in files) {
            assertContains(file.toString(), "TableHandle")
        }
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generated table handles compile against SDK types`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example"
        val typeGen = TypeGenerator(schema, pkg)
        val tableHandleGen = TableHandleGenerator(schema, pkg)

        val sources = mutableListOf<SourceFile>()

        // Row types are needed for table handles to compile
        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        for (table in schema.publicTables) {
            val productType = schema.tableProductType(table)
            val file = typeGen.generateTableRowFile(table.sourceName, productType)
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        // Table handles + RemoteTables
        for (file in tableHandleGen.generateTableHandleFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        sources.add(
            SourceFile.kotlin(
                "RemoteTables.kt",
                tableHandleGen.generateRemoteTablesFile().toString(),
            )
        )

        val result =
            KotlinCompilation()
                .apply {
                    this.sources = sources
                    inheritClassPath = true
                }
                .compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Generated table handles failed to compile:\n${result.messages}",
        )
    }

    @Test
    fun `generates table handle impl for table with primary key`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        val playerTable = schema.publicTables.first { it.sourceName == "player" }
        val productType = schema.tableProductType(playerTable)
        val file = gen.generateTableHandleImplFile(playerTable, productType)
        val output = file.toString()

        assertContains(output, "class PlayerTableHandleImpl")
        assertContains(output, ": PlayerTableHandle")
        assertContains(output, "cache: ClientCache")
        assertContains(output, "callbacks: DbCallbacks")
        assertContains(output, "cache.getOrCreateTable<Player>(tableName)")
        // Unique indexes
        assertContains(output, "identityIndex")
        assertContains(output, "playerIdIndex")
        assertContains(output, "nameIndex")
        // PK table methods
        assertContains(output, "override fun onUpdate")
        assertContains(output, "override fun removeOnUpdate")
        // findBy methods
        assertContains(output, "override fun findByIdentity")
        assertContains(output, "override fun findByPlayerId")
        assertContains(output, "override fun findByName")
    }

    @Test
    fun `generates table handle impl for table without primary key`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        val testFTable = schema.publicTables.first { it.sourceName == "test_f" }
        val productType = schema.tableProductType(testFTable)
        val file = gen.generateTableHandleImplFile(testFTable, productType)
        val output = file.toString()

        assertContains(output, "class TestFTableHandleImpl")
        assertContains(output, ": TestFTableHandle")
        // No PK → no onUpdate
        assertTrue(!output.contains("onUpdate"))
        assertTrue(!output.contains("findBy"))
    }

    @Test
    fun `generates RemoteTablesImpl with lazy properties`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TableHandleGenerator(schema, "com.example")

        val file = gen.generateRemoteTablesImplFile()
        val output = file.toString()

        assertContains(output, "class RemoteTablesImpl")
        assertContains(output, ": RemoteTables")
        assertContains(output, "cache: ClientCache")
        assertContains(output, "callbacks: DbCallbacks")
        // Lazy delegation
        assertContains(output, "PlayerTableHandleImpl(cache, callbacks)")
        assertContains(output, "PersonTableHandleImpl(cache, callbacks)")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generated table handle impls compile against SDK types`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example"
        val typeGen = TypeGenerator(schema, pkg)
        val tableHandleGen = TableHandleGenerator(schema, pkg)

        val sources = mutableListOf<SourceFile>()

        // Row types
        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        for (table in schema.publicTables) {
            val productType = schema.tableProductType(table)
            val file = typeGen.generateTableRowFile(table.sourceName, productType)
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        // Table handle interfaces + RemoteTables
        for (file in tableHandleGen.generateTableHandleFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        sources.add(
            SourceFile.kotlin(
                "RemoteTables.kt",
                tableHandleGen.generateRemoteTablesFile().toString(),
            )
        )

        // Table handle impls + RemoteTablesImpl
        for (file in tableHandleGen.generateTableHandleImplFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        sources.add(
            SourceFile.kotlin(
                "RemoteTablesImpl.kt",
                tableHandleGen.generateRemoteTablesImplFile().toString(),
            )
        )

        val result =
            KotlinCompilation()
                .apply {
                    this.sources = sources
                    inheritClassPath = true
                }
                .compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Generated table handle impls failed to compile:\n${result.messages}",
        )
    }
}
