plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

group = property("dev.sanson.spacetimedb.group") as String
version = buildString {
    append(property("dev.sanson.spacetimedb.version") as String)
    if (!providers.environmentVariable("GITHUB_TOKEN").isPresent) append("-SNAPSHOT")
}

java {
    withSourcesJar()
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

publishing {
    repositories {
        val ghToken = providers.environmentVariable("GITHUB_TOKEN")
        if (ghToken.isPresent) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/jamiesanson/spacetimedb-kotlin")
                credentials {
                    username = "jamiesanson"
                    password = ghToken.get()
                }
            }
        }
    }
}