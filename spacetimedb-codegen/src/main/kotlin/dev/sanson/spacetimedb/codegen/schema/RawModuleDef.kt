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
import kotlinx.serialization.json.jsonObject

/**
 * Raw module definition v10 — the top-level schema format output by
 * `spacetimedb-standalone extract-schema`.
 *
 * The JSON envelope is `{"V10": {"sections": [...]}}`.
 */
@Serializable
public data class RawModuleDef(
    val V10: RawModuleDefV10,
)

@Serializable
public data class RawModuleDefV10(
    val sections: List<@Serializable(with = RawModuleDefV10SectionSerializer::class) RawModuleDefV10Section>,
)

/**
 * A single section of the V10 module definition.
 */
public sealed interface RawModuleDefV10Section {
    public data class TypespaceSection(val typespace: Typespace) : RawModuleDefV10Section
    public data class TypesSection(val types: List<RawTypeDef>) : RawModuleDefV10Section
    public data class TablesSection(val tables: List<RawTableDef>) : RawModuleDefV10Section
    public data class ReducersSection(val reducers: List<RawReducerDef>) : RawModuleDefV10Section
    public data class ProceduresSection(val procedures: List<RawProcedureDef>) : RawModuleDefV10Section
    public data class ViewsSection(val views: List<RawViewDef>) : RawModuleDefV10Section
    public data class SchedulesSection(val schedules: List<RawScheduleDef>) : RawModuleDefV10Section
    public data class LifeCycleReducersSection(val reducers: List<RawLifeCycleReducerDef>) : RawModuleDefV10Section
    public data class ExplicitNamesSection(val names: ExplicitNames) : RawModuleDefV10Section

    /** Sections we don't need to parse for codegen. */
    public data class Unknown(val key: String) : RawModuleDefV10Section
}

// --- Typespace ---

/**
 * The typespace: an indexed collection of all algebraic types in the module.
 * Types are referenced by [AlgebraicType.Ref] using their index in this list.
 */
@Serializable
public data class Typespace(
    @Serializable(with = AlgebraicTypeListSerializer::class)
    val types: List<AlgebraicType>,
)

