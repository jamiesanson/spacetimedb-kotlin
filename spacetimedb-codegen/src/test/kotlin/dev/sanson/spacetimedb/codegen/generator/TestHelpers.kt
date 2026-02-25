package dev.sanson.spacetimedb.codegen.generator

import dev.sanson.spacetimedb.codegen.schema.AlgebraicType
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.RawModuleDefV10
import dev.sanson.spacetimedb.codegen.schema.RawModuleDefV10Section
import dev.sanson.spacetimedb.codegen.schema.RawTypeDef
import dev.sanson.spacetimedb.codegen.schema.ScopedTypeName
import dev.sanson.spacetimedb.codegen.schema.Typespace

/**
 * Lightweight named-type entry for tests.
 */
data class TypeNameEntry(val name: String, val tyIndex: Int)

/**
 * Build a [ModuleSchema] from in-memory data for testing.
 */
fun schemaWith(
    types: List<AlgebraicType> = emptyList(),
    namedTypes: List<TypeNameEntry> = emptyList(),
): ModuleSchema {
    val sections = buildList {
        add(RawModuleDefV10Section.TypespaceSection(Typespace(types)))

        if (namedTypes.isNotEmpty()) {
            add(
                RawModuleDefV10Section.TypesSection(
                    namedTypes.map { entry ->
                        RawTypeDef(
                            sourceName = ScopedTypeName(scope = emptyList(), sourceName = entry.name),
                            ty = entry.tyIndex,
                            customOrdering = false,
                        )
                    }
                )
            )
        }
    }

    return ModuleSchema(RawModuleDefV10(sections))
}
