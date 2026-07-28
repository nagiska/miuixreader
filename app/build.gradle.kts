import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use(::load)
}

fun secret(name: String): String? =
    providers.environmentVariable(name).orNull ?: localProperties.getProperty(name)

val ciStorePath = secret("CI_KEYSTORE_PATH")
val ciStorePassword = secret("CI_KEYSTORE_PASSWORD")
val ciKeyAlias = secret("CI_KEY_ALIAS")
val ciKeyPassword = secret("CI_KEY_PASSWORD")
val ciStoreFile = ciStorePath?.let { file(it) }?.takeIf { it.isFile }
val hasCiSigning = listOf(ciStoreFile, ciStorePassword, ciKeyAlias, ciKeyPassword).all { it != null }

android {
    namespace = "io.github.nagiska.miuixreader"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.nagiska.miuixreader"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    if (hasCiSigning) {
        signingConfigs {
            create("ci") {
                storeFile = requireNotNull(ciStoreFile)
                storePassword = ciStorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (hasCiSigning) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.fragment)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines)

    implementation(libs.bundles.miuix)
    implementation(libs.backdrop)
    implementation(libs.bundles.readium)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
