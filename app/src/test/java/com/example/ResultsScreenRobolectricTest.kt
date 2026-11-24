package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.GameMode
import com.example.model.GhostRunPlayback
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RematchCountdownState
import com.example.model.RematchVoteOption
import com.example.model.TrackId
import com.example.model.WinCondition
import com.example.network.SessionRole
import com.example.ui.screens.ResultsScreen
import com.example.ui.theme.MicroRCTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResultsScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleSnapshot(
        finishTimeMs: Long = 45_000L,
        gameMode: GameMode = GameMode.RACE,
        isHybridTour: Boolean = false
    ): RaceSnapshot {
        val racer = RacerState(
            playerId = "local_p1",
            name = "Driver 1",
            carType = "APEX_FORMULA",
            primaryColorArgb = 0xFFE53935L,
            accentColorArgb = 0xFFFFFFFFL,
            x = 0f,
            y = 0f,
            angleRad = 0f,
            lap = 3,
            finished = true,
            finishTimeMs = finishTimeMs,
            bestLapMs = 14_500L,
            lapTimesMs = listOf(15_200L, 14_500L, 15_300L),
            rank = 1
        )
        return RaceSnapshot(
            raceTimeMs = finishTimeMs,
            phase = RacePhase.FINISHED.name,
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            racers = listOf(racer),
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            gameMode = gameMode.name,
            isHybridTour = isHybridTour
        )
    }

    private fun sampleRematchState(): RematchCountdownState {
        return RematchCountdownState(
            secondsLeft = 8,
            totalSeconds = 10,
            options = listOf(
                RematchVoteOption(
                    id = TrackId.WAREHOUSE_DRIFT.name,
                    displayName = "Warehouse Drift Yard",
                    subtitle = "3 Laps"
                ),
                RematchVoteOption(
                    id = "RANDOM",
                    displayName = "Random Track",
                    subtitle = "Next random circuit",
                    isRandom = true
                )
            ),
            leadingOptionId = "RANDOM"
        )
    }

    @Test
    fun resultsScreen_showsTryAgainInRematchCardWhenRecordNotBrokenAndInvokesCallback() {
        var tryAgainClicks = 0
        composeTestRule.setContent {
            MicroRCTheme {
                ResultsScreen(
                    role = SessionRole.Solo,
                    localPlayerId = "local_p1",
                    results = sampleSnapshot(finishTimeMs = 46_200L),
                    lobby = null,
                    rematchState = sampleRematchState(),
                    isNewPersonalBest = false,
                    lastRunRecordEvaluated = true,
                    onTryAgainSameTrack = { tryAgainClicks++ },
                    onReturnToLobby = {},
                    onExitHome = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rematch_countdown_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("results_try_again_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("results_try_again_button").performClick()
        assertEquals(1, tryAgainClicks)
    }

    @Test
    fun resultsScreen_hidesTryAgainWhenNewPersonalBestSetWithoutMissedGhost() {
        composeTestRule.setContent {
            MicroRCTheme {
                ResultsScreen(
                    role = SessionRole.Solo,
                    localPlayerId = "local_p1",
                    results = sampleSnapshot(finishTimeMs = 42_000L),
                    lobby = null,
                    rematchState = sampleRematchState(),
                    isNewPersonalBest = true,
                    lastRunRecordEvaluated = true,
                    challengeGhost = null,
                    onTryAgainSameTrack = {},
                    onReturnToLobby = {},
                    onExitHome = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rematch_countdown_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("results_try_again_button").assertDoesNotExist()
    }

    @Test
    fun resultsScreen_showsTryAgainWhenChallengeGhostWasFasterEvenOnLocalBest() {
        var tryAgainClicks = 0
        val rivalGhost = GhostRunPlayback(
            ghostId = "ghost_1",
            driverName = "Rival",
            carType = "APEX_FORMULA",
            primaryColorArgb = 0xFF1E88E5L,
            accentColorArgb = 0xFFFFFFFFL,
            boardKey = "board_1",
            trackId = TrackId.LIVING_ROOM_GP.name,
            totalLaps = 3,
            finishTimeMs = 40_000L,
            bestLapMs = 13_000L,
            frames = emptyList()
        )
        composeTestRule.setContent {
            MicroRCTheme {
                ResultsScreen(
                    role = SessionRole.Solo,
                    localPlayerId = "local_p1",
                    results = sampleSnapshot(finishTimeMs = 43_500L),
                    lobby = null,
                    rematchState = sampleRematchState(),
                    isNewPersonalBest = true,
                    lastRunRecordEvaluated = true,
                    challengeGhost = rivalGhost,
                    onTryAgainSameTrack = { tryAgainClicks++ },
                    onReturnToLobby = {},
                    onExitHome = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("results_try_again_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("results_try_again_button").performClick()
        assertEquals(1, tryAgainClicks)
    }

    @Test
    fun resultsScreen_hidesTryAgainInMultiplayerHostMode() {
        composeTestRule.setContent {
            MicroRCTheme {
                ResultsScreen(
                    role = SessionRole.Host,
                    localPlayerId = "local_p1",
                    results = sampleSnapshot(finishTimeMs = 46_200L),
                    lobby = null,
                    rematchState = sampleRematchState(),
                    isNewPersonalBest = false,
                    lastRunRecordEvaluated = true,
                    onTryAgainSameTrack = {},
                    onReturnToLobby = {},
                    onExitHome = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("results_try_again_button").assertDoesNotExist()
    }

    @Test
    fun resultsScreen_hidesTryAgainInArenaHybridAndBeforeRecordEvaluated() {
        composeTestRule.setContent {
            MicroRCTheme {
                ResultsScreen(
                    role = SessionRole.Solo,
                    localPlayerId = "local_p1",
                    results = sampleSnapshot(
                        finishTimeMs = 46_200L,
                        gameMode = GameMode.ARENA,
                        isHybridTour = false
                    ),
                    lobby = null,
                    rematchState = sampleRematchState(),
                    isNewPersonalBest = false,
                    lastRunRecordEvaluated = true,
                    onTryAgainSameTrack = {},
                    onReturnToLobby = {},
                    onExitHome = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("results_try_again_button").assertDoesNotExist()
    }
}
