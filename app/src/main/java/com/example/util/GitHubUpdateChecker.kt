package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val changelogBullets: List<String> = GitHubUpdateChecker.parseReleaseBullets(releaseNotes)
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

sealed interface InAppUpdateDialogState {
    data object Hidden : InAppUpdateDialogState
    data class Prompt(
        val currentVersion: String,
        val release: GitHubReleaseInfo
    ) : InAppUpdateDialogState
    data class Downloading(
        val release: GitHubReleaseInfo,
        val progressPercent: Int,
        val indeterminate: Boolean = false
    ) : InAppUpdateDialogState
    data class NeedsInstallPermission(
        val release: GitHubReleaseInfo,
        val apkFile: File
    ) : InAppUpdateDialogState
    data class Error(
        val release: GitHubReleaseInfo,
        val message: String
    ) : InAppUpdateDialogState
}

/**
 * Queries GitHub Releases for newer MicroRC APK builds, parses concise changelog bullets,
 * downloads the APK into cacheDir/updates/ with streaming progress, and launches the
 * Android package installer via FileProvider.
 */
object GitHubUpdateChecker {

    const val DEFAULT_REPOSITORY = "AcroBS/microrc"
    const val APK_UPDATES_SUBDIR = "updates"
    const val APK_FILE_NAME = "microrc-latest.apk"
    const val APK_VERSION_META_FILE = "microrc-latest.version"

    private val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val MARKDOWN_IMAGE_REGEX = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val MARKDOWN_LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val RAW_URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val BULLET_PREFIX_REGEX = Regex("^\\s*(?:[-*•+]|\\d+[.)])\\s+")
    private val HEADER_PREFIX_REGEX = Regex("^\\s*#{1,6}\\s+")
    private val HR_REGEX = Regex("^\\s*[-*_]{3,}\\s*$")
    private val PR_TRAILER_REGEX = Regex("\\s+(?:by\\s+@[A-Za-z0-9_-]+\\s+)?in\\s+#?\\d+\\s*$", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000
    private const val DOWNLOAD_READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5
    private const val MAX_BULLET_LENGTH = 110

    fun resolvedDefaultRepository(): String =
        BuildConfig.GITHUB_REPOSITORY.trim().ifBlank { DEFAULT_REPOSITORY }

    fun apkUpdatesDir(context: Context): File = File(context.cacheDir, APK_UPDATES_SUBDIR)

    fun targetApkFile(context: Context): File = File(apkUpdatesDir(context), APK_FILE_NAME)

    fun versionMetaFile(context: Context): File = File(apkUpdatesDir(context), APK_VERSION_META_FILE)

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
        return parts.mapNotNull { part ->
            val digits = part.takeWhile { it.isDigit() }
            digits.toIntOrNull()
        }
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
            host == "release-assets.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    /**
     * Parses a GitHub release body into a compact list of up to [maxBullets] (default 5)
     * concise bullet points, stripping markdown headers, fenced code blocks, raw links,
     * and boilerplate footer lines.
     */
    fun parseReleaseBullets(body: String, maxBullets: Int = 5): List<String> {
        val limit = maxBullets.coerceIn(1, 5)
        if (body.isBlank()) return emptyList()

        val explicitBullets = mutableListOf<String>()
        val fallbackLines = mutableListOf<String>()
        var inCodeBlock = false

        for (rawLine in body.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (inCodeBlock || trimmed.isEmpty()) continue
            if (HR_REGEX.matches(trimmed)) continue
            if (HEADER_PREFIX_REGEX.containsMatchIn(trimmed)) continue

            val lower = trimmed.lowercase()
            if (lower.startsWith("**full changelog**") ||
                lower.startsWith("full changelog") ||
                lower.startsWith("compare:") ||
                lower.startsWith("sha256")
            ) {
                continue
            }

            val isBullet = BULLET_PREFIX_REGEX.containsMatchIn(trimmed)
            val withoutMarker = if (isBullet) {
                trimmed.replaceFirst(BULLET_PREFIX_REGEX, "")
            } else {
                trimmed.removePrefix(">").trim()
            }

            val cleaned = cleanMarkdownLine(withoutMarker)
            if (cleaned.length < 4) continue

            if (isBullet) {
                if (explicitBullets.none { it.equals(cleaned, ignoreCase = true) }) {
                    explicitBullets.add(cleaned)
                }
            } else {
                if (fallbackLines.none { it.equals(cleaned, ignoreCase = true) }) {
                    fallbackLines.add(cleaned)
                }
            }
        }

        val combined = if (explicitBullets.isNotEmpty()) {
            explicitBullets.take(limit)
        } else {
            fallbackLines.take(limit)
        }
        return combined
    }

    private fun cleanMarkdownLine(input: String): String {
        var text = input
            .replace(MARKDOWN_IMAGE_REGEX, "")
            .replace(MARKDOWN_LINK_REGEX, "$1")
            .replace(RAW_URL_REGEX, "")
            .replace(HTML_TAG_REGEX, "")
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace("`", "")
            .replace(PR_TRAILER_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .trimEnd(':', ';', '-', '•')
            .trim()

        if (text.startsWith("*") && text.endsWith("*") && text.length > 2) {
            text = text.substring(1, text.length - 1).trim()
        }
        if (text.length > MAX_BULLET_LENGTH) {
            val cut = text.substring(0, MAX_BULLET_LENGTH - 1)
            val spaceIdx = cut.lastIndexOf(' ')
            text = (if (spaceIdx > 48) cut.substring(0, spaceIdx) else cut).trimEnd(',', ';', '.') + "…"
        }
        return text
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
            apkDownloadUrl = preferredApkUrl ?: fallbackApkUrl,
            changelogBullets = parseReleaseBullets(body)
        )
    }

    suspend fun checkLatestRelease(
        repository: String = resolvedDefaultRepository(),
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

    /**
     * Downloads a GitHub release APK into [destFile] on Dispatchers.IO, streaming byte
     * progress (0..100) via [onProgress] (or -1 when Content-Length is unavailable).
     */
    suspend fun downloadApk(
        apkUrl: String,
        destFile: File,
        expectedVersion: String,
        onProgress: (percent: Int, indeterminate: Boolean) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isTrustedGitHubUrl(apkUrl)) {
                throw IllegalArgumentException("Untrusted update download URL.")
            }
            destFile.parentFile?.mkdirs()
            val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
            if (tempFile.exists()) tempFile.delete()

            var currentUrl = apkUrl
            var connection: HttpURLConnection? = null
            try {
                var redirects = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
                        readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                        setRequestProperty("Accept", "application/octet-stream")
                        setRequestProperty("User-Agent", "MicroRC-Android/${BuildConfig.VERSION_NAME}")
                    }
                    connection = conn
                    val code = conn.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val location = conn.getHeaderField("Location")?.trim()
                        conn.disconnect()
                        if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                            throw IllegalStateException("Too many redirects while downloading update.")
                        }
                        if (!isTrustedGitHubUrl(location)) {
                            throw IllegalArgumentException("Redirected to untrusted host.")
                        }
                        currentUrl = location
                        redirects += 1
                        continue
                    }
                    if (code !in 200..299) {
                        throw IllegalStateException("Download failed with HTTP $code.")
                    }

