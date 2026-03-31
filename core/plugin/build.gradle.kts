plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.plugin"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
