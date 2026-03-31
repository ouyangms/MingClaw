plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

group = "com.loy.mingclaw.buildlogic"

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "mingclaw.android.library"
            implementationClass = "MingClawAndroidLibraryConventionPlugin"
        }
        register("hilt") {
            id = "mingclaw.hilt"
            implementationClass = "MingClawHiltConventionPlugin"
        }
    }
}
