package dev.sanson.spacetimedb.codegen.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RawModuleDefTest {
    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `parses V10 module def from JSON`() {
        val schema = ModuleSchema.fromJson(fixture)

        assertEquals(17, schema.typespace.types.size)
        assertEquals(17, schema.types.size)
        assertEquals(13, schema.tables.size)
        assertEquals(15, schema.reducers.size)
        assertEquals(4, schema.procedures.size)
        assertEquals(1, schema.views.size)
    }

    @Test
    fun `parses table with primary key and indexes`() {
        val schema = ModuleSchema.fromJson(fixture)
        val table = schema.tables.first { it.source_name == "logged_out_player" }

        assertEquals(listOf(0), table.primary_key)
        assertEquals(3, table.indexes.size)
        assertEquals("Public", table.table_access)
        assertEquals("User", table.table_type)

        val firstIndex = table.indexes.first()
        assertEquals("logged_out_player_player_id_idx_btree", firstIndex.source_name)
        assertEquals("player_id", firstIndex.accessor_name)
        assertEquals("BTree", firstIndex.algorithm.type)
        assertEquals(listOf(1), firstIndex.algorithm.columns)
    }

    @Test
    fun `parses table constraints`() {
        val schema = ModuleSchema.fromJson(fixture)
        val table = schema.tables.first { it.source_name == "logged_out_player" }

        assertEquals(3, table.constraints.size)
        val constraint = table.constraints.first()
        assertEquals("Unique", constraint.data.type)
        assertEquals(listOf(0), constraint.data.columns)
    }

    @Test
    fun `parses reducer with parameters`() {
        val schema = ModuleSchema.fromJson(fixture)
        val reducer = schema.reducers.first { it.source_name == "add" }

        assertEquals("ClientCallable", reducer.visibility)
        assertEquals(2, reducer.params.elements.size)

        val nameParam = reducer.params.elements[0]
        assertEquals("name", nameParam.name)
        assertIs<AlgebraicType.StringType>(nameParam.algebraic_type)

        val ageParam = reducer.params.elements[1]
        assertEquals("age", ageParam.name)
        assertIs<AlgebraicType.U8>(ageParam.algebraic_type)
    }

    @Test
    fun `parses type definitions with scoped names`() {
        val schema = ModuleSchema.fromJson(fixture)
        val typeDef = schema.types.first { it.source_name.source_name == "RepeatingTestArg" }

        assertEquals(emptyList(), typeDef.source_name.scope)
        assertEquals(6, typeDef.ty)
    }

    @Test
    fun `parses scoped type name with namespace`() {
        val schema = ModuleSchema.fromJson(fixture)
        val namespacedTypes = schema.types.filter { it.source_name.scope.isNotEmpty() }

        assert(namespacedTypes.isNotEmpty()) { "Expected at least one namespaced type" }
        val first = namespacedTypes.first()
        assert(first.source_name.scope.isNotEmpty())
    }

    @Test
    fun `resolves product type for table`() {
        val schema = ModuleSchema.fromJson(fixture)
        val table = schema.tables.first { it.source_name == "logged_out_player" }
        val productType = schema.tableProductType(table)

        // logged_out_player has: identity, player_id, name
        assertEquals(3, productType.elements.size)
        assertEquals("identity", productType.elements[0].name)
        assertEquals("player_id", productType.elements[1].name)
        assertEquals("name", productType.elements[2].name)
    }

    @Test
    fun `resolves algebraic type refs through typespace`() {
        val schema = ModuleSchema.fromJson(fixture)
        val ref = AlgebraicType.Ref(0)
        val resolved = schema.resolveType(ref)

        assertIs<AlgebraicType.Product>(resolved)
    }

    @Test
    fun `filters public tables`() {
        val schema = ModuleSchema.fromJson(fixture)
        val publicTables = schema.publicTables

        assert(publicTables.isNotEmpty()) { "Expected at least one public table" }
        assert(publicTables.all { it.table_access == "Public" })
    }

    @Test
    fun `filters client-callable reducers`() {
        val schema = ModuleSchema.fromJson(fixture)
        val clientReducers = schema.clientCallableReducers

        assert(clientReducers.isNotEmpty()) { "Expected at least one client-callable reducer" }
        assert(clientReducers.all { it.visibility == "ClientCallable" })
    }

    @Test
    fun `parses Identity as Product with __identity__ field`() {
        val schema = ModuleSchema.fromJson(fixture)
        val table = schema.tables.first { it.source_name == "logged_out_player" }
        val productType = schema.tableProductType(table)

        val identityField = productType.elements.first { it.name == "identity" }
        val resolved = identityField.algebraic_type
        assertIs<AlgebraicType.Product>(resolved)

        val innerElements = resolved.type.elements
        assertEquals(1, innerElements.size)
        assertEquals("__identity__", innerElements[0].name)
        assertIs<AlgebraicType.U256>(innerElements[0].algebraic_type)
    }

    @Test
    fun `parses explicit names section`() {
        val schema = ModuleSchema.fromJson(fixture)
        val names = schema.explicitNames

        assertNotNull(names)
        assert(names.entries.isNotEmpty()) { "Expected at least one explicit name entry" }

        val tableEntry = names.entries.firstOrNull { it.kind == "Table" }
        assertNotNull(tableEntry)
        assertNotNull(tableEntry.source_name)
        assertNotNull(tableEntry.canonical_name)
    }

    @Test
    fun `parses views`() {
        val schema = ModuleSchema.fromJson(fixture)
        assertEquals(1, schema.views.size)

        val view = schema.views.first()
        assertEquals("my_player", view.source_name)
        assertEquals(true, view.is_public)
    }

    @Test
    fun `parses procedures`() {
        val schema = ModuleSchema.fromJson(fixture)
        assertEquals(4, schema.procedures.size)

        val proc = schema.procedures.first()
        assertNotNull(proc.source_name)
        assertNotNull(proc.visibility)
    }
}
