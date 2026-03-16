package dev.sanson.spacetimedb.codegen.schema

/**
 * A resolved view of a SpacetimeDB module, with convenient accessors over the raw V10 section-based
 * format.
 *
 * Resolves [AlgebraicType.Ref] references via the [Typespace].
 */
public class ModuleSchema(private val raw: RawModuleDefV10) {
    public val typespace: Typespace =
        raw.sections
            .filterIsInstance<RawModuleDefV10Section.TypespaceSection>()
            .singleOrNull()
            ?.typespace ?: Typespace(emptyList())

    public val types: List<RawTypeDef> =
        raw.sections.filterIsInstance<RawModuleDefV10Section.TypesSection>().flatMap { it.types }

    public val tables: List<RawTableDef> =
        raw.sections.filterIsInstance<RawModuleDefV10Section.TablesSection>().flatMap { it.tables }

    public val reducers: List<RawReducerDef> =
        raw.sections.filterIsInstance<RawModuleDefV10Section.ReducersSection>().flatMap {
            it.reducers
        }

    public val procedures: List<RawProcedureDef> =
        raw.sections.filterIsInstance<RawModuleDefV10Section.ProceduresSection>().flatMap {
            it.procedures
        }

    public val views: List<RawViewDef> =
        raw.sections.filterIsInstance<RawModuleDefV10Section.ViewsSection>().flatMap { it.views }

    public val lifecycleReducers: List<RawLifeCycleReducerDef> =
        raw.sections.filterIsInstance<RawModuleDefV10Section.LifeCycleReducersSection>().flatMap {
            it.reducers
        }

    public val explicitNames: ExplicitNames? =
        raw.sections
            .filterIsInstance<RawModuleDefV10Section.ExplicitNamesSection>()
            .singleOrNull()
            ?.names

    /**
     * Resolve an [AlgebraicType.Ref] to the concrete type in the typespace. Follows chains of refs
     * (e.g. Ref → Ref → Product).
     */
    public fun resolveType(type: AlgebraicType): AlgebraicType {
        var current = type
        val visited = mutableSetOf<Int>()
        while (current is AlgebraicType.Ref) {
            if (!visited.add(current.id)) {
                throw IllegalStateException("Cyclic type reference at index ${current.id}")
            }
            current =
                typespace.types.getOrElse(current.id) {
                    throw IllegalStateException(
                        "Type ref ${current.id} out of bounds (typespace has ${typespace.types.size} types)"
                    )
                }
        }
        return current
    }

    /** Get the product type for a table's row, resolved from the typespace. */
    public fun tableProductType(table: RawTableDef): ProductType {
        val resolved = resolveType(AlgebraicType.Ref(table.productTypeRef))
        return (resolved as? AlgebraicType.Product)?.type
            ?: throw IllegalStateException(
                "Table '${table.sourceName}' type ref ${table.productTypeRef} resolved to $resolved, expected Product"
            )
    }

    /** Find the [RawTypeDef] (named custom type) for a given typespace index, if one exists. */
    public fun namedTypeForRef(ref: Int): RawTypeDef? = types.find { it.ty == ref }

    /** Returns only tables with public access (for client codegen), sorted alphabetically. */
    public val publicTables: List<RawTableDef>
        get() = tables.filter { it.tableAccess == "Public" }.sortedBy { it.sourceName }

    /**
     * Returns only client-callable reducers (for client codegen), sorted alphabetically. Excludes
     * lifecycle reducers (Init, OnConnect, OnDisconnect).
     */
    public val clientCallableReducers: List<RawReducerDef>
        get() {
            val lifecycleNames = lifecycleReducers.map { it.functionName }.toSet()
            return reducers
                .filter { it.visibility == "ClientCallable" && it.sourceName !in lifecycleNames }
                .sortedBy { it.sourceName }
        }

    /** Returns named custom types sorted alphabetically. */
    public val sortedTypes: List<RawTypeDef>
        get() = types.sortedBy { it.sourceName.sourceName }

    public companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        /**
         * Parse a [ModuleSchema] from the JSON output of `spacetimedb-standalone extract-schema`.
         */
        public fun fromJson(json: String): ModuleSchema {
            val rawDef = this.json.decodeFromString(RawModuleDef.serializer(), json)
            return ModuleSchema(rawDef.V10)
        }
    }
}
