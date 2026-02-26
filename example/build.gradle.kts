plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
    id("dev.sanson.spacetimedb")
    application
}

repositories {
    mavenCentral()
}

spacetimedb {
    modulePath.set(file("server"))
    packageName.set("com.example.game")
}

application {
    mainClass.set("com.example.game.MainKt")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}
