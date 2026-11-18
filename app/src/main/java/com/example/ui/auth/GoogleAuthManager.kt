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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleAuthManager {

    fun ensureFirebaseInitialized(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) return true
        return runCatching {
            FirebaseApp.initializeApp(appCtx) != null || FirebaseApp.getApps(appCtx).isNotEmpty()
        }.getOrDefault(false)
    }

    fun resolveWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        return if (resId != 0) context.getString(resId) else ""
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
                IllegalStateException("Firebase is not initialized in this environment.")
            )
        }
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            return Result.failure(
                IllegalStateException("Google Sign-In client ID is missing from configuration.")
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
            Log.w("Auth", "Sign-in cancelled or unconfigured: ${e.message}", e)
            Result.failure(e)
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
