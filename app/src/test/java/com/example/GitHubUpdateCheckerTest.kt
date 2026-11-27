package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.ui.components.InAppUpdateDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MicroRCTheme
import com.example.util.AppUpdateUiState
import com.example.util.GitHubReleaseInfo
import com.example.util.GitHubUpdateChecker
import com.example.util.InAppUpdateDialogState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubUpdateCheckerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun versionComparison_handlesNumericSegmentsAndTags() {
        assertTrue(GitHubUpdateChecker.isRemoteNewer("1.0", "v1.0.1"))
        assertTrue(GitHubUpdateChecker.isRemoteNewer("1.0.9", "v1.0.10"))
        assertTrue(GitHubUpdateChecker.isRemoteNewer("1.0.42", "v1.1.0"))
        assertTrue(GitHubUpdateChecker.isRemoteNewer("1.0.42", "2.0.0"))
        assertFalse(GitHubUpdateChecker.isRemoteNewer("1.0.12", "v1.0.12"))
        assertFalse(GitHubUpdateChecker.isRemoteNewer("1.0.15", "v1.0.12"))
        assertFalse(GitHubUpdateChecker.isRemoteNewer("1.0.0", "v1.0"))
        assertFalse(GitHubUpdateChecker.isRemoteNewer("1.0.5", "not-a-version"))
    }

    @Test
    fun parseReleaseJson_prefersMicroRcApkAssetAndValidatesGitHubUrls() {
        val json = """
            {
              "tag_name": "v1.0.45",
              "name": "MicroRC v1.0.45",
              "body": "Material You menu refresh",
              "draft": false,
              "html_url": "https://github.com/testuser/MicroRC/releases/tag/v1.0.45",
              "assets": [
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/testuser/MicroRC/releases/download/v1.0.45/checksums.txt"
                },
                {
                  "name": "MicroRC.apk",
                  "browser_download_url": "https://github.com/testuser/MicroRC/releases/download/v1.0.45/MicroRC.apk"
                }
              ]
            }
        """.trimIndent()

        val info = GitHubUpdateChecker.parseReleaseJson(json)
        assertNotNull(info)
        assertEquals("v1.0.45", info!!.tagName)
        assertEquals("1.0.45", info.normalizedVersion)
        assertEquals(
            "https://github.com/testuser/MicroRC/releases/download/v1.0.45/MicroRC.apk",
            info.preferredUpdateUrl
        )

        // Untrusted non-GitHub URLs are rejected
        assertFalse(GitHubUpdateChecker.isTrustedGitHubUrl("https://evil.example.com/MicroRC.apk"))
        assertFalse(GitHubUpdateChecker.isTrustedGitHubUrl("http://github.com/testuser/MicroRC"))
        assertTrue(GitHubUpdateChecker.isTrustedGitHubUrl("https://github.com/testuser/MicroRC/releases/latest"))

        // Draft releases are ignored
        val draftJson = """{"tag_name":"v1.0.99","draft":true,"html_url":"https://github.com/a/b/releases/tag/v1.0.99"}"""
        assertNull(GitHubUpdateChecker.parseReleaseJson(draftJson))
    }

    @Test
    fun checkLatestRelease_detectsUpdateAndUpToDateStates() = runTest {
        val sampleLatest = """
            {
              "tag_name": "v1.0.48",
              "name": "MicroRC v1.0.48",
              "html_url": "https://github.com/owner/microrc/releases/tag/v1.0.48",
              "assets": [
                {
                  "name": "MicroRC.apk",
                  "browser_download_url": "https://github.com/owner/microrc/releases/download/v1.0.48/MicroRC.apk"
                }
              ]
            }
        """.trimIndent()

        val updateState = GitHubUpdateChecker.checkLatestRelease(
            repository = "owner/microrc",
            currentVersion = "1.0.42",
            httpGet = { 200 to sampleLatest }
        )
        assertTrue(updateState is AppUpdateUiState.UpdateAvailable)
        val avail = updateState as AppUpdateUiState.UpdateAvailable
        assertEquals("v1.0.48", avail.release.tagName)
        assertEquals(
            "https://github.com/owner/microrc/releases/download/v1.0.48/MicroRC.apk",
            avail.release.preferredUpdateUrl
        )

        val upToDate = GitHubUpdateChecker.checkLatestRelease(
            repository = "owner/microrc",
            currentVersion = "1.0.48",
            httpGet = { 200 to sampleLatest }
        )
        assertTrue(upToDate is AppUpdateUiState.UpToDate)

        // Fallback to /releases list when /releases/latest returns 404
        val listFallback = GitHubUpdateChecker.checkLatestRelease(
            repository = "owner/microrc",
            currentVersion = "1.0.40",
            httpGet = { url ->
                if (url.endsWith("/latest")) 404 to null else 200 to "[$sampleLatest]"
            }
        )
        assertTrue(listFallback is AppUpdateUiState.UpdateAvailable)

        // Blank repository returns Unavailable cleanly
        val unconfigured = GitHubUpdateChecker.checkLatestRelease(
            repository = "",
            currentVersion = "1.0.40"
        )
        assertTrue(unconfigured is AppUpdateUiState.Unavailable)
    }

    @Test
    fun homeScreen_showsUpdateHintBannerAndInvokesCallbacks() {
        var downloadedUrl: String? = null
        var dismissedTag: String? = null
        val release = GitHubReleaseInfo(
            tagName = "v1.0.50",
            normalizedVersion = "1.0.50",
            releaseTitle = "MicroRC v1.0.50",
            releaseNotes = "New update",
            releasePageUrl = "https://github.com/owner/microrc/releases/tag/v1.0.50",
            apkDownloadUrl = "https://github.com/owner/microrc/releases/download/v1.0.50/MicroRC.apk"
        )

        composeTestRule.setContent {
            MicroRCTheme {
                HomeScreen(
                    profile = PlayerProfile(playerId = "p1", name = "Alex"),
                    onHostRace = {},
                    onJoinRace = {},
                    onSoloRace = {},
                    onOpenGarage = {},
                    onUpdateDriverName = {},
                    availableUpdate = AppUpdateUiState.UpdateAvailable(
                        currentVersion = "1.0.42",
                        release = release
                    ),
                    onDownloadUpdate = { downloadedUrl = it.preferredUpdateUrl },
                    onDismissUpdate = { dismissedTag = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("home_update_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Update Available • v1.0.50").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_update_download_button").performClick()
        assertEquals(release.apkDownloadUrl, downloadedUrl)

        composeTestRule.onNodeWithTag("home_update_dismiss_button").performClick()
        assertEquals("v1.0.50", dismissedTag)
    }

    @Test
    fun settingsScreen_showsGitHubUpdateCardAndRespectsPlayStoreInstall() {
        var openedUrl: String? = null
        val release = GitHubReleaseInfo(
            tagName = "v1.0.51",
            normalizedVersion = "1.0.51",
            releaseTitle = "MicroRC v1.0.51",
            releaseNotes = "",
            releasePageUrl = "https://github.com/owner/microrc/releases/tag/v1.0.51",
            apkDownloadUrl = "https://github.com/owner/microrc/releases/download/v1.0.51/MicroRC.apk"
        )

        composeTestRule.setContent {
            MicroRCTheme {
                SettingsScreen(
                    rules = RaceRulesSettings(),
                    canEditRules = true,
                    inSession = false,
                    onUpdateRules = {},
                    appVersionName = "1.0.42",
                    updateState = AppUpdateUiState.UpdateAvailable("1.0.42", release),
                    isPlayStoreInstall = false,
                    onOpenUpdateUrl = { openedUrl = it },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_app_update_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_update_download_button").performScrollTo().performClick()
        assertEquals(release.apkDownloadUrl, openedUrl)
    }

    @Test
    fun parseReleaseBullets_stripsMarkdownHeadersLinksAndLimitsToFiveConciseItems() {
        val rawBody = """
            ## What's Changed
            ### Highlights
            - **Brake-Release Launch Boost** added for 3-2-1-GO starts by @acro in #42
            - Lock Nitro activation during countdown — see [PR details](https://github.com/AcroBS/microrc/pull/43)
            - Minimalist Daily GP & Daily Drift completion checkmarks https://github.com/AcroBS/microrc
            - Direct in-app APK updater with live progress bar
            - Crew Ranking leaderboard on Play home screen
            - Extra sixth bullet that should be trimmed out
            ---
            **Full Changelog**: https://github.com/AcroBS/microrc/compare/v1.0.50...v1.0.51
        """.trimIndent()

        val bullets = GitHubUpdateChecker.parseReleaseBullets(rawBody)
        assertEquals("Should cap concise bullets at 5", 5, bullets.size)
        assertEquals("Brake-Release Launch Boost added for 3-2-1-GO starts", bullets[0])
        assertTrue(bullets[1].contains("PR details"))
        assertFalse("Raw URLs must be stripped", bullets.any { it.contains("https://") })
        assertFalse("Markdown headers must be stripped", bullets.any { it.contains("What's Changed") || it.contains("#") })
        assertFalse("Full Changelog footer must be stripped", bullets.any { it.contains("Full Changelog") })
    }

    @Test
    fun cleanupStaleCachedApks_deletesCachedApkWhenInstalledVersionMatchesOrExceeds() {
        val context = RuntimeEnvironment.getApplication()
        val updatesDir = GitHubUpdateChecker.apkUpdatesDir(context)
        updatesDir.mkdirs()
        val cachedApk = GitHubUpdateChecker.targetApkFile(context)
        val metaFile = GitHubUpdateChecker.versionMetaFile(context)
        val tmpFile = File(updatesDir, "microrc-latest.apk.tmp")

        // 1) Cached update is newer than installed version -> keep .apk, clean leftover .tmp
        cachedApk.writeBytes(byteArrayOf(1, 2, 3, 4))
        metaFile.writeText("1.0.60")
        tmpFile.writeBytes(byteArrayOf(9, 9))

        val cleanedWhileNewer = GitHubUpdateChecker.cleanupStaleCachedApks(context, currentVersion = "1.0.50")
        assertEquals(1, cleanedWhileNewer)
        assertTrue("Newer cached APK should remain before install", cachedApk.exists())
        assertFalse("Temp file should always be removed", tmpFile.exists())

        // 2) Installed version now matches or exceeds cached update -> delete stale .apk & meta
        val cleanedOnceUpdated = GitHubUpdateChecker.cleanupStaleCachedApks(context, currentVersion = "1.0.60")
        assertEquals(1, cleanedOnceUpdated)
        assertFalse("Stale cached APK must be deleted once installed matches", cachedApk.exists())
        assertFalse("Version sidecar must be cleaned up when no APKs remain", metaFile.exists())
    }

    @Test
    fun inAppUpdateDialog_rendersPromptBulletsDownloadingProgressAndPermissionPrompt() {
        val release = GitHubReleaseInfo(
            tagName = "v1.0.60",
            normalizedVersion = "1.0.60",
            releaseTitle = "MicroRC v1.0.60",
            releaseNotes = """
                ## Release Notes
                - Perfect Launch boost on brake release
                - Daily Sweep crown badge in Hero Banner
                - Direct in-app GitHub APK self-updater
            """.trimIndent(),
            releasePageUrl = "https://github.com/AcroBS/microrc/releases/tag/v1.0.60",
            apkDownloadUrl = "https://github.com/AcroBS/microrc/releases/download/v1.0.60/MicroRC.apk"
        )
        val dialogState = mutableStateOf<InAppUpdateDialogState>(
            InAppUpdateDialogState.Prompt(currentVersion = "1.0.55", release = release)
        )
        var confirmedRelease: GitHubReleaseInfo? = null
        var dismissedTag: String? = null
        var openedUnknownSources = 0

        composeTestRule.setContent {
            MicroRCTheme {
                InAppUpdateDialog(
                    state = dialogState.value,
                    onConfirmUpdate = { confirmedRelease = it },
                    onDismissLater = { dismissedTag = it },
                    onCancelDownload = {},
                    onOpenUnknownSourcesSettings = { openedUnknownSources++ },
                    onOpenReleasePageFallback = {}
                )
            }
        }

        // 1) Prompt shows version badge, 3 concise bullets, Update Now & Later
        composeTestRule.onNodeWithTag("update_dialog_version_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Perfect Launch boost on brake release").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Sweep crown badge in Hero Banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Direct in-app GitHub APK self-updater").assertIsDisplayed()

        composeTestRule.onNodeWithTag("update_dialog_confirm_button").performClick()
        assertEquals(release, confirmedRelease)

        composeTestRule.onNodeWithTag("update_dialog_later_button").performClick()
        assertEquals("v1.0.60", dismissedTag)

        // 2) Downloading shows live LinearProgressIndicator and percentage
        composeTestRule.runOnIdle {
            dialogState.value = InAppUpdateDialogState.Downloading(release = release, progressPercent = 64)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("update_dialog_progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("64%").assertIsDisplayed()

        // 3) Missing unknown app sources permission shows explanation & Open Settings button
        composeTestRule.runOnIdle {
            dialogState.value = InAppUpdateDialogState.NeedsInstallPermission(
                release = release,
                apkFile = File(RuntimeEnvironment.getApplication().cacheDir, "updates/microrc-latest.apk")
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("update_dialog_permission_explanation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_dialog_open_settings_button").performClick()
        assertEquals(1, openedUnknownSources)
    }
}
