package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import dev.sanson.spacetimedb.codegen.schema.AlgebraicType
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.asOption
import dev.sanson.spacetimedb.codegen.schema.isConnectionId
import dev.sanson.spacetimedb.codegen.schema.isIdentity
import dev.sanson.spacetimedb.codegen.schema.isScheduleAt
import dev.sanson.spacetimedb.codegen.schema.isTimeDuration
import dev.sanson.spacetimedb.codegen.schema.isTimestamp

// SDK type references
private val IDENTITY_CLASS = ClassName("dev.sanson.spacetimedb", "Identity")
private val CONNECTION_ID_CLASS = ClassName("dev.sanson.spacetimedb", "ConnectionId")
private val TIMESTAMP_CLASS = ClassName("dev.sanson.spacetimedb", "Timestamp")
private val TIME_DURATION_CLASS = ClassName("dev.sanson.spacetimedb", "TimeDuration")
private val SCHEDULE_AT_CLASS = ClassName("dev.sanson.spacetimedb", "ScheduleAt")
private val U128_CLASS = ClassName("dev.sanson.spacetimedb.bsatn", "U128")
private val I128_CLASS = ClassName("dev.sanson.spacetimedb.bsatn", "I128")
private val U256_CLASS = ClassName("dev.sanson.spacetimedb.bsatn", "U256")
private val I256_CLASS = ClassName("dev.sanson.spacetimedb.bsatn", "I256")

/**
 * Maps SpacetimeDB [AlgebraicType]s to KotlinPoet [TypeName]s.
 *
 * Resolves type references through the schema's typespace, detects special
 * types (Identity, ConnectionId, Timestamp, etc.), and maps Options to nullable types.
 */
public class TypeMapper(
    private val schema: ModuleSchema,
    private val targetPackage: String,
) {
    /**
     * Convert an [AlgebraicType] to a KotlinPoet [TypeName].
     *
     * Resolves Refs, detects special types, and maps Options to nullables.
     */
    public fun typeName(type: AlgebraicType): TypeName {
        // Resolve Ref through typespace, checking for named types first
        if (type is AlgebraicType.Ref) {
            val resolved = schema.resolveType(type)
            return resolvedTypeName(resolved, refId = type.id)
        }
        return resolvedTypeName(type, refId = null)
    }

    private fun resolvedTypeName(type: AlgebraicType, refId: Int?): TypeName {
        // Check special types first (these are structural matches on the resolved type)
        if (type.isIdentity) return IDENTITY_CLASS
        if (type.isConnectionId) return CONNECTION_ID_CLASS
        if (type.isTimestamp) return TIMESTAMP_CLASS
        if (type.isTimeDuration) return TIME_DURATION_CLASS
        if (type.isScheduleAt) return SCHEDULE_AT_CLASS

        // Check Option<T> → T?
        val optionInner = type.asOption()
        if (optionInner != null) {
            return typeName(optionInner).copy(nullable = true)
        }

        // If this came from a Ref and there's a named type, use its name
        if (refId != null) {
            val namedType = schema.namedTypeForRef(refId)
            if (namedType != null) {
                val name = namedType.sourceName.sourceName.toPascalCase()
                return ClassName(targetPackage, name)
            }
        }

        return when (type) {
            is AlgebraicType.Bool -> Boolean::class.asTypeName()
            is AlgebraicType.I8 -> Byte::class.asTypeName()
            is AlgebraicType.U8 -> UByte::class.asTypeName()
            is AlgebraicType.I16 -> Short::class.asTypeName()
            is AlgebraicType.U16 -> UShort::class.asTypeName()
            is AlgebraicType.I32 -> Int::class.asTypeName()
            is AlgebraicType.U32 -> UInt::class.asTypeName()
            is AlgebraicType.I64 -> Long::class.asTypeName()
            is AlgebraicType.U64 -> ULong::class.asTypeName()
            is AlgebraicType.I128 -> I128_CLASS
            is AlgebraicType.U128 -> U128_CLASS
            is AlgebraicType.I256 -> I256_CLASS
            is AlgebraicType.U256 -> U256_CLASS
            is AlgebraicType.F32 -> Float::class.asTypeName()
            is AlgebraicType.F64 -> Double::class.asTypeName()
            is AlgebraicType.StringType -> String::class.asTypeName()

            is AlgebraicType.Array -> {
                // Special case: Array<U8> → ByteArray
                if (type.elementType is AlgebraicType.U8) {
                    ByteArray::class.asTypeName()
                } else {
                    List::class.asTypeName().parameterizedBy(typeName(type.elementType))
                }
            }

            is AlgebraicType.Product -> {
                // Anonymous product type — shouldn't happen for named types
                // (those are caught by the Ref path above)
                throw IllegalArgumentException("Unnamed product type encountered; expected a Ref to a named type")
            }

            is AlgebraicType.Sum -> {
                // Anonymous sum type — shouldn't happen for named types
                throw IllegalArgumentException("Unnamed sum type encountered; expected a Ref to a named type")
            }

            is AlgebraicType.Ref -> {
                // Should not reach here — Refs are resolved at the top of typeName()
                throw IllegalStateException("Unresolved Ref(${type.id})")
            }
        }
    }
}

/**
 * Known acronyms that should be treated as separate words within a
 * snake_case segment (e.g. "webrtc" → "web" + "rtc" → "WebRtc").
 */
private val KNOWN_ACRONYMS = listOf(
    "https", "http", "api", "db", "rtc", "sql", "tcp", "udp", "url",
)

/**
 * Split a single segment on known acronym boundaries.
 * E.g. "webrtc" → ["web", "rtc"], "api" → ["api"].
 */
private fun String.splitOnAcronyms(): List<String> {
    val lower = lowercase()
    for (acronym in KNOWN_ACRONYMS) {
        if (lower.length > acronym.length) {
            if (lower.endsWith(acronym)) {
                val prefix = substring(0, length - acronym.length)
                return prefix.splitOnAcronyms() + listOf(acronym)
            }
            if (lower.startsWith(acronym)) {
                val suffix = substring(acronym.length)
                return listOf(acronym) + suffix.splitOnAcronyms()
            }
        }
    }
    return listOf(this)
}

/**
 * Convert a snake_case or camelCase string to PascalCase.
 */
internal fun String.toPascalCase(): String =
    split("_", "-")
        .flatMap { it.splitOnAcronyms() }
        .joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercase() }
        }

/**
 * Convert a snake_case string to camelCase.
 */
internal fun String.toCamelCase(): String {
    val pascal = toPascalCase()
    return pascal.replaceFirstChar { it.lowercase() }
}
