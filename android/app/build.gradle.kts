plugins {
    id("com.android.application")
}

// URL du tableau de bord Vercel : la WebView l'affiche et le widget appelle son /api/proxy
val baseUrl = (findProperty("transportBaseUrl") as String?) ?: "https://transport-screen.vercel.app"
// En CI, le numéro de build fait monter la version pour pouvoir installer par-dessus
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1

android {
    namespace = "io.github.cyba26.transport"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.cyba26.transport"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    signingConfigs {
        // Clé commitée exprès : chaque APK (local ou CI) a la même signature
        // et s'installe par-dessus le précédent. Appli perso, hors Play Store.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
