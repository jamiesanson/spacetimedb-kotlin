plugins {
    id("dev.sanson.module")
    `java-gradle-plugin`
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":spacetimedb-codegen"))
    implementation(libs.kotlinpoet)

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("spacetimedb") {
            id = "dev.sanson.spacetimedb"
            implementationClass = "dev.sanson.spacetimedb.gradle.SpacetimeDbPlugin"
        }
    }
}
