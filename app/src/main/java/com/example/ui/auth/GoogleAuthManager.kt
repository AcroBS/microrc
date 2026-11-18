package com.example.ui.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

data class FirebaseDiagnosticInfo(
    val isFirebaseConfigured: Boolean,
    val hasWebClientId: Boolean,
    val hasRuntimeOverride: Boolean,
    val apkSha1: String,
    val apkSha256: String
)

private data class RuntimeFirebaseConfig(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val storageBucket: String,
    val gcmSenderId: String,
    val webClientId: String
)

object GoogleAuthManager {
    private const val RUNTIME_CONFIG_FILE = "firebase_runtime_config.json"

    private val _firebaseInitialized = MutableStateFlow(false)
    val firebaseInitialized: StateFlow<Boolean> = _firebaseInitialized.asStateFlow()

    private val _configRevision = MutableStateFlow(0)
    val configRevision: StateFlow<Int> = _configRevision.asStateFlow()

    @Synchronized
    fun ensureFirebaseInitialized(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            _firebaseInitialized.value = true
            return true
        }

        // 1. Standard google-services.json resource initialization
        val defaultInit = runCatching {
            FirebaseApp.initializeApp(appCtx) != null || FirebaseApp.getApps(appCtx).isNotEmpty()
        }.getOrDefault(false)
        if (defaultInit) {
            _firebaseInitialized.value = true
            return true
        }

        // 2. User-supplied runtime google-services.json override (for GitHub / Obtainium APKs)
        val runtimeCfg = readRuntimeConfig(appCtx)
        if (runtimeCfg != null &&
            runtimeCfg.projectId.isNotBlank() &&
            runtimeCfg.applicationId.isNotBlank() &&
            runtimeCfg.apiKey.isNotBlank()
        ) {
            val ok = runCatching {
                val builder = FirebaseOptions.Builder()
                    .setProjectId(runtimeCfg.projectId)
                    .setApplicationId(runtimeCfg.applicationId)
                    .setApiKey(runtimeCfg.apiKey)
                if (runtimeCfg.storageBucket.isNotBlank()) {
                    builder.setStorageBucket(runtimeCfg.storageBucket)
                }
                if (runtimeCfg.gcmSenderId.isNotBlank()) {
                    builder.setGcmSenderId(runtimeCfg.gcmSenderId)
                }
                FirebaseApp.initializeApp(appCtx, builder.build())
                FirebaseApp.getApps(appCtx).isNotEmpty()
            }.getOrElse { err ->
                Log.w("Auth", "Runtime FirebaseOptions init failed: ${err.localizedMessage}")
                false
            }
            if (ok) {
                _firebaseInitialized.value = true
                return true
            }
        }

        // 3. BuildConfig CI environment fallback keys (injected via CI secrets if configured)
        val envProjectId = BuildConfig.CI_FIREBASE_PROJECT_ID.trim()
        val envAppId = BuildConfig.CI_FIREBASE_APP_ID.trim()
        val envApiKey = BuildConfig.CI_FIREBASE_API_KEY.trim()
        if (envProjectId.isNotBlank() && envAppId.isNotBlank() && envApiKey.isNotBlank()) {
            val ok = runCatching {
                val builder = FirebaseOptions.Builder()
                    .setProjectId(envProjectId)
                    .setApplicationId(envAppId)
                    .setApiKey(envApiKey)
                val bucket = BuildConfig.CI_FIREBASE_STORAGE_BUCKET.trim()
                if (bucket.isNotBlank()) builder.setStorageBucket(bucket)
                val sender = BuildConfig.CI_FIREBASE_GCM_SENDER_ID.trim()
                if (sender.isNotBlank()) builder.setGcmSenderId(sender)
                FirebaseApp.initializeApp(appCtx, builder.build())
                FirebaseApp.getApps(appCtx).isNotEmpty()
            }.getOrElse { err ->
                Log.w("Auth", "BuildConfig FirebaseOptions init failed: ${err.localizedMessage}")
                false
            }
            if (ok) {
                _firebaseInitialized.value = true
                return true
            }
        }

        _firebaseInitialized.value = false
        return false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAuthState(context: Context): Flow<FirebaseUser?> {
        val appCtx = context.applicationContext ?: context
        ensureFirebaseInitialized(appCtx)
        return _firebaseInitialized.flatMapLatest { ready ->
            if (ready && FirebaseApp.getApps(appCtx).isNotEmpty()) {
                runCatching { Firebase.auth.authStateFlow() }
                    .getOrElse { flowOf(null) }
            } else {
                flowOf(null)
            }
        }
    }

    fun resolveWebClientId(context: Context): String {
        val appCtx = context.applicationContext ?: context
        val fromResources = runCatching {
            appCtx.getString(R.string.default_web_client_id).trim()
        }.getOrDefault("")
        if (fromResources.isNotBlank()) return fromResources

        val fromRuntime = readRuntimeConfig(appCtx)?.webClientId?.trim().orEmpty()
        if (fromRuntime.isNotBlank()) return fromRuntime

        return BuildConfig.CI_FIREBASE_WEB_CLIENT_ID.trim()
    }

