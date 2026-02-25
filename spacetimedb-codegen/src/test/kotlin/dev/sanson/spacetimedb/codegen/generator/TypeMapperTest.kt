package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import dev.sanson.spacetimedb.codegen.schema.AlgebraicType
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.ProductType
import dev.sanson.spacetimedb.codegen.schema.ProductTypeElement
import dev.sanson.spacetimedb.codegen.schema.SumType
import dev.sanson.spacetimedb.codegen.schema.SumTypeVariant
import dev.sanson.spacetimedb.codegen.schema.Typespace
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeMapperTest {

    private fun mapperWith(vararg types: AlgebraicType): TypeMapper {
        val schema = schemaWith(types = types.toList())
        return TypeMapper(schema, "com.example")
    }

    @Test
    fun `maps primitive types to Kotlin equivalents`() {
        val mapper = mapperWith()

        assertEquals(Boolean::class.asTypeName(), mapper.typeName(AlgebraicType.Bool))
        assertEquals(Byte::class.asTypeName(), mapper.typeName(AlgebraicType.I8))
        assertEquals(UByte::class.asTypeName(), mapper.typeName(AlgebraicType.U8))
        assertEquals(Short::class.asTypeName(), mapper.typeName(AlgebraicType.I16))
        assertEquals(UShort::class.asTypeName(), mapper.typeName(AlgebraicType.U16))
        assertEquals(Int::class.asTypeName(), mapper.typeName(AlgebraicType.I32))
        assertEquals(UInt::class.asTypeName(), mapper.typeName(AlgebraicType.U32))
        assertEquals(Long::class.asTypeName(), mapper.typeName(AlgebraicType.I64))
        assertEquals(ULong::class.asTypeName(), mapper.typeName(AlgebraicType.U64))
        assertEquals(Float::class.asTypeName(), mapper.typeName(AlgebraicType.F32))
        assertEquals(Double::class.asTypeName(), mapper.typeName(AlgebraicType.F64))
        assertEquals(String::class.asTypeName(), mapper.typeName(AlgebraicType.StringType))
    }

    @Test
    fun `maps big integers to SDK types`() {
        val mapper = mapperWith()

        assertEquals(
            ClassName("dev.sanson.spacetimedb.bsatn", "I128"),
            mapper.typeName(AlgebraicType.I128),
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb.bsatn", "U128"),
            mapper.typeName(AlgebraicType.U128),
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb.bsatn", "I256"),
            mapper.typeName(AlgebraicType.I256),
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb.bsatn", "U256"),
            mapper.typeName(AlgebraicType.U256),
        )
    }

    @Test
    fun `maps Array of U8 to ByteArray`() {
        val mapper = mapperWith()
        assertEquals(
            ByteArray::class.asTypeName(),
            mapper.typeName(AlgebraicType.Array(AlgebraicType.U8)),
        )
    }

    @Test
    fun `maps Array of T to List of T`() {
        val mapper = mapperWith()
        assertEquals(
            List::class.asTypeName().parameterizedBy(String::class.asTypeName()),
            mapper.typeName(AlgebraicType.Array(AlgebraicType.StringType)),
        )
    }

    @Test
    fun `maps nested Array to List of List`() {
        val mapper = mapperWith()
        val type = AlgebraicType.Array(AlgebraicType.Array(AlgebraicType.I32))
        assertEquals(
            List::class.asTypeName().parameterizedBy(
                List::class.asTypeName().parameterizedBy(Int::class.asTypeName())
            ),
            mapper.typeName(type),
        )
    }

    @Test
    fun `maps Option to nullable type`() {
        val mapper = mapperWith()
        val option = AlgebraicType.Sum(
            SumType(
                listOf(
                    SumTypeVariant("some", AlgebraicType.StringType),
                    SumTypeVariant("none", AlgebraicType.Product(ProductType(emptyList()))),
                )
            )
        )
        assertEquals(
            String::class.asTypeName().copy(nullable = true),
            mapper.typeName(option),
        )
    }

    @Test
    fun `maps Identity product to SDK Identity`() {
        val mapper = mapperWith()
        val identity = AlgebraicType.Product(
            ProductType(
                listOf(ProductTypeElement("__identity__", AlgebraicType.U256))
            )
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb", "Identity"),
            mapper.typeName(identity),
        )
    }

    @Test
    fun `maps ConnectionId product to SDK ConnectionId`() {
        val mapper = mapperWith()
        val connId = AlgebraicType.Product(
            ProductType(
                listOf(ProductTypeElement("__connection_id__", AlgebraicType.U128))
            )
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb", "ConnectionId"),
            mapper.typeName(connId),
        )
    }

    @Test
    fun `maps Timestamp product to SDK Timestamp`() {
        val mapper = mapperWith()
        val timestamp = AlgebraicType.Product(
            ProductType(
                listOf(ProductTypeElement("__timestamp_micros_since_unix_epoch__", AlgebraicType.I64))
            )
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb", "Timestamp"),
            mapper.typeName(timestamp),
        )
    }

    @Test
    fun `maps Ref to named type`() {
        // Typespace index 0 = a Product type, and there's a named RawTypeDef for it
        val schema = schemaWith(
            types = listOf(
                AlgebraicType.Product(
                    ProductType(
                        listOf(
                            ProductTypeElement("x", AlgebraicType.I32),
                            ProductTypeElement("y", AlgebraicType.I32),
                        )
                    )
                )
            ),
            namedTypes = listOf(TypeNameEntry(name = "my_point", tyIndex = 0)),
        )
        val mapper = TypeMapper(schema, "com.example")

        assertEquals(
            ClassName("com.example", "MyPoint"),
            mapper.typeName(AlgebraicType.Ref(0)),
        )
    }

    @Test
    fun `maps Ref to Identity when type is special`() {
        val schema = schemaWith(
            types = listOf(
                AlgebraicType.Product(
                    ProductType(
                        listOf(ProductTypeElement("__identity__", AlgebraicType.U256))
                    )
                )
            ),
            namedTypes = listOf(TypeNameEntry(name = "Identity", tyIndex = 0)),
        )
        val mapper = TypeMapper(schema, "com.example")

        // Even though there's a named type, Identity resolves to the SDK type
        assertEquals(
            ClassName("dev.sanson.spacetimedb", "Identity"),
            mapper.typeName(AlgebraicType.Ref(0)),
        )
    }

    @Test
    fun `maps ScheduleAt to SDK type`() {
        val mapper = mapperWith()
        val scheduleAt = AlgebraicType.Sum(
            SumType(
                listOf(
                    SumTypeVariant(
                        "Interval",
                        AlgebraicType.Product(
                            ProductType(listOf(ProductTypeElement("__time_duration_micros__", AlgebraicType.I64)))
                        ),
                    ),
                    SumTypeVariant(
                        "Time",
                        AlgebraicType.Product(
                            ProductType(listOf(ProductTypeElement("__timestamp_micros_since_unix_epoch__", AlgebraicType.I64)))
                        ),
                    ),
                )
            )
        )
        assertEquals(
            ClassName("dev.sanson.spacetimedb", "ScheduleAt"),
            mapper.typeName(scheduleAt),
        )
    }
}
