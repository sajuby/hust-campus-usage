plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val bundleDesign by tasks.registering(Sync::class) {
    from("../../design") { include("project.json", "defaults.json") }
    into(layout.buildDirectory.dir("generated/campus-design"))
}
val bundleMetro by tasks.registering(Sync::class) {
    from("../../shared/metro") { include("wuhan-reference.json", "wuhan-map.json", "map.html", "source.json") }
    into(layout.buildDirectory.dir("generated/campus-metro/metro"))
}
android {
    namespace = "dev.campususage.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.campususage.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 7
        versionName = "0.5.0"
        resValue("string", "app_name", "校园用量助手")
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev"; resValue("string", "app_name", "校园用量助手·开发版") }
        release { isMinifyEnabled = false }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // A single canonical design file is bundled for both renderers.
    // Bundle the shared JSON, while keeping source art and generation records out of the APK.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/campus-design"))
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/campus-metro"))
    sourceSets["test"].resources.srcDir("../../shared/metro")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
tasks.named("preBuild").configure { dependsOn(bundleDesign, bundleMetro) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { throw GradleException("当前仅开放 Debug 开发构建。完成真机兼容、长期会话与正式签名验证后再启用 Release。") }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
