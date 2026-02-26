pluginManagement {
    // Resolve the SpacetimeDB plugin from the parent SDK project
    includeBuild("..")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Resolve SDK library dependencies from the parent project
includeBuild("..")

rootProject.name = "spacetimedb-integration-tests"
