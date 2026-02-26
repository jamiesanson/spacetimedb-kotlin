package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.ProductType
import dev.sanson.spacetimedb.codegen.schema.RawTableDef

// SDK types
private val EVENT = ClassName("dev.sanson.spacetimedb", "Event")
private val CALLBACK_ID = ClassName("dev.sanson.spacetimedb", "CallbackId")
private val TABLE = ClassName("dev.sanson.spacetimedb", "Table")
private val TABLE_WITH_PK = ClassName("dev.sanson.spacetimedb", "TableWithPrimaryKey")
private val EVENT_TABLE = ClassName("dev.sanson.spacetimedb", "EventTable")
private val CLIENT_CACHE = ClassName("dev.sanson.spacetimedb", "ClientCache")
private val DB_CALLBACKS = ClassName("dev.sanson.spacetimedb", "DbCallbacks")
private val UNIQUE_INDEX = ClassName("dev.sanson.spacetimedb", "UniqueIndex")

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

    // -- Implementation generation --

    /**
     * Generate implementation files for all public table handles.
     */
    public fun generateTableHandleImplFiles(): List<FileSpec> =
        schema.publicTables.map { table ->
            val productType = schema.tableProductType(table)
            generateTableHandleImplFile(table, productType)
        }

    /**
     * Generate a concrete implementation of a table handle interface.
     */
    public fun generateTableHandleImplFile(table: RawTableDef, productType: ProductType): FileSpec {
        val tablePascal = table.sourceName.toPascalCase()
        val className = "${tablePascal}TableHandleImpl"
        val handleInterface = ClassName(targetPackage, "${tablePascal}TableHandle")
        val rowClass = ClassName(targetPackage, tablePascal)

        val hasPrimaryKey = table.primaryKey.isNotEmpty()
        val isEvent = table.isEvent
        val uniqueColumns = uniqueColumnIndices(table)

        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(handleInterface)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("cache", CLIENT_CACHE)
                    .addParameter("callbacks", DB_CALLBACKS)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("cache", CLIENT_CACHE)
                    .initializer("cache")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("callbacks", DB_CALLBACKS)
                    .initializer("callbacks")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("tableName", String::class)
                    .initializer("%S", table.sourceName)
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        if (!isEvent) {
            // Non-event tables get a tableCache property
            classBuilder.addProperty(
                PropertySpec.builder("tableCache", ClassName("dev.sanson.spacetimedb", "TableCache").parameterizedBy(rowClass))
                    .initializer(CodeBlock.of("cache.getOrCreateTable<%T>(tableName)", rowClass))
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

            // Unique indexes
            for (colIndex in uniqueColumns) {
                val element = productType.elements.getOrNull(colIndex) ?: continue
                val fieldName = element.name ?: continue
                val fieldType = typeMapper.typeName(element.algebraicType)
                val indexPropName = "${fieldName.toCamelCase()}Index"

                classBuilder.addProperty(
                    PropertySpec.builder(
                        indexPropName,
                        UNIQUE_INDEX.parameterizedBy(rowClass, fieldType)
                    )
                        .initializer(
                            CodeBlock.of(
                                "tableCache.registerUniqueIndex(%T { it.%N })",
                                UNIQUE_INDEX.parameterizedBy(rowClass, fieldType),
                                fieldName.toCamelCase()
                            )
                        )
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
            }

            // count
            classBuilder.addProperty(
                PropertySpec.builder("count", Int::class)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addCode("return tableCache.count").build())
                    .build()
            )

            // iterator()
            classBuilder.addFunction(
                FunSpec.builder("iterator")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .returns(Iterator::class.asClassName().parameterizedBy(rowClass))
                    .addCode("return tableCache.iterator()")
                    .build()
            )

            // onInsert / removeOnInsert
            classBuilder.addFunction(buildRowCallbackFun("onInsert", "registerOnInsert", rowClass))
            classBuilder.addFunction(buildRemoveCallbackFun("removeOnInsert", "removeOnInsert"))

            // onDelete / removeOnDelete
            classBuilder.addFunction(buildRowCallbackFun("onDelete", "registerOnDelete", rowClass))
            classBuilder.addFunction(buildRemoveCallbackFun("removeOnDelete", "removeOnDelete"))

            // onUpdate / removeOnUpdate (only for PK tables)
            if (hasPrimaryKey) {
                classBuilder.addFunction(buildUpdateCallbackFun(rowClass))
                classBuilder.addFunction(buildRemoveCallbackFun("removeOnUpdate", "removeOnUpdate"))
            }

            // findByX methods
            for (colIndex in uniqueColumns) {
                val element = productType.elements.getOrNull(colIndex) ?: continue
                val fieldName = element.name ?: continue
                val fieldType = typeMapper.typeName(element.algebraicType)
                val methodName = "findBy${fieldName.toPascalCase()}"
                val indexPropName = "${fieldName.toCamelCase()}Index"

                classBuilder.addFunction(
                    FunSpec.builder(methodName)
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(fieldName.toCamelCase(), fieldType)
                        .returns(rowClass.copy(nullable = true))
                        .addCode("return %N.find(%N)", indexPropName, fieldName.toCamelCase())
                        .build()
                )
            }
        } else {
            // Event table: onEvent / removeOnEvent using insert callbacks internally
            classBuilder.addFunction(buildRowCallbackFun("onEvent", "registerOnInsert", rowClass))
            classBuilder.addFunction(buildRemoveCallbackFun("removeOnEvent", "removeOnInsert"))
        }

        return FileSpec.builder(targetPackage, className)
            .addType(classBuilder.build())
            .build()
    }

    /**
     * Generate a `RemoteTablesImpl` class implementing `RemoteTables`.
     */
    public fun generateRemoteTablesImplFile(): FileSpec {
        val remoteTablesInterface = ClassName(targetPackage, "RemoteTables")

        val classBuilder = TypeSpec.classBuilder("RemoteTablesImpl")
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(remoteTablesInterface)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("cache", CLIENT_CACHE)
                    .addParameter("callbacks", DB_CALLBACKS)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("cache", CLIENT_CACHE)
                    .initializer("cache")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("callbacks", DB_CALLBACKS)
                    .initializer("callbacks")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        for (table in schema.publicTables) {
            val tablePascal = table.sourceName.toPascalCase()
            val handleClass = ClassName(targetPackage, "${tablePascal}TableHandle")
            val implClass = ClassName(targetPackage, "${tablePascal}TableHandleImpl")
            val propName = table.sourceName.toCamelCase()

            classBuilder.addProperty(
                PropertySpec.builder(propName, handleClass)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .delegate(CodeBlock.of("lazy { %T(cache, callbacks) }", implClass))
                    .build()
            )
        }

        return FileSpec.builder(targetPackage, "RemoteTablesImpl")
            .addType(classBuilder.build())
            .build()
    }

    // -- Helper methods for generating callback functions --

    private fun buildRowCallbackFun(methodName: String, delegateMethod: String, rowClass: ClassName): FunSpec {
        val callbackType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("event", EVENT.parameterizedBy(STAR)).build(),
                ParameterSpec.builder("row", rowClass).build(),
            ),
            returnType = UNIT,
        )
        return FunSpec.builder(methodName)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("callback", callbackType)
            .returns(CALLBACK_ID)
            .addCode("return callbacks.%N(tableName, callback)", delegateMethod)
            .build()
    }

    private fun buildUpdateCallbackFun(rowClass: ClassName): FunSpec {
        val callbackType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("event", EVENT.parameterizedBy(STAR)).build(),
                ParameterSpec.builder("oldRow", rowClass).build(),
                ParameterSpec.builder("newRow", rowClass).build(),
            ),
            returnType = UNIT,
        )
        return FunSpec.builder("onUpdate")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("callback", callbackType)
            .returns(CALLBACK_ID)
            .addCode("return callbacks.registerOnUpdate(tableName, callback)")
            .build()
    }

    private fun buildRemoveCallbackFun(methodName: String, delegateMethod: String): FunSpec =
        FunSpec.builder(methodName)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("id", CALLBACK_ID)
            .addCode("callbacks.%N(tableName, id)", delegateMethod)
            .build()

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