                    val totalBytes = conn.contentLengthLong
                    val indeterminate = totalBytes <= 0L
                    var downloadedBytes = 0L
                    var lastReportedPct = -1
                    onProgress(0, indeterminate)

                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (!indeterminate) {
                                    val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    if (pct != lastReportedPct) {
                                        lastReportedPct = pct
                                        onProgress(pct, false)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (downloadedBytes <= 0L) {
                        throw IllegalStateException("Downloaded APK file was empty.")
                    }
                    if (destFile.exists()) destFile.delete()
                    if (!tempFile.renameTo(destFile)) {
                        tempFile.copyTo(destFile, overwrite = true)
                        tempFile.delete()
                    }
                    val metaFile = File(destFile.parentFile, APK_VERSION_META_FILE)
                    metaFile.writeText(normalizeVersion(expectedVersion).ifBlank { expectedVersion.trim() })
                    onProgress(100, false)
                    break
                }
                destFile
            } catch (t: Throwable) {
                if (tempFile.exists()) tempFile.delete()
                throw t
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Deletes stale APK files inside cacheDir/updates/ once the installed app version
     * matches or exceeds the cached update (plus any abandoned .tmp download files).
     */
    fun cleanupStaleCachedApks(
        context: Context,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Int {
        val dir = apkUpdatesDir(context)
        if (!dir.exists() || !dir.isDirectory) return 0

        var deletedCount = 0
        val files = dir.listFiles().orEmpty()
        val metaFile = File(dir, APK_VERSION_META_FILE)
        val cachedVersion = metaFile.takeIf { it.exists() }
            ?.let { runCatching { it.readText().trim() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

        for (file in files) {
            if (!file.isFile) continue
            when {
                file.name.endsWith(".tmp", ignoreCase = true) -> {
                    if (file.delete()) deletedCount++
                }
                file.name.endsWith(".apk", ignoreCase = true) -> {
                    val versionToCompare = cachedVersion ?: runCatching {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.versionName
                    }.getOrNull()
                    val isStale = versionToCompare.isNullOrBlank() ||
                        !isRemoteNewer(currentVersion, versionToCompare)
                    if (isStale && file.delete()) {
                        deletedCount++
                    }
                }
            }
        }

        val remainingApks = dir.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
        if (!remainingApks && metaFile.exists()) {
            metaFile.delete()
        }
        return deletedCount
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildApkInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0L) return false
        return runCatching {
            context.startActivity(buildApkInstallIntent(context, apkFile))
            true
        }.getOrDefault(false)
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
