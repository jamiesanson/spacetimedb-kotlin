plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

group = property("dev.sanson.spacetimedb.group") as String
version = property("dev.sanson.spacetimedb.version") as String

java {
    withSourcesJar()
}