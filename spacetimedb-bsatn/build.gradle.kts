plugins { id("dev.sanson.kmp-library") }

kotlin {
    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.serialization.core) }

        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
