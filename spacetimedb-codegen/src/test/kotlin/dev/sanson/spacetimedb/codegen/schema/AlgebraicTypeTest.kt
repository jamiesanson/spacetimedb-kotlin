package dev.sanson.spacetimedb.codegen.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertIs<AlgebraicType.I32>(type.type.elements[0].algebraicType)
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

    // --- Special type detection tests ---

    @Test
    fun `detects Identity product type`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"some": "__identity__"}, "algebraic_type": {"U256": []}}
        ]}}""")
        assertTrue(type.isIdentity)
        assertFalse(type.isConnectionId)
        assertFalse(type.isTimestamp)
    }

    @Test
    fun `detects ConnectionId product type`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"some": "__connection_id__"}, "algebraic_type": {"U128": []}}
        ]}}""")
        assertTrue(type.isConnectionId)
        assertFalse(type.isIdentity)
    }

    @Test
    fun `detects Timestamp product type`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"some": "__timestamp_micros_since_unix_epoch__"}, "algebraic_type": {"I64": []}}
        ]}}""")
        assertTrue(type.isTimestamp)
        assertFalse(type.isTimeDuration)
    }

    @Test
    fun `detects TimeDuration product type`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"some": "__time_duration_micros__"}, "algebraic_type": {"I64": []}}
        ]}}""")
        assertTrue(type.isTimeDuration)
        assertFalse(type.isTimestamp)
    }

    @Test
    fun `regular product is not a special type`() {
        val type = parse("""{"Product": {"elements": [
            {"name": {"some": "x"}, "algebraic_type": {"I32": []}},
            {"name": {"some": "y"}, "algebraic_type": {"I32": []}}
        ]}}""")
        assertFalse(type.isIdentity)
        assertFalse(type.isConnectionId)
        assertFalse(type.isTimestamp)
        assertFalse(type.isTimeDuration)
    }

    @Test
    fun `detects Option sum type`() {
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "some"}, "algebraic_type": {"String": []}},
            {"name": {"some": "none"}, "algebraic_type": {"Product": {"elements": []}}}
        ]}}""")
        val inner = type.asOption()
        assertNotNull(inner)
        assertIs<AlgebraicType.StringType>(inner)
    }

    @Test
    fun `non-option sum returns null from asOption`() {
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "Active"}, "algebraic_type": {"Product": {"elements": []}}},
            {"name": {"some": "Inactive"}, "algebraic_type": {"Product": {"elements": []}}}
        ]}}""")
        assertNull(type.asOption())
    }

    @Test
    fun `detects simple enum`() {
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "Active"}, "algebraic_type": {"Product": {"elements": []}}},
            {"name": {"some": "Inactive"}, "algebraic_type": {"Product": {"elements": []}}}
        ]}}""")
        assertIs<AlgebraicType.Sum>(type)
        assertTrue(type.type.isSimpleEnum)
    }

    @Test
    fun `non-simple enum is not simple`() {
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "Value"}, "algebraic_type": {"I32": []}},
            {"name": {"some": "None"}, "algebraic_type": {"Product": {"elements": []}}}
        ]}}""")
        assertIs<AlgebraicType.Sum>(type)
        assertFalse(type.type.isSimpleEnum)
    }

    @Test
    fun `detects ScheduleAt sum type`() {
        val interval = """{"Product": {"elements": [
            {"name": {"some": "__time_duration_micros__"}, "algebraic_type": {"I64": []}}
        ]}}"""
        val timestamp = """{"Product": {"elements": [
            {"name": {"some": "__timestamp_micros_since_unix_epoch__"}, "algebraic_type": {"I64": []}}
        ]}}"""
        val type = parse("""{"Sum": {"variants": [
            {"name": {"some": "Interval"}, "algebraic_type": $interval},
            {"name": {"some": "Time"}, "algebraic_type": $timestamp}
        ]}}""")
        assertTrue(type.isScheduleAt)
    }

    @Test
    fun `primitive types are not special types`() {
        val type = parse("""{"U32": []}""")
        assertFalse(type.isIdentity)
        assertFalse(type.isConnectionId)
        assertFalse(type.isTimestamp)
        assertFalse(type.isTimeDuration)
        assertFalse(type.isScheduleAt)
        assertNull(type.asOption())
    }

    private fun parse(jsonStr: String): AlgebraicType =
        json.decodeFromString(AlgebraicTypeSerializer, jsonStr)
}
