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
            implementation(libs.okio.nodefilesystem)
        }
    }
}
