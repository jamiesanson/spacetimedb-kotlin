plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaPublications.html {
        outputDirectory = layout.projectDirectory.dir("docs-site/public/api")
    }
}

dependencies {
    dokka(project(":spacetimedb-bsatn"))
    dokka(project(":spacetimedb-core"))
    dokka(project(":spacetimedb-codegen"))
    dokka(project(":spacetimedb-gradle-plugin"))
}
