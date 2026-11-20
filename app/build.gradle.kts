import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import groovy.json.JsonSlurper

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.microrc.k7m9xp"
    minSdk = 24
    targetSdk = 36
    versionCode = providers.environmentVariable("CI_VERSION_CODE").orNull?.toIntOrNull() ?: 1
    versionName =
      providers.environmentVariable("CI_VERSION_NAME").orNull?.takeIf { it.isNotBlank() } ?: "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val gsContent =
      providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull
    val gsExists = !gsContent.isNullOrBlank()
    val gsDefinesWebClientId =
      gsExists &&
        runCatching {
            val root = JsonSlurper().parseText(gsContent!!) as? Map<*, *>
            val clients = root?.get("client") as? List<*> ?: emptyList<Any?>()
            val matchingClient =
              clients.filterIsInstance<Map<*, *>>().firstOrNull { client ->
                val clientInfo = client["client_info"] as? Map<*, *>
                val androidInfo = clientInfo?.get("android_client_info") as? Map<*, *>
                androidInfo?.get("package_name") == "com.aistudio.microrc.k7m9xp"
              }
            val oauthClients = matchingClient?.get("oauth_client") as? List<*> ?: emptyList<Any?>()
            oauthClients.filterIsInstance<Map<*, *>>().any { oauth ->
              (oauth["client_type"] as? Number)?.toInt() == 3 &&
                (oauth["client_id"] as? String)?.isNotBlank() == true
            }
          }
          .getOrDefault(false)
    if (!gsDefinesWebClientId) {
      if (!gsExists) {
        file("build/generated/res/processDebugGoogleServices").deleteRecursively()
        file("build/generated/res/processReleaseGoogleServices").deleteRecursively()
      }
      resValue("string", "default_web_client_id", "")
    }

    val appletConfigContent =
      providers
        .fileContents(layout.projectDirectory.file("src/main/res/values/firebase_applet_config.xml"))
        .asText
        .orNull
    val hasFirestoreDbRes = appletConfigContent?.contains("name=\"firestore_database_id\"") == true
    if (!hasFirestoreDbRes) {
      resValue(
        "string",
        "firestore_database_id",
        "ai-studio-android-microrc-315a4780-b35f-4d6b-8583-99fb992be8f7"
      )
    }

    fun ciEnvLiteral(key: String): String {
      val v =
        providers.environmentVariable(key).orNull
          ?.trim()
          .orEmpty()
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
      return "\"$v\""
    }
    buildConfigField("String", "CI_FIREBASE_PROJECT_ID", ciEnvLiteral("FIREBASE_PROJECT_ID"))
    buildConfigField("String", "CI_FIREBASE_APP_ID", ciEnvLiteral("FIREBASE_APP_ID"))
    buildConfigField("String", "CI_FIREBASE_API_KEY", ciEnvLiteral("FIREBASE_API_KEY"))
    buildConfigField("String", "CI_FIREBASE_STORAGE_BUCKET", ciEnvLiteral("FIREBASE_STORAGE_BUCKET"))
    buildConfigField("String", "CI_FIREBASE_GCM_SENDER_ID", ciEnvLiteral("FIREBASE_GCM_SENDER_ID"))
    buildConfigField("String", "CI_FIREBASE_WEB_CLIENT_ID", ciEnvLiteral("FIREBASE_WEB_CLIENT_ID"))
    buildConfigField("String", "CI_FIRESTORE_DATABASE_ID", ciEnvLiteral("FIRESTORE_DATABASE_ID"))

    val disableGithubUpdateCheck =
      providers.environmentVariable("DISABLE_GITHUB_UPDATE_CHECK").orNull
        ?.equals("true", ignoreCase = true) == true
    val githubRepo =
      if (disableGithubUpdateCheck) {
        ""
      } else {
        providers.environmentVariable("GITHUB_UPDATE_REPO").orNull
          ?.trim()
          ?.takeIf { it.isNotBlank() }
          ?: providers.environmentVariable("GITHUB_REPOSITORY").orNull?.trim().orEmpty()
      }
    buildConfigField(
      "String",
      "GITHUB_REPOSITORY",
      "\"${githubRepo.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    )
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    resValues = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

if (providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull.isNullOrBlank()) {
  tasks.matching { it.name.startsWith("process") && it.name.endsWith("GoogleServices") }
    .configureEach { enabled = false }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
