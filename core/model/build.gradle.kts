plugins {
    id("mingclaw.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.model"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
}