internal object AlgebraicTypeListSerializer : KSerializer<List<AlgebraicType>> {
    private val delegate = ListSerializer(AlgebraicTypeSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<AlgebraicType>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<AlgebraicType> = delegate.deserialize(decoder)
}

// --- Table ---

@Serializable
public data class RawTableDef(
    @SerialName("source_name")
    val sourceName: String,
    @SerialName("product_type_ref")
    val productTypeRef: Int,
    @SerialName("primary_key")
    val primaryKey: List<Int>,
    val indexes: List<RawIndexDef>,
    val constraints: List<RawConstraintDef>,
    val sequences: List<RawSequenceDef>,
    @SerialName("table_type")
    @Serializable(with = TaggedUnitSerializer::class)
    val tableType: String,
    @SerialName("table_access")
    @Serializable(with = TaggedUnitSerializer::class)
    val tableAccess: String,
    @SerialName("is_event")
    val isEvent: Boolean,
)

@Serializable
public data class RawIndexDef(
    @SerialName("source_name")
    @Serializable(with = OptionStringSerializer::class)
    val sourceName: String?,
    @SerialName("accessor_name")
    @Serializable(with = OptionStringSerializer::class)
    val accessorName: String?,
    @Serializable(with = IndexAlgorithmSerializer::class)
    val algorithm: IndexAlgorithm,
)

public data class IndexAlgorithm(
    val type: String,
    val columns: List<Int>,
)

@Serializable
public data class RawConstraintDef(
    @SerialName("source_name")
    @Serializable(with = OptionStringSerializer::class)
    val sourceName: String?,
    @Serializable(with = ConstraintDataSerializer::class)
    val data: ConstraintData,
)

public data class ConstraintData(
    val type: String,
    val columns: List<Int>,
)

@Serializable
public data class RawSequenceDef(
    @SerialName("source_name")
    @Serializable(with = OptionStringSerializer::class)
    val sourceName: String?,
    val column: Int,
    val increment: Int = 1,
)

// --- Reducer ---

@Serializable
public data class RawReducerDef(
    @SerialName("source_name")
    val sourceName: String,
    val params: ProductType,
    @Serializable(with = TaggedUnitSerializer::class)
    val visibility: String,
    @SerialName("ok_return_type")
    @Serializable(with = AlgebraicTypeSerializer::class)
    val okReturnType: AlgebraicType,
    @SerialName("err_return_type")
    @Serializable(with = AlgebraicTypeSerializer::class)
    val errReturnType: AlgebraicType,
)

// --- Procedure ---

@Serializable
public data class RawProcedureDef(
    @SerialName("source_name")
    val sourceName: String,
    val params: ProductType,
    @Serializable(with = TaggedUnitSerializer::class)
    val visibility: String,
    @SerialName("return_type")
    @Serializable(with = AlgebraicTypeSerializer::class)
    val returnType: AlgebraicType,
)

// --- View ---

@Serializable
public data class RawViewDef(
    @SerialName("source_name")
    val sourceName: String,
    val index: Int,
    @SerialName("is_public")
    val isPublic: Boolean,
    @SerialName("is_anonymous")
    val isAnonymous: Boolean = false,
    val params: ProductType,
    @SerialName("return_type")
    @Serializable(with = AlgebraicTypeSerializer::class)
    val returnType: AlgebraicType,
)

// --- Schedule ---

@Serializable
public data class RawScheduleDef(
    @SerialName("source_name")
    @Serializable(with = OptionStringSerializer::class)
    val sourceName: String?,
    @SerialName("table_name")
    val tableName: String,
    @SerialName("schedule_at_col")
    val scheduleAtCol: Int,
    @SerialName("function_name")
    val functionName: String,
)

// --- Lifecycle ---

@Serializable
public data class RawLifeCycleReducerDef(
    @SerialName("lifecycle_spec")
    @Serializable(with = TaggedUnitSerializer::class)
    val lifecycleSpec: String,
    @SerialName("function_name")
    val functionName: String,
)

// --- Explicit names ---

@Serializable
public data class ExplicitNames(
    val entries: List<@Serializable(with = ExplicitNameEntrySerializer::class) ExplicitNameEntry>,
)

public data class ExplicitNameEntry(
    val kind: String,
    val sourceName: String,
    val canonicalName: String,
)

// --- Section serializer ---

internal object RawModuleDefV10SectionSerializer : KSerializer<RawModuleDefV10Section> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RawModuleDefV10Section")

    override fun serialize(encoder: Encoder, value: RawModuleDefV10Section) {
        throw UnsupportedOperationException("Section serialization not needed for codegen")
    }

    override fun deserialize(decoder: Decoder): RawModuleDefV10Section {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Section can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val (key, value) = jsonObject.entries.singleOrNull()
            ?: throw SerializationException("Expected single-key object for Section, got: ${jsonObject.keys}")

        return when (key) {
            "Typespace" -> RawModuleDefV10Section.TypespaceSection(
                jsonDecoder.json.decodeFromJsonElement(Typespace.serializer(), value)
            )
            "Types" -> RawModuleDefV10Section.TypesSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawTypeDef.serializer()), value)
            )
            "Tables" -> RawModuleDefV10Section.TablesSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawTableDef.serializer()), value)
            )
            "Reducers" -> RawModuleDefV10Section.ReducersSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawReducerDef.serializer()), value)
            )
            "Procedures" -> RawModuleDefV10Section.ProceduresSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawProcedureDef.serializer()), value)
            )
            "Views" -> RawModuleDefV10Section.ViewsSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawViewDef.serializer()), value)
            )
            "Schedules" -> RawModuleDefV10Section.SchedulesSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawScheduleDef.serializer()), value)
            )
            "LifeCycleReducers" -> RawModuleDefV10Section.LifeCycleReducersSection(
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(RawLifeCycleReducerDef.serializer()), value)
            )
            "ExplicitNames" -> RawModuleDefV10Section.ExplicitNamesSection(
                jsonDecoder.json.decodeFromJsonElement(ExplicitNames.serializer(), value)
            )
            else -> RawModuleDefV10Section.Unknown(key)
        }
    }
}

