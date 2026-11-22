import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "app.ourmeat"
  compileSdk = 35

  defaultConfig {
    applicationId = "app.ourmeat"
    minSdk = 34
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    debug {
      // The emulator reaches a price service running on the development machine.
      buildConfigField("String", "PRICE_API_URL", "\"${priceApiUrl(default = "http://10.0.2.2:8080/")}\"")
    }
    release {
      // Empty by default: the price comparison stays unavailable until an https service is set.
      buildConfigField("String", "PRICE_API_URL", "\"${priceApiUrl(default = "")}\"")
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    viewBinding = true
    buildConfig = true
  }
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  implementation(libs.gson)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
}

/**
 * Base url of the price service, read from `-PpriceApiUrl=` or `price.api.url` in the
 * unversioned `local.properties`, so no environment address is committed.
 */
fun priceApiUrl(default: String): String {
  val local = Properties()
  rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(local::load)
  val value = (findProperty("priceApiUrl") as String?) ?: local.getProperty("price.api.url") ?: default
  return value.trim().replace("\"", "").let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
}
