package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MicroRCTheme
import com.example.util.AppUpdateUiState
import com.example.util.GitHubReleaseInfo
import com.example.util.GitHubUpdateChecker
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
import org.robolectric.annotation.Config

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
                    onOpenTrackEditor = {},
                    onOpenSettings = {},
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
}
