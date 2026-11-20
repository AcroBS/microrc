package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?
) {
    /** Direct GitHub APK asset URL when present, otherwise the GitHub Releases page. */
    val preferredUpdateUrl: String
        get() = apkDownloadUrl?.takeIf { it.isNotBlank() } ?: releasePageUrl
}

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String
    ) : AppUpdateUiState
    data class UpdateAvailable(
        val currentVersion: String,
        val release: GitHubReleaseInfo
    ) : AppUpdateUiState
    data class Unavailable(val message: String) : AppUpdateUiState
}

/**
 * Checks the latest published GitHub Release for newer MicroRC APK builds.
 * Opens the GitHub APK download URL in the system browser (no REQUEST_INSTALL_PACKAGES
 * permission or in-app binary execution, keeping Google Play Console deployment compliant).
 */
object GitHubUpdateChecker {

    private val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    fun isInstalledFromPlayStore(context: Context): Boolean {
        return runCatching {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending"
        }.getOrDefault(false)
    }

    fun normalizeVersion(raw: String): String {
        val trimmed = raw.trim()
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true) &&
            trimmed.length > 1 &&
            trimmed[1].isDigit()
        ) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        return withoutPrefix.substringBefore('-').substringBefore('+').trim()
    }

    fun parseVersionSegments(raw: String): List<Int> {
        val cleaned = normalizeVersion(raw)
        if (cleaned.isBlank()) return emptyList()
        val parts = cleaned.split('.', '_', '-')
        val nums = parts.mapNotNull { part ->
            val digits = part.takeWhile { it.isDigit() }
            digits.toIntOrNull()
        }
        return nums
    }

    fun isRemoteNewer(currentVersion: String, remoteTag: String): Boolean {
        val local = parseVersionSegments(currentVersion)
        val remote = parseVersionSegments(remoteTag)
        if (local.isEmpty() || remote.isEmpty()) return false

        val maxLen = maxOf(local.size, remote.size)
        for (i in 0 until maxLen) {
            val l = local.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun isTrustedGitHubUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "github.com" ||
            host == "www.github.com" ||
            host == "objects.githubusercontent.com" ||
            host == "release-assets.githubusercontent.com"
    }

    fun parseReleaseJson(jsonText: String): GitHubReleaseInfo? {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return null
        val obj = runCatching {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                (0 until arr.length())
                    .asSequence()
                    .mapNotNull { arr.optJSONObject(it) }
                    .firstOrNull { !it.optBoolean("draft", false) }
            } else {
                JSONObject(trimmed).takeUnless { it.optBoolean("draft", false) }
            }
        }.getOrNull() ?: return null

        val tag = obj.optString("tag_name", "").trim()
        if (tag.isBlank()) return null

        val htmlUrl = obj.optString("html_url", "").trim()
        if (!isTrustedGitHubUrl(htmlUrl)) return null

        val title = obj.optString("name", "").trim().ifBlank { "MicroRC $tag" }
        val body = obj.optString("body", "").trim()

        val assets = obj.optJSONArray("assets")
        var preferredApkUrl: String? = null
        var fallbackApkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "").trim()
                val downloadUrl = asset.optString("browser_download_url", "").trim()
                if (!assetName.endsWith(".apk", ignoreCase = true)) continue
                if (!isTrustedGitHubUrl(downloadUrl)) continue
                if (assetName.equals("MicroRC.apk", ignoreCase = true)) {
                    preferredApkUrl = downloadUrl
                    break
                } else if (fallbackApkUrl == null) {
                    fallbackApkUrl = downloadUrl
                }
            }
        }

        return GitHubReleaseInfo(
            tagName = tag,
            normalizedVersion = normalizeVersion(tag),
            releaseTitle = title,
            releaseNotes = body,
            releasePageUrl = htmlUrl,
            apkDownloadUrl = preferredApkUrl ?: fallbackApkUrl
        )
    }

    suspend fun checkLatestRelease(
        repository: String = BuildConfig.GITHUB_REPOSITORY,
        currentVersion: String = BuildConfig.VERSION_NAME,
        httpGet: suspend (String) -> Pair<Int, String?> = ::defaultHttpGet
    ): AppUpdateUiState {
        val cleanRepo = repository.trim()
        if (cleanRepo.isBlank() || !REPO_PATTERN.matches(cleanRepo)) {
            return AppUpdateUiState.Unavailable(
                "Update checks activate automatically in GitHub-built APKs."
            )
        }

        val latestUrl = "https://api.github.com/repos/$cleanRepo/releases/latest"
        val (statusCode, body) = runCatching { httpGet(latestUrl) }
            .getOrElse {
                return AppUpdateUiState.Unavailable(
                    "Couldn't reach GitHub right now. Check your internet connection."
                )
            }

        val releaseJson = when {
            statusCode in 200..299 && !body.isNullOrBlank() -> body
            statusCode == 404 -> {
                val (fallbackCode, fallbackBody) = runCatching {
                    httpGet("https://api.github.com/repos/$cleanRepo/releases?per_page=3")
                }.getOrElse {
                    return AppUpdateUiState.Unavailable("Couldn't reach GitHub right now.")
                }
                if (fallbackCode in 200..299 && !fallbackBody.isNullOrBlank()) {
                    fallbackBody
                } else {
                    return AppUpdateUiState.Unavailable("No published GitHub releases found yet.")
                }
            }
            statusCode == 403 -> {
                return AppUpdateUiState.Unavailable("GitHub rate limit reached. Try again later.")
            }
            else -> {
                return AppUpdateUiState.Unavailable("GitHub returned HTTP $statusCode.")
            }
        }

        val release = parseReleaseJson(releaseJson)
            ?: return AppUpdateUiState.Unavailable("No published GitHub releases found yet.")

        val cleanCurrent = normalizeVersion(currentVersion).ifBlank { currentVersion.trim() }
        return if (isRemoteNewer(cleanCurrent, release.tagName)) {
            AppUpdateUiState.UpdateAvailable(
                currentVersion = cleanCurrent,
                release = release
            )
        } else {
            AppUpdateUiState.UpToDate(
                currentVersion = cleanCurrent,
                latestVersion = release.normalizedVersion.ifBlank { release.tagName }
            )
        }
    }

    fun openUpdateUrl(context: Context, url: String): Boolean {
        if (!isTrustedGitHubUrl(url)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private suspend fun defaultHttpGet(urlString: String): Pair<Int, String?> =
        withContext(Dispatchers.IO) {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "MicroRC-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }
                code to text
            } finally {
                conn.disconnect()
            }
        }
}
