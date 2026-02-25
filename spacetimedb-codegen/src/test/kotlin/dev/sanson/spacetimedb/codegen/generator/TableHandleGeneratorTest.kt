package dev.sanson.spacetimedb.codegen.generator

import dev.sanson.spacetimedb.codegen.schema.AlgebraicType
import dev.sanson.spacetimedb.codegen.schema.ConstraintData
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.ProductType
import dev.sanson.spacetimedb.codegen.schema.ProductTypeElement
import dev.sanson.spacetimedb.codegen.schema.RawConstraintDef
import dev.sanson.spacetimedb.codegen.schema.RawTableDef
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
