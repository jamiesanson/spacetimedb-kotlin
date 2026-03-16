package dev.sanson.spacetimedb.codegen.generator

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.sanson.spacetimedb.codegen.schema.AlgebraicType
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import dev.sanson.spacetimedb.codegen.schema.ProductType
import dev.sanson.spacetimedb.codegen.schema.ProductTypeElement
import dev.sanson.spacetimedb.codegen.schema.SumType
import dev.sanson.spacetimedb.codegen.schema.SumTypeVariant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

class TypeGeneratorTest {

    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `generates product type as class with constructor`() {
        val schema = schemaWith()
        val gen = TypeGenerator(schema, "com.example")

        val productType =
            ProductType(
                listOf(
                    ProductTypeElement("player_id", AlgebraicType.U64),
                    ProductTypeElement("name", AlgebraicType.StringType),
                )
            )

        val file = gen.generateTableRowFile("logged_out_player", productType)
        val output = file.toString()

        assertContains(output, "class LoggedOutPlayer")
        assertContains(output, "Serializable")
        assertContains(output, "playerId")
        assertContains(output, "ULong")
        assertContains(output, "player_id")
        assertContains(output, "val name")
    }

    @Test
    fun `generates simple enum for unit-only sum type`() {
        val schema =
            schemaWith(
                types =
                    listOf(
                        AlgebraicType.Sum(
                            SumType(
                                listOf(
                                    SumTypeVariant(
                                        "Active",
                                        AlgebraicType.Product(ProductType(emptyList())),
                                    ),
                                    SumTypeVariant(
                                        "Inactive",
                                        AlgebraicType.Product(ProductType(emptyList())),
                                    ),
                                    SumTypeVariant(
                                        "Banned",
                                        AlgebraicType.Product(ProductType(emptyList())),
                                    ),
                                )
                            )
                        )
                    ),
                namedTypes = listOf(TypeNameEntry("PlayerStatus", 0)),
            )
        val gen = TypeGenerator(schema, "com.example")
        val typeDef = schema.types.first()
        val file = gen.generateTypeFile(typeDef)

        assertNotNull(file)
        val output = file.toString()
        assertContains(output, "enum class PlayerStatus")
        assertContains(output, "Active")
        assertContains(output, "Inactive")
        assertContains(output, "Banned")
    }

    @Test
    fun `generates PascalCase enum entries with SerialName for lowercase variants`() {
        val schema =
            schemaWith(
                types =
                    listOf(
                        AlgebraicType.Sum(
                            SumType(
                                listOf(
                                    SumTypeVariant(
                                        "text",
                                        AlgebraicType.Product(ProductType(emptyList())),
                                    ),
                                    SumTypeVariant(
                                        "voice",
                                        AlgebraicType.Product(ProductType(emptyList())),
                                    ),
                                )
                            )
                        )
                    ),
                namedTypes = listOf(TypeNameEntry("ChannelKind", 0)),
            )
        val gen = TypeGenerator(schema, "com.example")
        val typeDef = schema.types.first()
        val file = gen.generateTypeFile(typeDef)

        assertNotNull(file)
        val output = file.toString()
        assertContains(output, "enum class ChannelKind")
        assertContains(output, "Text")
        assertContains(output, "Voice")
        assertContains(output, "@SerialName(\"text\")")
        assertContains(output, "@SerialName(\"voice\")")
    }

    @Test
    fun `generates sealed class for data sum type`() {
        val schema =
            schemaWith(
                types =
                    listOf(
                        AlgebraicType.Sum(
                            SumType(
                                listOf(
                                    SumTypeVariant(
                                        "Circle",
                                        AlgebraicType.Product(
                                            ProductType(
                                                listOf(
                                                    ProductTypeElement("radius", AlgebraicType.F64)
                                                )
                                            )
                                        ),
                                    ),
                                    SumTypeVariant(
                                        "Rectangle",
                                        AlgebraicType.Product(
                                            ProductType(
                                                listOf(
                                                    ProductTypeElement("width", AlgebraicType.F64),
                                                    ProductTypeElement("height", AlgebraicType.F64),
                                                )
                                            )
                                        ),
                                    ),
                                    SumTypeVariant(
                                        "None",
                                        AlgebraicType.Product(ProductType(emptyList())),
                                    ),
                                )
                            )
                        )
                    ),
                namedTypes = listOf(TypeNameEntry("Shape", 0)),
            )
        val gen = TypeGenerator(schema, "com.example")
        val typeDef = schema.types.first()
        val file = gen.generateTypeFile(typeDef)

        assertNotNull(file)
        val output = file.toString()
        assertContains(output, "sealed class Shape")
        assertContains(output, "data class Circle")
        assertContains(output, "radius")
        assertContains(output, "Double")
        assertContains(output, "data class Rectangle")
        assertContains(output, "data object None")
    }

