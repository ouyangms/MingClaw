plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
}

android {
    namespace = "com.loy.mingclaw.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
