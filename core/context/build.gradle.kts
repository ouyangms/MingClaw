plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.context"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))
    implementation(project(":core:data"))
    implementation(project(":core:memory"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