    @Test
    fun `skips Identity type`() {
        val schema =
            schemaWith(
                types =
                    listOf(
                        AlgebraicType.Product(
                            ProductType(
                                listOf(ProductTypeElement("__identity__", AlgebraicType.U256))
                            )
                        )
                    ),
                namedTypes = listOf(TypeNameEntry("Identity", 0)),
            )
        val gen = TypeGenerator(schema, "com.example")
        val typeDef = schema.types.first()

        assertNull(gen.generateTypeFile(typeDef))
    }

    @Test
    fun `generates SerialName for snake_case fields`() {
        val schema = schemaWith()
        val gen = TypeGenerator(schema, "com.example")

        val productType =
            ProductType(
                listOf(
                    ProductTypeElement("first_name", AlgebraicType.StringType),
                    ProductTypeElement("age", AlgebraicType.U8),
                )
            )

        val file = gen.generateTableRowFile("person", productType)
        val output = file.toString()

        // first_name → firstName with @SerialName
        assertContains(output, "first_name")
        assertContains(output, "firstName")
        // age stays as age — no @SerialName needed
        assertContains(output, "val age")
    }

    @Test
    fun `generates types from real fixture`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TypeGenerator(schema, "com.example.module")
        val files = gen.generateTypeFiles()

        assert(files.isNotEmpty()) { "Expected at least one generated type file" }

        for (file in files) {
            val output = file.toString()
            assert(output.contains("@Serializable")) { "File ${file.name} missing @Serializable" }
        }
    }

    @Test
    fun `generates table row types from fixture`() {
        val schema = ModuleSchema.fromJson(fixture)
        val gen = TypeGenerator(schema, "com.example.module")

        for (table in schema.publicTables) {
            val productType = schema.tableProductType(table)
            val file = gen.generateTableRowFile(table.sourceName, productType)
            val output = file.toString()

            assertContains(output, "@Serializable")
            assertContains(output, "class ${table.sourceName.toPascalCase()}")
        }
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generated code compiles successfully`() {
        val schema = ModuleSchema.fromJson(fixture)
        val pkg = "com.example.module"
        val typeGen = TypeGenerator(schema, pkg)
        val tableHandleGen = TableHandleGenerator(schema, pkg)
        val reducerGen = ReducerGenerator(schema, pkg)

        // Collect all generated sources
        val sources = mutableListOf<SourceFile>()

        for (file in typeGen.generateTypeFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        for (table in schema.publicTables) {
            val productType = schema.tableProductType(table)
            val file = typeGen.generateTableRowFile(table.sourceName, productType)
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }

        for (file in tableHandleGen.generateTableHandleFiles()) {
            sources.add(SourceFile.kotlin("${file.name}.kt", file.toString()))
        }
        sources.add(
            SourceFile.kotlin(
                "RemoteTables.kt",
                tableHandleGen.generateRemoteTablesFile().toString(),
            )
        )
        sources.add(SourceFile.kotlin("Reducer.kt", reducerGen.generateReducerFile().toString()))
        sources.add(
            SourceFile.kotlin(
                "RemoteReducers.kt",
                reducerGen.generateRemoteReducersFile().toString(),
            )
        )

        val compilation =
            KotlinCompilation().apply {
                this.sources = sources
                inheritClassPath = true
            }

        val result = compilation.compile()
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Generated code failed to compile:\n${result.messages}",
        )
    }
}
