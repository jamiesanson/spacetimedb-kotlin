plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    dokka(project(":spacetimedb-bsatn"))
    dokka(project(":spacetimedb-core"))
    dokka(project(":spacetimedb-codegen"))
    dokka(project(":spacetimedb-gradle-plugin"))
}
