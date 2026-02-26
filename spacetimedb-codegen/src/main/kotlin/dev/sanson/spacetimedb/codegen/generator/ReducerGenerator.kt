package dev.sanson.spacetimedb.codegen.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema

private val SPACETIMEDB_CONNECTION = ClassName("dev.sanson.spacetimedb", "SpacetimeDbConnection")
private val DB_CALLBACKS = ClassName("dev.sanson.spacetimedb", "DbCallbacks")
private val CALLBACK_ID = ClassName("dev.sanson.spacetimedb", "CallbackId")
private val REDUCER_EVENT = ClassName("dev.sanson.spacetimedb", "ReducerEvent")
private val BSATN = ClassName("dev.sanson.spacetimedb.bsatn", "Bsatn")

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
     * Also generates `on<Reducer>` / `removeOn<Reducer>` callback methods.
     */
    public fun generateRemoteReducersFile(): FileSpec {
        val reducerClassName = ClassName(targetPackage, "Reducer")
        val callbackType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("event", REDUCER_EVENT.parameterizedBy(reducerClassName)).build(),
            ),
            returnType = com.squareup.kotlinpoet.UNIT,
        )

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

            // on<Reducer>
            val onName = "on${reducer.sourceName.toPascalCase()}"
            builder.addFunction(
                FunSpec.builder(onName)
                    .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
                    .addParameter("callback", callbackType)
                    .returns(CALLBACK_ID)
                    .build()
            )

            // removeOn<Reducer>
            val removeName = "removeOn${reducer.sourceName.toPascalCase()}"
            builder.addFunction(
                FunSpec.builder(removeName)
                    .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
                    .addParameter("id", CALLBACK_ID)
                    .build()
            )
        }

        return FileSpec.builder(targetPackage, "RemoteReducers")
            .addType(builder.build())
            .build()
    }

    /**
     * Generate a `RemoteReducersImpl` class implementing `RemoteReducers`.
     *
     * For reducers with arguments, generates a private `@Serializable` args class
     * and BSATN-encodes the args before calling `connection.callReducer()`.
     * No-arg reducers pass an empty byte array.
     */
    public fun generateRemoteReducersImplFile(): FileSpec {
        val remoteReducersInterface = ClassName(targetPackage, "RemoteReducers")

        val fileBuilder = FileSpec.builder(targetPackage, "RemoteReducersImpl")

        // Generate private @Serializable args classes for reducers with parameters
        for (reducer in schema.clientCallableReducers) {
            val params = reducer.params.elements
            if (params.isEmpty()) continue

            val argsClassName = "${reducer.sourceName.toPascalCase()}Args"
            val argsClassBuilder = TypeSpec.classBuilder(argsClassName)
                .addModifiers(KModifier.PRIVATE)
                .addAnnotation(SERIALIZABLE)

            val ctorBuilder = FunSpec.constructorBuilder()
            for (element in params) {
                val originalName = element.name
                    ?: throw IllegalArgumentException("Reducer '${reducer.sourceName}' has unnamed parameter")
                val fieldName = originalName.toCamelCase()
                val fieldType = typeMapper.typeName(element.algebraicType)

                ctorBuilder.addParameter(fieldName, fieldType)

                val propBuilder = PropertySpec.builder(fieldName, fieldType)
                    .initializer(fieldName)

                // Add @SerialName if the Kotlin name differs from the original
                if (originalName != fieldName) {
                    propBuilder.addAnnotation(
                        AnnotationSpec.builder(SERIAL_NAME)
                            .addMember("%S", originalName)
                            .build()
                    )
                }

                argsClassBuilder.addProperty(propBuilder.build())
            }

            argsClassBuilder.primaryConstructor(ctorBuilder.build())
            fileBuilder.addType(argsClassBuilder.build())
        }

        // Generate the impl class
        val reducerClassName = ClassName(targetPackage, "Reducer")
        val callbackType = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("event", REDUCER_EVENT.parameterizedBy(reducerClassName)).build(),
            ),
            returnType = com.squareup.kotlinpoet.UNIT,
        )

        val classBuilder = TypeSpec.classBuilder("RemoteReducersImpl")
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(remoteReducersInterface)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("connection", SPACETIMEDB_CONNECTION)
                    .addParameter("callbacks", DB_CALLBACKS)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("connection", SPACETIMEDB_CONNECTION)
                    .initializer("connection")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("callbacks", DB_CALLBACKS)
                    .initializer("callbacks")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        for (reducer in schema.clientCallableReducers) {
            val methodName = reducer.sourceName.toCamelCase()
            val params = reducer.params.elements

            val funBuilder = FunSpec.builder(methodName)
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)

            for (element in params) {
                val originalName = element.name
                    ?: throw IllegalArgumentException("Reducer '${reducer.sourceName}' has unnamed parameter")
                val paramName = originalName.toCamelCase()
                val paramType = typeMapper.typeName(element.algebraicType)
                funBuilder.addParameter(paramName, paramType)
            }

            if (params.isEmpty()) {
                funBuilder.addCode(
                    "connection.callReducer(%S, byteArrayOf())",
                    reducer.sourceName,
                )
            } else {
                val argsClassName = ClassName(targetPackage, "${reducer.sourceName.toPascalCase()}Args")
                val argsCtorArgs = params.mapNotNull { it.name?.toCamelCase() }.joinToString(", ")
                val serializerMember = MemberName("kotlinx.serialization", "serializer")

                funBuilder.addCode(
                    CodeBlock.builder()
                        .addStatement("val args = %T(%L)", argsClassName, argsCtorArgs)
                        .addStatement(
                            "connection.callReducer(%S, %T.encodeToByteArray(%M<%T>(), args))",
                            reducer.sourceName,
                            BSATN,
                            serializerMember,
                            argsClassName,
                        )
                        .build()
                )
            }

            classBuilder.addFunction(funBuilder.build())

            // on<Reducer> — delegates to callbacks.registerOnReducer
            val onName = "on${reducer.sourceName.toPascalCase()}"
            classBuilder.addFunction(
                FunSpec.builder(onName)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .addParameter("callback", callbackType)
                    .returns(CALLBACK_ID)
                    .addCode(
                        "return callbacks.registerOnReducer(%S) { callback(it as %T) }",
                        reducer.sourceName,
                        REDUCER_EVENT.parameterizedBy(reducerClassName),
                    )
                    .build()
            )

            // removeOn<Reducer>
            val removeName = "removeOn${reducer.sourceName.toPascalCase()}"
            classBuilder.addFunction(
                FunSpec.builder(removeName)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .addParameter("id", CALLBACK_ID)
                    .addCode("callbacks.removeOnReducer(%S, id)", reducer.sourceName)
                    .build()
            )
        }

        fileBuilder.addType(classBuilder.build())
        return fileBuilder.build()
    }
}
