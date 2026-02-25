package dev.sanson.spacetimedb.codegen.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The SpacetimeDB algebraic type system.
 *
 * Represents all types expressible in SpacetimeDB's SATS (SpacetimeDB Algebraic Type System).
 * Serialized as a JSON tagged union: `{"VariantName": payload}` where unit types use `[]`.
 */
@Serializable(with = AlgebraicTypeSerializer::class)
public sealed interface AlgebraicType {
    /** Reference to a type in the [Typespace] by index. */
    public data class Ref(val id: Int) : AlgebraicType

    /** Sum type (tagged union / enum). */
    public data class Sum(val type: SumType) : AlgebraicType

    /** Product type (struct / record). */
    public data class Product(val type: ProductType) : AlgebraicType

    /** Homogeneous array type. */
    public data class Array(val elementType: AlgebraicType) : AlgebraicType

    // Primitive types
    public data object Bool : AlgebraicType
    public data object I8 : AlgebraicType
    public data object U8 : AlgebraicType
    public data object I16 : AlgebraicType
    public data object U16 : AlgebraicType
    public data object I32 : AlgebraicType
    public data object U32 : AlgebraicType
    public data object I64 : AlgebraicType
    public data object U64 : AlgebraicType
    public data object I128 : AlgebraicType
    public data object U128 : AlgebraicType
    public data object I256 : AlgebraicType
    public data object U256 : AlgebraicType
    public data object F32 : AlgebraicType
    public data object F64 : AlgebraicType
    public data object StringType : AlgebraicType
}

/**
 * A sum type: a tagged union of named variants.
 */
@Serializable
public data class SumType(
    val variants: List<SumTypeVariant>,
)

/**
 * A single variant of a [SumType].
 */
@Serializable
public data class SumTypeVariant(
    @Serializable(with = OptionStringSerializer::class)
    val name: String?,
    @SerialName("algebraic_type")
    @Serializable(with = AlgebraicTypeSerializer::class)
    val algebraicType: AlgebraicType,
)

/**
 * A product type: a record of named fields.
 */
@Serializable
public data class ProductType(
    val elements: List<ProductTypeElement>,
)

/**
 * A single field of a [ProductType].
 */
@Serializable
public data class ProductTypeElement(
    @Serializable(with = OptionStringSerializer::class)
    val name: String?,
    @SerialName("algebraic_type")
    @Serializable(with = AlgebraicTypeSerializer::class)
    val algebraicType: AlgebraicType,
)

/**
 * Serializer for [AlgebraicType].
 *
 * SATS algebraic types serialize as JSON tagged unions:
 * - Unit types: `{"Bool": []}`, `{"U32": []}`, `{"String": []}`
 * - Ref: `{"Ref": 42}`
 * - Sum: `{"Sum": {"variants": [...]}}`
 * - Product: `{"Product": {"elements": [...]}}`
 * - Array: `{"Array": {"U8": []}}` (element type directly)
 */
internal object AlgebraicTypeSerializer : KSerializer<AlgebraicType> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AlgebraicType")

    override fun serialize(encoder: Encoder, value: AlgebraicType) {
        throw UnsupportedOperationException("AlgebraicType serialization not needed for codegen")
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): AlgebraicType {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("AlgebraicType can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val (key, value) = jsonObject.entries.singleOrNull()
            ?: throw SerializationException("Expected single-key object for AlgebraicType, got: ${jsonObject.keys}")

        return when (key) {
            "Ref" -> AlgebraicType.Ref(jsonDecoder.json.decodeFromJsonElement(Int.serializer(), value))
            "Sum" -> AlgebraicType.Sum(jsonDecoder.json.decodeFromJsonElement(SumType.serializer(), value))
            "Product" -> AlgebraicType.Product(jsonDecoder.json.decodeFromJsonElement(ProductType.serializer(), value))
            "Array" -> AlgebraicType.Array(jsonDecoder.json.decodeFromJsonElement(AlgebraicTypeSerializer, value))
            "Bool" -> AlgebraicType.Bool
            "I8" -> AlgebraicType.I8
            "U8" -> AlgebraicType.U8
            "I16" -> AlgebraicType.I16
            "U16" -> AlgebraicType.U16
            "I32" -> AlgebraicType.I32
            "U32" -> AlgebraicType.U32
            "I64" -> AlgebraicType.I64
            "U64" -> AlgebraicType.U64
            "I128" -> AlgebraicType.I128
            "U128" -> AlgebraicType.U128
            "I256" -> AlgebraicType.I256
            "U256" -> AlgebraicType.U256
            "F32" -> AlgebraicType.F32
            "F64" -> AlgebraicType.F64
            "String" -> AlgebraicType.StringType
            else -> throw SerializationException("Unknown AlgebraicType variant: $key")
        }
    }
}

/**
 * Serializer for SATS `Option<String>` which encodes as `{"some": "value"}` or `{"none": {}}`.
 */
internal object OptionStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("OptionString")

    override fun serialize(encoder: Encoder, value: String?) {
        throw UnsupportedOperationException("OptionString serialization not needed for codegen")
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("OptionString can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "some" in jsonObject -> jsonDecoder.json.decodeFromJsonElement(String.serializer(), jsonObject["some"]!!)
            "none" in jsonObject -> null
            else -> throw SerializationException("Expected 'some' or 'none' for Option, got: ${jsonObject.keys}")
        }
    }
}
