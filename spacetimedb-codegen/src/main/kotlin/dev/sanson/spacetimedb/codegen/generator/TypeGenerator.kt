package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.sanson.spacetimedb.codegen.schema.AlgebraicType
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.ProductType
import dev.sanson.spacetimedb.codegen.schema.RawTypeDef
import dev.sanson.spacetimedb.codegen.schema.SumType
import dev.sanson.spacetimedb.codegen.schema.asOption
import dev.sanson.spacetimedb.codegen.schema.isConnectionId
import dev.sanson.spacetimedb.codegen.schema.isIdentity
import dev.sanson.spacetimedb.codegen.schema.isScheduleAt
import dev.sanson.spacetimedb.codegen.schema.isTimeDuration
import dev.sanson.spacetimedb.codegen.schema.isTimestamp

/**
 * Generates Kotlin source files for SpacetimeDB table row types and custom types.
 */
public class TypeGenerator(
    private val schema: ModuleSchema,
    private val targetPackage: String,
) {
    private val typeMapper = TypeMapper(schema, targetPackage)

    /**
     * Generate files for all named custom types in the schema.
     */
    public fun generateTypeFiles(): List<FileSpec> =
        schema.sortedTypes
            .mapNotNull { typeDef -> generateTypeFile(typeDef) }

    /**
     * Generate a file for a single named type.
     * Returns null if the type resolves to a special SDK type (Identity, Timestamp, etc.).
     */
    public fun generateTypeFile(typeDef: RawTypeDef): FileSpec? {
        val resolved = schema.resolveType(AlgebraicType.Ref(typeDef.ty))
        val typeName = typeDef.sourceName.sourceName.toPascalCase()

        // Skip types that map to SDK special types
        if (resolved.isIdentity || resolved.isConnectionId ||
            resolved.isTimestamp || resolved.isTimeDuration ||
            resolved.isScheduleAt
        ) {
            return null
        }

        // Skip Option types — they are expressed as nullable
        if (resolved.asOption() != null) return null

        val typeSpec = when (resolved) {
            is AlgebraicType.Product -> generateProductType(typeName, resolved.type)
            is AlgebraicType.Sum -> generateSumType(typeName, resolved.type)
            else -> return null
        }

        return FileSpec.builder(targetPackage, typeName)
            .addType(typeSpec)
            .build()
    }

    /**
     * Generate a file for a table's row type.
     */
    public fun generateTableRowFile(tableName: String, productType: ProductType): FileSpec {
        val typeName = tableName.toPascalCase()
        val typeSpec = generateProductType(typeName, productType)

        return FileSpec.builder(targetPackage, typeName)
            .addType(typeSpec)
            .build()
    }

    /**
     * Generate a @Serializable class for a product type (struct).
     */
    internal fun generateProductType(name: String, productType: ProductType): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(name)
            .addAnnotation(SERIALIZABLE)
            .addModifiers(KModifier.PUBLIC)

        val constructorBuilder = FunSpec.constructorBuilder()

        for (element in productType.elements) {
            val originalName = element.name
                ?: throw IllegalArgumentException("Product type '$name' has unnamed field")

            val fieldName = originalName.toCamelCase()
            val fieldType = typeMapper.typeName(element.algebraicType)
            val needsSerialName = originalName != fieldName

            constructorBuilder.addParameter(fieldName, fieldType)

            classBuilder.addProperty(
                PropertySpec.builder(fieldName, fieldType)
                    .initializer(fieldName)
                    .addModifiers(KModifier.PUBLIC)
                    .apply {
                        if (needsSerialName) {
                            addAnnotation(
                                AnnotationSpec.builder(SERIAL_NAME)
                                    .addMember("%S", originalName)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
        }

        classBuilder.primaryConstructor(constructorBuilder.build())

        return classBuilder.build()
    }

    /**
     * Generate a type for a sum type.
     *
     * - Simple enums (all unit variants) → Kotlin enum class
     * - Data sum types → abstract class with subtype per variant
     */
    internal fun generateSumType(name: String, sumType: SumType): TypeSpec {
        if (sumType.isSimpleEnum) {
            return generateSimpleEnum(name, sumType)
        }
        return generateDataSum(name, sumType)
    }

    private fun generateSimpleEnum(name: String, sumType: SumType): TypeSpec {
        val enumBuilder = TypeSpec.enumBuilder(name)
            .addAnnotation(SERIALIZABLE)
            .addModifiers(KModifier.PUBLIC)

        for (variant in sumType.variants) {
            val originalName = variant.name
                ?: throw IllegalArgumentException("Enum '$name' has unnamed variant")

            val kotlinName = originalName.replaceFirstChar { it.uppercase() }

            if (kotlinName != originalName) {
                enumBuilder.addEnumConstant(
                    kotlinName,
                    TypeSpec.anonymousClassBuilder()
                        .addAnnotation(
                            AnnotationSpec.builder(SERIAL_NAME)
                                .addMember("%S", originalName)
                                .build()
                        )
                        .build()
                )
            } else {
                enumBuilder.addEnumConstant(kotlinName)
            }
        }

        return enumBuilder.build()
    }

    private fun generateDataSum(name: String, sumType: SumType): TypeSpec {
        val abstractClass = TypeSpec.classBuilder(name)
            .addAnnotation(SERIALIZABLE)
            .addModifiers(KModifier.PUBLIC, KModifier.SEALED)

        for (variant in sumType.variants) {
            val variantName = variant.name?.toPascalCase()
                ?: throw IllegalArgumentException("Sum type '$name' has unnamed variant")

            val variantType = variant.algebraicType

            val variantSpec = if (variantType is AlgebraicType.Product && variantType.type.elements.isEmpty()) {
                // Unit variant → data object
                TypeSpec.objectBuilder(variantName)
                    .addModifiers(KModifier.DATA)
                    .superclass(ClassName(targetPackage, name))
                    .build()
            } else if (variantType is AlgebraicType.Product && variantType.type.elements.size == 1) {
                // Single-field variant → class with one property
                val element = variantType.type.elements.single()
                val fieldName = element.name?.toCamelCase() ?: "value"
                val fieldType = typeMapper.typeName(element.algebraicType)

                TypeSpec.classBuilder(variantName)
                    .addModifiers(KModifier.PUBLIC, KModifier.DATA)
                    .superclass(ClassName(targetPackage, name))
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter(fieldName, fieldType)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(fieldName, fieldType)
                            .initializer(fieldName)
                            .addModifiers(KModifier.PUBLIC)
                            .build()
                    )
                    .build()
            } else {
                // Multi-field variant → class with multiple properties
                val variantBuilder = TypeSpec.classBuilder(variantName)
                    .addModifiers(KModifier.PUBLIC, KModifier.DATA)
                    .superclass(ClassName(targetPackage, name))

                val ctorBuilder = FunSpec.constructorBuilder()

                if (variantType is AlgebraicType.Product) {
                    for (element in variantType.type.elements) {
                        val fieldName = element.name?.toCamelCase() ?: "value"
                        val fieldType = typeMapper.typeName(element.algebraicType)
                        ctorBuilder.addParameter(fieldName, fieldType)
                        variantBuilder.addProperty(
                            PropertySpec.builder(fieldName, fieldType)
                                .initializer(fieldName)
                                .addModifiers(KModifier.PUBLIC)
                                .build()
                        )
                    }
                } else {
                    // Non-product payload (e.g., a single primitive)
                    val fieldType = typeMapper.typeName(variantType)
                    ctorBuilder.addParameter("value", fieldType)
                    variantBuilder.addProperty(
                        PropertySpec.builder("value", fieldType)
                            .initializer("value")
                            .addModifiers(KModifier.PUBLIC)
                            .build()
                    )
                }

                variantBuilder.primaryConstructor(ctorBuilder.build())
                variantBuilder.build()
            }

            abstractClass.addType(variantSpec)
        }

        return abstractClass.build()
    }
}
