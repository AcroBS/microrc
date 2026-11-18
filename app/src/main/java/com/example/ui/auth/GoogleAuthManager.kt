package com.example.ui.auth

import android.content.Context
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
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
import java.io.File

class SignInCancelledByUserException(cause: Throwable? = null) :
    Exception("Sign-in cancelled", cause)

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleAuthManager {
    private val _firebaseInitialized = MutableStateFlow(false)
    val firebaseInitialized: StateFlow<Boolean> = _firebaseInitialized.asStateFlow()

    @Synchronized
    fun ensureFirebaseInitialized(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        // Clean up any legacy manual config file from older builds
        runCatching { File(appCtx.filesDir, "firebase_runtime_config.json").delete() }

        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            _firebaseInitialized.value = true
            return true
        }

        val defaultInit = runCatching {
            FirebaseApp.initializeApp(appCtx) != null || FirebaseApp.getApps(appCtx).isNotEmpty()
        }.getOrDefault(false)
        if (defaultInit) {
            _firebaseInitialized.value = true
            return true
        }

        // Silent build-time fallback if CI injected BuildConfig fields
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
            }.getOrDefault(false)
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

    private fun resolveWebClientId(context: Context): String {
        val appCtx = context.applicationContext ?: context
        val fromResources = runCatching {
            appCtx.getString(R.string.default_web_client_id).trim()
        }.getOrDefault("")
        if (fromResources.isNotBlank()) return fromResources
        return BuildConfig.CI_FIREBASE_WEB_CLIENT_ID.trim()
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
                    "Online services are temporarily unavailable. You can still play Local Wi-Fi, Hotspot, and Solo modes offline."
                )
            )
        }
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In is temporarily unavailable on this build. Offline Wi-Fi and Solo modes remain ready to play."
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
                Result.failure(
                    IllegalStateException("Couldn't complete Google Sign-In. Please try again.")
                )
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            Result.failure(SignInCancelledByUserException(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google sign-in failed", e)
            Result.failure(
                IllegalStateException(
                    "Couldn't sign in with Google. Please check your internet connection and try again.",
                    e
                )
            )
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

    /**
     * Deletes the user's online data and Google-authenticated account (Google Play User Data Policy).
     */
    suspend fun deleteAccountAndSignOut(
        context: Context,
        beforeDeleteCleanup: (suspend () -> Unit)? = null
    ): Result<Unit> {
        return try {
            withContext(NonCancellable) {
                try {
                    beforeDeleteCleanup?.invoke()
                } catch (e: Exception) {
                    Log.w("Auth", "Cloud data cleanup warning before account deletion: ${e.localizedMessage}")
                }
                val credentialManager = CredentialManager.create(context)
                if (ensureFirebaseInitialized(context)) {
                    val user = Firebase.auth.currentUser
                    if (user != null) {
                        try {
                            user.delete().await()
                        } catch (_: FirebaseAuthRecentLoginRequiredException) {
                            // Re-authenticate once via Google if session is older than 5 minutes
                            val webClientId = resolveWebClientId(context)
                            if (webClientId.isNotBlank()) {
                                runCatching {
                                    val reauthOpt = GetSignInWithGoogleOption.Builder(webClientId).build()
                                    val req = GetCredentialRequest.Builder().addCredentialOption(reauthOpt).build()
                                    val res = credentialManager.getCredential(request = req, context = context)
                                    val cred = res.credential
                                    if (cred is CustomCredential &&
                                        cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    ) {
                                        val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                                        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
                                        user.delete().await()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("Auth", "Auth user deletion warning: ${e.localizedMessage}")
                        }
                    }
                    Firebase.auth.signOut()
                }
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear CredentialManager state after account deletion", e)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
