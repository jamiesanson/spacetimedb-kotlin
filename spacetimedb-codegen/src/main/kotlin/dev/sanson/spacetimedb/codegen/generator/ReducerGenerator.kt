package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.RawReducerDef

private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")

/**
 * Generates typed reducer wrappers.
 *
 * For each module, generates:
 * - A `Reducer` abstract class hierarchy with a subtype per client-callable reducer
 * - A `RemoteReducers` interface with an invoke method per reducer
 */
public class ReducerGenerator(
    private val schema: ModuleSchema,
    private val targetPackage: String,
) {
    private val typeMapper = TypeMapper(schema, targetPackage)

    /**
     * Generate the `Reducer` abstract class hierarchy.
     *
     * Each client-callable reducer becomes a subtype, holding its arguments.
     * Reducers with no arguments become `data object`s.
     */
    public fun generateReducerFile(): FileSpec {
        val reducerClass = TypeSpec.classBuilder("Reducer")
            .addModifiers(KModifier.PUBLIC, KModifier.SEALED)

        for (reducer in schema.clientCallableReducers) {
            val variantName = reducer.sourceName.toPascalCase()
            val params = reducer.params.elements

            val variantSpec = if (params.isEmpty()) {
                TypeSpec.objectBuilder(variantName)
                    .addModifiers(KModifier.DATA)
                    .superclass(ClassName(targetPackage, "Reducer"))
                    .build()
            } else {
                val classBuilder = TypeSpec.classBuilder(variantName)
                    .addModifiers(KModifier.PUBLIC, KModifier.DATA)
                    .superclass(ClassName(targetPackage, "Reducer"))

                val ctorBuilder = FunSpec.constructorBuilder()

                for (element in params) {
                    val originalName = element.name
                        ?: throw IllegalArgumentException("Reducer '${reducer.sourceName}' has unnamed parameter")
                    val fieldName = originalName.toCamelCase()
                    val fieldType = typeMapper.typeName(element.algebraicType)

                    ctorBuilder.addParameter(fieldName, fieldType)

                    val propBuilder = PropertySpec.builder(fieldName, fieldType)
                        .initializer(fieldName)
                        .addModifiers(KModifier.PUBLIC)

                    if (originalName != fieldName) {
                        propBuilder.addAnnotation(
                            AnnotationSpec.builder(SERIAL_NAME)
                                .addMember("%S", originalName)
                                .build()
                        )
                    }

                    classBuilder.addProperty(propBuilder.build())
                }

                classBuilder.primaryConstructor(ctorBuilder.build())
                classBuilder.build()
            }

            reducerClass.addType(variantSpec)
        }

        return FileSpec.builder(targetPackage, "Reducer")
            .addType(reducerClass.build())
            .build()
    }

    /**
     * Generate the `RemoteReducers` interface with a method per reducer.
     *
     * Each method takes the reducer's parameters directly (not a wrapper class).
     */
    public fun generateRemoteReducersFile(): FileSpec {
        val builder = TypeSpec.interfaceBuilder("RemoteReducers")
            .addModifiers(KModifier.PUBLIC)

        for (reducer in schema.clientCallableReducers) {
            val methodName = reducer.sourceName.toCamelCase()
            val funBuilder = FunSpec.builder(methodName)
                .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)

            for (element in reducer.params.elements) {
                val originalName = element.name
                    ?: throw IllegalArgumentException("Reducer '${reducer.sourceName}' has unnamed parameter")
                val paramName = originalName.toCamelCase()
                val paramType = typeMapper.typeName(element.algebraicType)
                funBuilder.addParameter(paramName, paramType)
            }

            builder.addFunction(funBuilder.build())
        }

        return FileSpec.builder(targetPackage, "RemoteReducers")
            .addType(builder.build())
            .build()
    }
}
