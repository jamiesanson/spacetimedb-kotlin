plugins {
    id("dev.sanson.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":spacetimedb-bsatn"))
            api(libs.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.okio.fakefilesystem)
        }

        jsMain.dependencies {
            // okio-nodefilesystem could be added for Node.js filesystem support,
            // but it breaks browser compilation. Users needing credential persistence
            // on JS/Node should pass their own FileSystem to CredentialFile.
        }
    }
}
