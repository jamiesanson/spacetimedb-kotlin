plugins {
    id("dev.sanson.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":spacetimedb-bsatn"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
