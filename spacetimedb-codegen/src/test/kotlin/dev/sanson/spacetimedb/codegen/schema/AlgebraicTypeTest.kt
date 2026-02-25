package dev.sanson.spacetimedb.codegen.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AlgebraicTypeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses primitive unit types`() {
        assertIs<AlgebraicType.Bool>(parse("""{"Bool": []}"""))
        assertIs<AlgebraicType.U8>(parse("""{"U8": []}"""))
        assertIs<AlgebraicType.I32>(parse("""{"I32": []}"""))
        assertIs<AlgebraicType.U64>(parse("""{"U64": []}"""))
        assertIs<AlgebraicType.F32>(parse("""{"F32": []}"""))
        assertIs<AlgebraicType.F64>(parse("""{"F64": []}"""))
        assertIs<AlgebraicType.StringType>(parse("""{"String": []}"""))
        assertIs<AlgebraicType.U128>(parse("""{"U128": []}"""))
        assertIs<AlgebraicType.U256>(parse("""{"U256": []}"""))
    }

    @Test
    fun `parses Ref type`() {
        val type = parse("""{"Ref": 42}""")
        assertIs<AlgebraicType.Ref>(type)
        assertEquals(42, type.id)
    }

    @Test
    fun `parses Array type`() {
        val type = parse("""{"Array": {"U8": []}}""")
        assertIs<AlgebraicType.Array>(type)
        assertIs<AlgebraicType.U8>(type.elementType)
    }

    @Test
    fun `parses nested Array type`() {
        val type = parse("""{"Array": {"Array": {"String": []}}}""")
        assertIs<AlgebraicType.Array>(type)
        val inner = type.elementType
        assertIs<AlgebraicType.Array>(inner)
        assertIs<AlgebraicType.StringType>(inner.elementType)
    }

    @Test
    fun `parses Product type`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"some": "x"}, "algebraic_type": {"I32": []}},
            {"name": {"some": "y"}, "algebraic_type": {"I32": []}}
        ]}}""")
        assertIs<AlgebraicType.Product>(type)
        assertEquals(2, type.type.elements.size)
        assertEquals("x", type.type.elements[0].name)
        assertEquals("y", type.type.elements[1].name)
        assertIs<AlgebraicType.I32>(type.type.elements[0].algebraic_type)
    }

    @Test
    fun `parses Product with unnamed fields`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"none": {}}, "algebraic_type": {"U32": []}}
        ]}}""")
        assertIs<AlgebraicType.Product>(type)
        assertEquals(null, type.type.elements[0].name)
    }

    @Test
    fun `parses Sum type`() {
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "Active"}, "algebraic_type": {"Product": {"elements": []}}},
            {"name": {"some": "Inactive"}, "algebraic_type": {"Product": {"elements": []}}}
        ]}}""")
        assertIs<AlgebraicType.Sum>(type)
        assertEquals(2, type.type.variants.size)
        assertEquals("Active", type.type.variants[0].name)
        assertEquals("Inactive", type.type.variants[1].name)
    }

    @Test
    fun `parses Option as Sum with some and none variants`() {
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "some"}, "algebraic_type": {"String": []}},
            {"name": {"some": "none"}, "algebraic_type": {"Product": {"elements": []}}}
        ]}}""")
        assertIs<AlgebraicType.Sum>(type)
        assertEquals("some", type.type.variants[0].name)
        assertEquals("none", type.type.variants[1].name)
    }

    @Test
    fun `parses Array of Ref`() {
        val type = parse("""{"Array": {"Ref": 5}}""")
        assertIs<AlgebraicType.Array>(type)
        val inner = type.elementType
        assertIs<AlgebraicType.Ref>(inner)
        assertEquals(5, inner.id)
    }

    private fun parse(jsonStr: String): AlgebraicType =
        json.decodeFromString(AlgebraicTypeSerializer, jsonStr)
}
