package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.ui.auth.GoogleAuthManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

class OnlineServerTimeoutException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

object FirestoreDatabaseResolver {
    const val AI_STUDIO_PROVISIONED_PROJECT_ID = "gen-lang-client-0040246566"
    const val DEFAULT_FIRESTORE_DATABASE_ID = "(default)"

    fun resolveDatabaseId(
        configuredResourceId: String,
        activeProjectId: String?,
        ciDatabaseOverride: String = ""
    ): String {
        val cleanOverride = ciDatabaseOverride.trim()
        if (cleanOverride.isNotEmpty()) {
            return cleanOverride
        }
        val cleanConfigured = configuredResourceId.trim()
        val cleanProject = activeProjectId?.trim().orEmpty()
        if (cleanConfigured.startsWith("ai-studio-") &&
            cleanProject.isNotEmpty() &&
            cleanProject != AI_STUDIO_PROVISIONED_PROJECT_ID
        ) {
            return DEFAULT_FIRESTORE_DATABASE_ID
        }
        return cleanConfigured.ifEmpty { DEFAULT_FIRESTORE_DATABASE_ID }
    }

    fun resolveDatabaseId(context: Context): String {
        val appContext = context.applicationContext
        GoogleAuthManager.ensureFirebaseInitialized(appContext)
        val configured = appContext.getString(R.string.firestore_database_id)
        val projectId = runCatching {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                FirebaseApp.getInstance().options.projectId
            } else {
                null
            }
        }.getOrNull()
        return resolveDatabaseId(
            configuredResourceId = configured,
            activeProjectId = projectId,
            ciDatabaseOverride = BuildConfig.CI_FIRESTORE_DATABASE_ID
        )
    }
}

suspend fun <T> withFirestoreTimeout(
    timeoutMs: Long = 10_000L,
    actionLabel: String = "online race server",
    block: suspend () -> T
): T {
    return try {
        withTimeout(timeoutMs) {
            block()
        }
    } catch (timeout: TimeoutCancellationException) {
        throw OnlineServerTimeoutException(
            "Could not connect to the $actionLabel in time. Please check your internet connection and try again.",
            timeout
        )
    }
}

fun isSilentCoroutineCancellation(error: Throwable): Boolean {
    val chain = generateSequence(error) { it.cause }.toList()
    if (chain.any {
            it is OnlineServerTimeoutException ||
                it is TimeoutCancellationException ||
                it is FirebaseFirestoreException
        }
    ) {
        return false
    }
    if (chain.any { it is CancellationException }) {
        return true
    }
    return chain.any { cause ->
        val msg = cause.message.orEmpty()
        msg.contains("StandaloneCoroutine was cancelled", ignoreCase = true) ||
            msg.contains("ScopeCoroutine was cancelled", ignoreCase = true) ||
            msg.contains("Job was cancelled", ignoreCase = true) ||
            msg.contains("Child of the scoped flow was cancelled", ignoreCase = true)
    }
}

fun rethrowIfScopeCancellation(e: Throwable) {
    if (e is CancellationException &&
        e !is TimeoutCancellationException &&
        generateSequence(e.cause) { it.cause }.none { it is FirebaseFirestoreException }
    ) {
        throw e
    }
}

fun formatUserFacingOnlineErrorOrNull(error: Throwable, fallback: String): String? {
    if (isSilentCoroutineCancellation(error)) return null
    return formatUserFacingOnlineError(error, fallback)
}

fun formatUserFacingOnlineError(error: Throwable, fallback: String): String {
    if (isSilentCoroutineCancellation(error)) {
        return fallback
    }
    val chain = generateSequence(error) { it.cause }.toList()
    val timeoutErr = chain.firstOrNull {
        it is OnlineServerTimeoutException || it is TimeoutCancellationException
    }
    if (timeoutErr != null) {
        return timeoutErr.message
            ?: "Could not connect to the online lobby server in time. Please check your internet connection and try again."
    }

    val firestoreErr = chain.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreErr != null) {
        return when (firestoreErr.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Online lobby access was denied by server permissions. Please sign out and sign in again, or verify cloud database rules."
            FirebaseFirestoreException.Code.NOT_FOUND,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "Online lobby database is not ready yet. Please try again in a moment."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "Online race server is temporarily unreachable. Check your internet connection and try again."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Your sign-in session expired. Please sign out and sign in with Google again."
            else ->
                firestoreErr.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    if (error is IllegalArgumentException || error is IllegalStateException) {
        return error.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    return error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
