plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String