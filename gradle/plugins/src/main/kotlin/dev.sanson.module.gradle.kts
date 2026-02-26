plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

group = property("dev.sanson.spacetimedb.group") as String
version = property("dev.sanson.spacetimedb.version") as String

java {
    withSourcesJar()
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