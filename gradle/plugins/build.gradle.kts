plugins {
    `kotlin-dsl`
}

dependencies {
    api(libs.plugins.foojay.asDependency())
    api(libs.plugins.kotlin.jvm.asDependency())
    api(libs.plugins.kotlin.multiplatform.asDependency())
    api(libs.plugins.kotlin.serialization.asDependency())
}

fun Provider<PluginDependency>.asDependency(): Provider<String> =
    this.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

kotlin {
    jvmToolchain(libs.versions.java.toolchain.get().toInt())
}