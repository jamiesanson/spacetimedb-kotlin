plugins {
    id("dev.sanson.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":spacetimedb-bsatn"))
            api(libs.okio)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
            implementation(libs.turbine)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        linuxMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        mingwMain.dependencies {
            implementation(libs.ktor.client.winhttp)
        }
    }
}
