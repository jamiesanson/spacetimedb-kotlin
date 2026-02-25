package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import dev.sanson.spacetimedb.codegen.schema.ConstraintData
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.ProductType
import dev.sanson.spacetimedb.codegen.schema.RawTableDef

// SDK types
private val EVENT = ClassName("dev.sanson.spacetimedb", "Event")
private val CALLBACK_ID = ClassName("dev.sanson.spacetimedb", "CallbackId")
private val TABLE = ClassName("dev.sanson.spacetimedb", "Table")
private val TABLE_WITH_PK = ClassName("dev.sanson.spacetimedb", "TableWithPrimaryKey")
private val EVENT_TABLE = ClassName("dev.sanson.spacetimedb", "EventTable")

/**
 * Generates typed table handle classes per table.
 *
 * Each table handle provides:
 * - `count` / `iterator()` (delegated to the underlying table)
 * - `onInsert` / `onDelete` callbacks
 * - `onUpdate` for tables with primary keys
 * - `findByX()` for each unique constraint column
 */
public class TableHandleGenerator(
    private val schema: ModuleSchema,
    private val targetPackage: String,
) {
    private val typeMapper = TypeMapper(schema, targetPackage)

    /**
     * Generate table handle files for all public tables.
     */
    public fun generateTableHandleFiles(): List<FileSpec> =
        schema.publicTables.map { table ->
            val productType = schema.tableProductType(table)
            generateTableHandleFile(table, productType)
        }

    /**
     * Generate a table handle file for a single table.
     */
    public fun generateTableHandleFile(table: RawTableDef, productType: ProductType): FileSpec {
        val tablePascal = table.sourceName.toPascalCase()
        val className = "${tablePascal}TableHandle"
        val rowClass = ClassName(targetPackage, tablePascal)

        val typeSpec = generateTableHandle(className, rowClass, table, productType)

        return FileSpec.builder(targetPackage, className)
            .addType(typeSpec)
            .build()
    }

    private fun generateTableHandle(
        className: String,
        rowClass: ClassName,
        table: RawTableDef,
        productType: ProductType,
    ): TypeSpec {
        val hasPrimaryKey = table.primaryKey.isNotEmpty()
        val isEvent = table.isEvent

        // Choose the interface to implement
        val superInterface = when {
            isEvent -> EVENT_TABLE.parameterizedBy(rowClass)
            hasPrimaryKey -> TABLE_WITH_PK.parameterizedBy(rowClass)
            else -> TABLE.parameterizedBy(rowClass)
        }

        val builder = TypeSpec.interfaceBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(superInterface)

        // Add findByX() for each unique constraint
        val uniqueColumns = uniqueColumnIndices(table)
        for (colIndex in uniqueColumns) {
            val element = productType.elements.getOrNull(colIndex) ?: continue
            val fieldName = element.name ?: continue
            val fieldType = typeMapper.typeName(element.algebraicType)
            val methodName = "findBy${fieldName.toPascalCase()}"

            builder.addFunction(
                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
                    .addParameter(fieldName.toCamelCase(), fieldType)
                    .returns(rowClass.copy(nullable = true))
                    .build()
            )
        }

        return builder.build()
    }

    /**
     * Generate the RemoteTables container interface with a property per public table.
     */
    public fun generateRemoteTablesFile(): FileSpec {
        val builder = TypeSpec.interfaceBuilder("RemoteTables")
            .addModifiers(KModifier.PUBLIC)

        for (table in schema.publicTables) {
            val tablePascal = table.sourceName.toPascalCase()
            val handleClass = ClassName(targetPackage, "${tablePascal}TableHandle")
            val propName = table.sourceName.toCamelCase()

            builder.addProperty(
                PropertySpec.builder(propName, handleClass)
                    .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
                    .build()
            )
        }

        return FileSpec.builder(targetPackage, "RemoteTables")
            .addType(builder.build())
            .build()
    }

    /**
     * Extract the set of column indices that have unique constraints.
     * Only single-column unique constraints are considered.
     */
    private fun uniqueColumnIndices(table: RawTableDef): List<Int> =
        table.constraints
            .filter { it.data.type == "Unique" && it.data.columns.size == 1 }
            .map { it.data.columns.single() }
            .distinct()
            .sorted()
}
