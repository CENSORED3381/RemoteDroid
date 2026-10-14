import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.remotedroid.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.remotedroid.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.4.0"

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// Fase 4 (espelhamento de tela): baixa o scrcpy-server oficial (Genymobile/scrcpy,
// Apache 2.0) e coloca como asset do app, para ser enviado ao aparelho alvo em
// tempo de execucao. A versao abaixo precisa bater com SCRCPY_SERVER_VERSION em
// MirrorStreamer.kt (o servidor recusa rodar se a versao informada na linha de
// comando nao bater com a dele). Precisa de internet durante o build (CI ou local).
val scrcpyServerVersion = "2.4"
val scrcpyServerAsset = file("src/main/assets/scrcpy-server.jar")

tasks.register("fetchScrcpyServer") {
    outputs.file(scrcpyServerAsset)
    doLast {
        if (!scrcpyServerAsset.exists() || scrcpyServerAsset.length() == 0L) {
            scrcpyServerAsset.parentFile.mkdirs()
            val url = "https://github.com/Genymobile/scrcpy/releases/download/" +
                "v$scrcpyServerVersion/scrcpy-server-v$scrcpyServerVersion"
            logger.lifecycle("baixando scrcpy-server v$scrcpyServerVersion de $url")
            URI(url).toURL().openStream().use { input ->
                scrcpyServerAsset.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("fetchScrcpyServer")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Cliente ADB em Kotlin puro (pareamento + shell + sync) - sem precisar do binario adb
    implementation("com.flyfishxu:kadb:2.1.4")

    // Okio: usado para ler os streams brutos do Kadb (fase 4 - espelhamento de tela)
    implementation("com.squareup.okio:okio:3.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