    fun hasRuntimeOverride(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        return File(appCtx.filesDir, RUNTIME_CONFIG_FILE).exists()
    }

    fun saveRuntimeGoogleServicesJson(context: Context, rawInput: String): Result<Unit> {
        val appCtx = context.applicationContext ?: context
        return runCatching {
            val cleaned = cleanPastedGoogleServicesText(rawInput)
            if (cleaned.isBlank()) {
                throw IllegalArgumentException("Paste the contents of your app/google-services.json file first.")
            }
            val root = JSONObject(cleaned)
            val parsed = extractFirebaseConfigFromGoogleServices(root, appCtx.packageName)
            if (parsed.projectId.isBlank() || parsed.applicationId.isBlank() || parsed.apiKey.isBlank()) {
                throw IllegalArgumentException(
                    "Invalid google-services.json: missing project_id, mobilesdk_app_id, or current_key."
                )
            }
            if (parsed.webClientId.isBlank()) {
                throw IllegalArgumentException(
                    "This google-services.json is missing a Web OAuth client (client_type 3) needed for Google Sign-In."
                )
            }

            val storedJson = JSONObject().apply {
                put("projectId", parsed.projectId)
                put("applicationId", parsed.applicationId)
                put("apiKey", parsed.apiKey)
                put("storageBucket", parsed.storageBucket)
                put("gcmSenderId", parsed.gcmSenderId)
                put("webClientId", parsed.webClientId)
            }
            File(appCtx.filesDir, RUNTIME_CONFIG_FILE).writeText(storedJson.toString(), Charsets.UTF_8)

            val initialized = ensureFirebaseInitialized(appCtx)
            _configRevision.value = _configRevision.value + 1
            if (!initialized) {
                throw IllegalStateException("Saved config, but FirebaseApp could not initialize with those values.")
            }
        }
    }

    fun clearRuntimeOverride(context: Context) {
        val appCtx = context.applicationContext ?: context
        runCatching { File(appCtx.filesDir, RUNTIME_CONFIG_FILE).delete() }
        _configRevision.value = _configRevision.value + 1
    }

