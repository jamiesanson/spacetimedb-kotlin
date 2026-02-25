package dev.sanson.spacetimedb.codegen.schema

/**
 * A resolved view of a SpacetimeDB module, with convenient accessors
 * over the raw V10 section-based format.
 *
 * Resolves [AlgebraicType.Ref] references via the [Typespace].
 */
public class ModuleSchema(private val raw: RawModuleDefV10) {
    public val typespace: Typespace = raw.sections
        .filterIsInstance<RawModuleDefV10Section.TypespaceSection>()
        .singleOrNull()?.typespace
        ?: Typespace(emptyList())

    public val types: List<RawTypeDef> = raw.sections
        .filterIsInstance<RawModuleDefV10Section.TypesSection>()
        .flatMap { it.types }

    public val tables: List<RawTableDef> = raw.sections
        .filterIsInstance<RawModuleDefV10Section.TablesSection>()
        .flatMap { it.tables }

    public val reducers: List<RawReducerDef> = raw.sections
        .filterIsInstance<RawModuleDefV10Section.ReducersSection>()
        .flatMap { it.reducers }

    public val procedures: List<RawProcedureDef> = raw.sections
        .filterIsInstance<RawModuleDefV10Section.ProceduresSection>()
        .flatMap { it.procedures }

    public val views: List<RawViewDef> = raw.sections
        .filterIsInstance<RawModuleDefV10Section.ViewsSection>()
        .flatMap { it.views }

    public val explicitNames: ExplicitNames? = raw.sections
        .filterIsInstance<RawModuleDefV10Section.ExplicitNamesSection>()
        .singleOrNull()?.names

    /**
     * Resolve an [AlgebraicType.Ref] to the concrete type in the typespace.
     * Follows chains of refs (e.g. Ref → Ref → Product).
     */
    public fun resolveType(type: AlgebraicType): AlgebraicType {
        var current = type
        val visited = mutableSetOf<Int>()
        while (current is AlgebraicType.Ref) {
            if (!visited.add(current.id)) {
                throw IllegalStateException("Cyclic type reference at index ${current.id}")
            }
            current = typespace.types.getOrElse(current.id) {
                throw IllegalStateException("Type ref ${current.id} out of bounds (typespace has ${typespace.types.size} types)")
            }
        }
        return current
    }

    /**
     * Get the product type for a table's row, resolved from the typespace.
     */
    public fun tableProductType(table: RawTableDef): ProductType {
        val resolved = resolveType(AlgebraicType.Ref(table.product_type_ref))
        return (resolved as? AlgebraicType.Product)?.type
            ?: throw IllegalStateException("Table '${table.source_name}' type ref ${table.product_type_ref} resolved to $resolved, expected Product")
    }

    /**
     * Find the [RawTypeDef] (named custom type) for a given typespace index, if one exists.
     */
    public fun namedTypeForRef(ref: Int): RawTypeDef? =
        types.find { it.ty == ref }

    /**
     * Returns only tables with public access (for client codegen).
     */
    public val publicTables: List<RawTableDef>
        get() = tables.filter { it.table_access == "Public" }

    /**
     * Returns only client-callable reducers (for client codegen).
     */
    public val clientCallableReducers: List<RawReducerDef>
        get() = reducers.filter { it.visibility == "ClientCallable" }

    public companion object {
        /**
         * Parse a [ModuleSchema] from the JSON output of `spacetimedb-standalone extract-schema`.
         */
        public fun fromJson(json: String): ModuleSchema {
            val rawDef = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            }.decodeFromString(RawModuleDef.serializer(), json)
            return ModuleSchema(rawDef.V10)
        }
    }
}
