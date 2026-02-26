plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
    id("dev.sanson.spacetimedb")
}

repositories {
    mavenCentral()
}

spacetimedb {
    modulePath.set(file("../example/server"))
    packageName.set("dev.sanson.spacetimedb.integration.module")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Support non-standard Docker socket locations (e.g., Rancher Desktop)
    val dockerHost = providers.environmentVariable("DOCKER_HOST")
    if (dockerHost.isPresent) {
        environment("DOCKER_HOST", dockerHost.get())
    }
    // Disable Ryuk (Testcontainers cleanup container) if TESTCONTAINERS_RYUK_DISABLED is set.
    // Needed for some Docker runtimes (e.g., Rancher Desktop) where Ryuk fails to start.
    val ryukDisabled = providers.environmentVariable("TESTCONTAINERS_RYUK_DISABLED")
    if (ryukDisabled.isPresent) {
        environment("TESTCONTAINERS_RYUK_DISABLED", ryukDisabled.get())
    }
}

dependencies {
    implementation("dev.sanson.spacetimedb:spacetimedb-core:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Ktor engine required at runtime for WebSocket transport
    implementation("io.ktor:ktor-client-cio:3.4.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.testcontainers:testcontainers:1.21.1")
    testImplementation("org.testcontainers:junit-jupiter:1.21.1")
}