// --- Type definitions ---

@Serializable
public data class RawTypeDef(
    @SerialName("source_name")
    val sourceName: ScopedTypeName,
    val ty: Int,
    @SerialName("custom_ordering")
    val customOrdering: Boolean,
)

@Serializable
public data class ScopedTypeName(
    val scope: List<String>,
    @SerialName("source_name")
    val sourceName: String,
)

// --- Helper serializers ---

/**
 * Serializer for SATS enums that serialize as `{"VariantName": []}`.
 * Extracts just the variant name as a [String].
 */
internal object TaggedUnitSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("TaggedUnit")

    override fun serialize(encoder: Encoder, value: String) {
        throw UnsupportedOperationException("TaggedUnit serialization not needed for codegen")
    }

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("TaggedUnit can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return jsonObject.keys.singleOrNull()
            ?: throw SerializationException("Expected single-key object for TaggedUnit, got: ${jsonObject.keys}")
    }
}

/**
 * Serializer for index algorithms: `{"BTree": [col1, col2]}`.
 */
internal object IndexAlgorithmSerializer : KSerializer<IndexAlgorithm> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("IndexAlgorithm")

    override fun serialize(encoder: Encoder, value: IndexAlgorithm) {
        throw UnsupportedOperationException("IndexAlgorithm serialization not needed for codegen")
    }

    override fun deserialize(decoder: Decoder): IndexAlgorithm {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("IndexAlgorithm can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val (key, value) = jsonObject.entries.singleOrNull()
            ?: throw SerializationException("Expected single-key object for IndexAlgorithm, got: ${jsonObject.keys}")

        val columns = jsonDecoder.json.decodeFromJsonElement(ListSerializer(Int.serializer()), value)
        return IndexAlgorithm(type = key, columns = columns)
    }
}

/**
 * Serializer for constraint data: `{"Unique": {"columns": [0, 1]}}`.
 */
internal object ConstraintDataSerializer : KSerializer<ConstraintData> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ConstraintData")

    override fun serialize(encoder: Encoder, value: ConstraintData) {
        throw UnsupportedOperationException("ConstraintData serialization not needed for codegen")
    }

    override fun deserialize(decoder: Decoder): ConstraintData {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("ConstraintData can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val (key, value) = jsonObject.entries.singleOrNull()
            ?: throw SerializationException("Expected single-key object for ConstraintData, got: ${jsonObject.keys}")

        val inner = value.jsonObject
        val columns = jsonDecoder.json.decodeFromJsonElement(
            ListSerializer(Int.serializer()),
            inner["columns"]!!
        )
        return ConstraintData(type = key, columns = columns)
    }
}

/**
 * Serializer for explicit name entries: `{"Table": {"source_name": "...", "canonical_name": "..."}}`.
 */
internal object ExplicitNameEntrySerializer : KSerializer<ExplicitNameEntry> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ExplicitNameEntry")

    override fun serialize(encoder: Encoder, value: ExplicitNameEntry) {
        throw UnsupportedOperationException("ExplicitNameEntry serialization not needed for codegen")
    }

    override fun deserialize(decoder: Decoder): ExplicitNameEntry {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("ExplicitNameEntry can only be deserialized from JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val (key, value) = jsonObject.entries.singleOrNull()
            ?: throw SerializationException("Expected single-key object for ExplicitNameEntry, got: ${jsonObject.keys}")

        val inner = value.jsonObject
        return ExplicitNameEntry(
            kind = key,
            sourceName = inner["source_name"]?.let {
                jsonDecoder.json.decodeFromJsonElement(String.serializer(), it)
            } ?: throw SerializationException("Missing source_name in ExplicitNameEntry"),
            canonicalName = inner["canonical_name"]?.let {
                jsonDecoder.json.decodeFromJsonElement(String.serializer(), it)
            } ?: throw SerializationException("Missing canonical_name in ExplicitNameEntry"),
        )
    }
}