    private fun cleanPastedGoogleServicesText(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val lines = text.lines().toMutableList()
            if (lines.isNotEmpty() && lines.first().trim().startsWith("```")) {
                lines.removeAt(0)
            }
            if (lines.isNotEmpty() && lines.last().trim().startsWith("```")) {
                lines.removeAt(lines.lastIndex)
            }
            text = lines.joinToString("\n").trim()
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) ||
            (text.startsWith("'") && text.endsWith("'"))
        ) {
            text = text.substring(1, text.length - 1).trim()
        }
        if (!text.startsWith("{")) {
            val decoded = runCatching {
                val compact = text.replace("\\s".toRegex(), "")
                String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8).trim()
            }.getOrNull()
            if (decoded != null && decoded.startsWith("{")) {
                return decoded
            }
        }
        return text
    }

    private fun extractFirebaseConfigFromGoogleServices(
        root: JSONObject,
        expectedPackage: String
    ): RuntimeFirebaseConfig {
        // Also accept already-normalized runtime JSON
        if (root.has("projectId") && root.has("applicationId") && root.has("apiKey")) {
            return RuntimeFirebaseConfig(
                projectId = root.optString("projectId").trim(),
                applicationId = root.optString("applicationId").trim(),
                apiKey = root.optString("apiKey").trim(),
                storageBucket = root.optString("storageBucket").trim(),
                gcmSenderId = root.optString("gcmSenderId").trim(),
                webClientId = root.optString("webClientId").trim()
            )
        }

        val projectInfo = root.optJSONObject("project_info")
            ?: throw IllegalArgumentException("Missing 'project_info' object in google-services.json.")
        val projectId = projectInfo.optString("project_id").trim()
        val storageBucket = projectInfo.optString("storage_bucket").trim()
        val projectNumber = projectInfo.optString("project_number").trim()

        val clients = root.optJSONArray("client")
            ?: throw IllegalArgumentException("Missing 'client' array in google-services.json.")
        if (clients.length() == 0) {
            throw IllegalArgumentException("No Android client entries found in google-services.json.")
        }

        var selectedClient: JSONObject? = null
        for (i in 0 until clients.length()) {
            val c = clients.optJSONObject(i) ?: continue
            val pkg = c.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name")
                ?.trim()
            if (pkg == expectedPackage) {
                selectedClient = c
                break
            }
        }
        val clientObj = selectedClient ?: clients.optJSONObject(0)
            ?: throw IllegalArgumentException("Invalid client entry in google-services.json.")

        val appId = clientObj.optJSONObject("client_info")
            ?.optString("mobilesdk_app_id")
            ?.trim()
            .orEmpty()

        val apiKeys = clientObj.optJSONArray("api_key")
        var apiKey = ""
        if (apiKeys != null) {
            for (i in 0 until apiKeys.length()) {
                val candidate = apiKeys.optJSONObject(i)?.optString("current_key")?.trim().orEmpty()
                if (candidate.isNotBlank()) {
                    apiKey = candidate
                    break
                }
            }
        }

        var webClientId = findType3ClientId(clientObj.optJSONArray("oauth_client"))
        if (webClientId.isBlank()) {
            val otherClients = clientObj.optJSONObject("services")
                ?.optJSONObject("appinvite_service")
                ?.optJSONArray("other_platform_oauth_client")
            webClientId = findType3ClientId(otherClients)
        }
        if (webClientId.isBlank()) {
            for (i in 0 until clients.length()) {
                val c = clients.optJSONObject(i) ?: continue
                webClientId = findType3ClientId(c.optJSONArray("oauth_client"))
                if (webClientId.isNotBlank()) break
            }
        }

        return RuntimeFirebaseConfig(
            projectId = projectId,
            applicationId = appId,
            apiKey = apiKey,
            storageBucket = storageBucket,
            gcmSenderId = projectNumber,
            webClientId = webClientId
        )
    }

    private fun findType3ClientId(oauthArray: org.json.JSONArray?): String {
        if (oauthArray == null) return ""
        for (i in 0 until oauthArray.length()) {
            val item = oauthArray.optJSONObject(i) ?: continue
            if (item.optInt("client_type", -1) == 3) {
                val cid = item.optString("client_id").trim()
                if (cid.isNotBlank()) return cid
            }
        }
        return ""
    }

    private fun readRuntimeConfig(context: Context): RuntimeFirebaseConfig? {
        return runCatching {
            val file = File(context.filesDir, RUNTIME_CONFIG_FILE)
            if (!file.exists()) return null
            val json = JSONObject(file.readText(Charsets.UTF_8))
            RuntimeFirebaseConfig(
                projectId = json.optString("projectId").trim(),
                applicationId = json.optString("applicationId").trim(),
                apiKey = json.optString("apiKey").trim(),
                storageBucket = json.optString("storageBucket").trim(),
                gcmSenderId = json.optString("gcmSenderId").trim(),
                webClientId = json.optString("webClientId").trim()
            )
        }.getOrNull()
    }

    fun inspectDiagnostics(context: Context): FirebaseDiagnosticInfo {
        val configured = ensureFirebaseInitialized(context)
        val hasClient = resolveWebClientId(context).isNotBlank()
        val hasOverride = hasRuntimeOverride(context)
        val (sha1, sha256) = readApkSigningFingerprints(context)
        return FirebaseDiagnosticInfo(
            isFirebaseConfigured = configured,
            hasWebClientId = hasClient,
            hasRuntimeOverride = hasOverride,
            apkSha1 = sha1,
            apkSha256 = sha256
        )
    }

    @Suppress("DEPRECATION")
    private fun readApkSigningFingerprints(context: Context): Pair<String, String> {
        return runCatching {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = pkgInfo.signingInfo
                val signers = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    null
                }
                signers?.firstOrNull()?.toByteArray()
            } else {
                val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes == null || certBytes.isEmpty()) {
                "" to ""
            } else {
                val sha1 = MessageDigest.getInstance("SHA-1")
                    .digest(certBytes)
                    .joinToString(":") { "%02X".format(it) }
                val sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(certBytes)
                    .joinToString(":") { "%02X".format(it) }
                sha1 to sha256
            }
        }.getOrDefault("" to "")
    }

    suspend fun attemptAutoSignIn(context: Context) {
        if (!ensureFirebaseInitialized(context)) return
        if (Firebase.auth.currentUser != null) return
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) return

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(request = request, context = context)
            handleSignInResult(result)
        } catch (e: NoCredentialException) {
            Log.d("Auth", "No authorized Google credentials available for silent sign-in.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Auth", "Silent auto sign-in skipped: ${e.localizedMessage}")
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        if (!ensureFirebaseInitialized(context)) {
            return Result.failure(
                IllegalStateException(
                    "Firebase is not configured in this APK yet. Tap 'Paste google-services.json' below for instant setup, or add secret GOOGLE_SERVICES_JSON in GitHub Actions."
                )
            )
        }
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In Web Client ID is missing. Tap 'Paste google-services.json' below or update your GOOGLE_SERVICES_JSON secret."
                )
            )
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(request = request, context = context)
            val user = handleSignInResult(result)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unsupported credential returned from Google Sign-In."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Sign-in cancelled or SHA-1 mismatch: ${e.message}", e)
            Result.failure(
                IllegalStateException(
                    "Google Sign-In was cancelled or this GitHub APK's signing SHA-1 is not registered in Firebase Console yet. Copy the APK SHA-1 below and add it under Firebase Console → Project Settings → Your Android App.",
                    e
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse): FirebaseUser? {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            return Firebase.auth.signInWithCredential(firebaseCredential).await().user
        }
        return null
    }

    suspend fun signOut(
        context: Context,
        beforeSignOutCleanup: (suspend () -> Unit)? = null
    ) {
        withContext(NonCancellable) {
            try {
                beforeSignOutCleanup?.invoke()
            } catch (e: Exception) {
                Log.w("Auth", "Pre-signout room cleanup warning: ${e.localizedMessage}")
            }
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear CredentialManager state", e)
            }
            if (ensureFirebaseInitialized(context)) {
                Firebase.auth.signOut()
            }
        }
    }
}
