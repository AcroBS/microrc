package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlayStreakCalculator
import com.example.data.PlayStreakState
import com.example.data.PreferencesRepository
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.CarVisualCodec
import com.example.model.CarVisualUpgrades
import com.example.model.ControlInput
import com.example.model.CosmeticUpgradeSlot
import com.example.model.GameMode
import com.example.model.LobbyPlayer
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.model.WinCondition
import com.example.network.RaceTelemetryCodec
import com.example.ui.screens.GarageScreen
import com.example.ui.theme.MicroRCTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GarageCosmeticsAndStreakTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun epochForLocalDay(year: Int, monthZeroBased: Int, day: Int): Long {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun streakCountdown_countsDownCorrectlyAcrossMilestones() {
        assertEquals(5, PlayStreakCalculator.daysUntilNextUnlock(0))
        assertEquals(4, PlayStreakCalculator.daysUntilNextUnlock(1))
        assertEquals(3, PlayStreakCalculator.daysUntilNextUnlock(2))
        assertEquals(2, PlayStreakCalculator.daysUntilNextUnlock(3))
        assertEquals(1, PlayStreakCalculator.daysUntilNextUnlock(4))
        assertEquals(5, PlayStreakCalculator.daysUntilNextUnlock(5))
        assertEquals(4, PlayStreakCalculator.daysUntilNextUnlock(6))
        assertEquals(1, PlayStreakCalculator.daysUntilNextUnlock(9))
        assertEquals(5, PlayStreakCalculator.daysUntilNextUnlock(10))
        assertEquals(5, PlayStreakCalculator.daysUntilNextUnlock(15))
    }

    @Test
    fun preferencesRepository_awards5DayTokens_unlocksPerCarAndPersistsAfterStreakReset() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = PreferencesRepository(app)

        // Days 1..4 yield 0 tokens
        for (dayOffset in 1..4) {
            repo.recordPlayStreakActivity(epochForLocalDay(2026, Calendar.NOVEMBER, dayOffset))
        }
        // Repeat Day 4 (same day) is idempotent
        val day4Again = repo.recordPlayStreakActivity(epochForLocalDay(2026, Calendar.NOVEMBER, 4))
        assertEquals(4, day4Again.currentStreakDays)
        assertEquals(1, day4Again.daysUntilNextUnlock)
        var profile = repo.profileFlow.first()
        assertEquals(0, profile.earnedUpgradeTokens)
        assertEquals(0, profile.availableUpgradeTokens)

        // Attempt unlock with 0 tokens -> rejected cleanly
        val rejected = repo.unlockCarUpgrade(RcCarType.APEX_FORMULA, CosmeticUpgradeSlot.REAR_SPOILER)
        assertFalse(rejected)

        // Day 5 awards 1st permanent Upgrade Token
        val day5 = repo.recordPlayStreakActivity(epochForLocalDay(2026, Calendar.NOVEMBER, 5))
        assertEquals(5, day5.currentStreakDays)
        assertTrue(day5.isFiveDayMilestone)
        assertEquals(5, day5.daysUntilNextUnlock)
        profile = repo.profileFlow.first()
        assertEquals(1, profile.earnedUpgradeTokens)
        assertEquals(1, profile.availableUpgradeTokens)

        // Duplicate Day 5 call does NOT double-award
        repo.recordPlayStreakActivity(epochForLocalDay(2026, Calendar.NOVEMBER, 5))
        profile = repo.profileFlow.first()
        assertEquals(1, profile.earnedUpgradeTokens)

        // Unlock Rear Spoiler on Apex Formula only
        val unlockedApexSpoiler = repo.unlockCarUpgrade(RcCarType.APEX_FORMULA, CosmeticUpgradeSlot.REAR_SPOILER)
        assertTrue(unlockedApexSpoiler)
        profile = repo.profileFlow.first()
        assertEquals(1, profile.earnedUpgradeTokens)
        assertEquals(0, profile.availableUpgradeTokens)
        assertTrue(profile.upgradesFor(RcCarType.APEX_FORMULA).isUnlocked(CosmeticUpgradeSlot.REAR_SPOILER))
        assertTrue(profile.upgradesFor(RcCarType.APEX_FORMULA).isEquipped(CosmeticUpgradeSlot.REAR_SPOILER))
        // Per-car isolation: Volt Buggy remains locked
        assertFalse(profile.upgradesFor(RcCarType.VOLT_BUGGY).isUnlocked(CosmeticUpgradeSlot.REAR_SPOILER))
        assertEquals(0, profile.upgradesFor(RcCarType.VOLT_BUGGY).unlockedCount)

        // Attempting to equip a locked slot is ignored
        repo.toggleCarUpgrade(RcCarType.APEX_FORMULA, CosmeticUpgradeSlot.ROOF_LED_LIGHTBAR, true)
        profile = repo.profileFlow.first()
        assertFalse(profile.upgradesFor(RcCarType.APEX_FORMULA).isEquipped(CosmeticUpgradeSlot.ROOF_LED_LIGHTBAR))

        // Unequipping keeps Rear Spoiler unlocked
        repo.toggleCarUpgrade(RcCarType.APEX_FORMULA, CosmeticUpgradeSlot.REAR_SPOILER, false)
        profile = repo.profileFlow.first()
        assertTrue(profile.upgradesFor(RcCarType.APEX_FORMULA).isUnlocked(CosmeticUpgradeSlot.REAR_SPOILER))
        assertFalse(profile.upgradesFor(RcCarType.APEX_FORMULA).isEquipped(CosmeticUpgradeSlot.REAR_SPOILER))

        // Advance consecutive Days 6..10 -> awards 2nd token on Day 10
        for (dayOffset in 6..10) {
            repo.recordPlayStreakActivity(epochForLocalDay(2026, Calendar.NOVEMBER, dayOffset))
        }
        profile = repo.profileFlow.first()
        assertEquals(2, profile.earnedUpgradeTokens)
        assertEquals(1, profile.availableUpgradeTokens)

        // Miss 2 days (skip Nov 11-12, race Nov 13) -> streak resets to 1, unlocked part & tokens stay permanent
        val afterGap = repo.recordPlayStreakActivity(epochForLocalDay(2026, Calendar.NOVEMBER, 13))
        assertEquals(1, afterGap.currentStreakDays)
        assertEquals(10, afterGap.bestStreakDays)
        profile = repo.profileFlow.first()
        assertEquals(2, profile.earnedUpgradeTokens)
        assertEquals(1, profile.availableUpgradeTokens)
        assertTrue(profile.upgradesFor(RcCarType.APEX_FORMULA).isUnlocked(CosmeticUpgradeSlot.REAR_SPOILER))
    }

    @Test
    fun carVisualCodec_roundTripsAndMergesCloudGarageState() {
        val localMap = mapOf(
            RcCarType.APEX_FORMULA.storageId to CarVisualUpgrades(
                unlockedMask = CosmeticUpgradeSlot.REAR_SPOILER.bitFlag,
                equippedMask = 0 // intentionally unequipped locally
            )
        )
        val cloudMap = mapOf(
            RcCarType.APEX_FORMULA.storageId to CarVisualUpgrades(
                unlockedMask = CosmeticUpgradeSlot.REAR_SPOILER.bitFlag or CosmeticUpgradeSlot.WHIP_ANTENNA.bitFlag,
                equippedMask = CosmeticUpgradeSlot.REAR_SPOILER.bitFlag or CosmeticUpgradeSlot.WHIP_ANTENNA.bitFlag
            ),
            RcCarType.DRIFT_PHANTOM.storageId to CarVisualUpgrades(
                unlockedMask = CosmeticUpgradeSlot.CUSTOM_TRAIL.bitFlag,
                equippedMask = CosmeticUpgradeSlot.CUSTOM_TRAIL.bitFlag
            )
        )

        val encoded = CarVisualCodec.encode(earnedTokens = 4, upgradesByCarId = cloudMap)
        val decoded = CarVisualCodec.decode(encoded)
        assertEquals(4, decoded.earnedTokens)
        assertTrue(decoded.upgradesByCarId[RcCarType.APEX_FORMULA.storageId]!!.isEquipped(CosmeticUpgradeSlot.WHIP_ANTENNA))
        assertTrue(decoded.upgradesByCarId[RcCarType.DRIFT_PHANTOM.storageId]!!.isEquipped(CosmeticUpgradeSlot.CUSTOM_TRAIL))

        val merged = CarVisualCodec.mergeGarageStates(
            localEarnedTokens = 2,
            localUpgrades = localMap,
            cloudEarnedTokens = 4,
            cloudUpgrades = decoded.upgradesByCarId
        )
        assertEquals(4, merged.earnedTokens)
        val apexMerged = merged.upgradesByCarId[RcCarType.APEX_FORMULA.storageId]!!
        // Preserves local unequipped choice for locally-known spoiler, adopts newly discovered cloud antenna
        assertTrue(apexMerged.isUnlocked(CosmeticUpgradeSlot.REAR_SPOILER))
        assertFalse(apexMerged.isEquipped(CosmeticUpgradeSlot.REAR_SPOILER))
        assertTrue(apexMerged.isEquipped(CosmeticUpgradeSlot.WHIP_ANTENNA))
        assertTrue(merged.upgradesByCarId[RcCarType.DRIFT_PHANTOM.storageId]!!.isEquipped(CosmeticUpgradeSlot.CUSTOM_TRAIL))
    }

    @Test
    fun cosmeticUpgrades_haveZeroImpactOnPhysicsSpeedHandlingOrHitbox() {
        val track = TrackCatalog.getTrack(TrackId.LIVING_ROOM_GP)
        val rules = RaceRulesSettings(
            randomConditionsEnabled = false,
            randomJumpsEnabled = false
        )
        val stockPlayer = LobbyPlayer(
            id = "p1",
            name = "Stock",
            isHost = true,
            isBot = false,
            carType = RcCarType.APEX_FORMULA.name,
            equippedUpgradesMask = 0
        )
        val deckedOutPlayer = stockPlayer.copy(
            equippedUpgradesMask = CarVisualUpgrades.ALL_SLOTS_MASK
        )

        val engineStock = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = listOf(stockPlayer),
            rules = rules,
            random = Random(987654L)
        )
        val engineCosmetic = RacePhysicsEngine(
            track = track,
            totalLaps = 3,
            players = listOf(deckedOutPlayer),
            rules = rules,
            random = Random(987654L)
        )

        var snapA: RaceSnapshot = engineStock.step(0.016f)
        var snapB: RaceSnapshot = engineCosmetic.step(0.016f)

        for (tick in 0 until 260) {
            val steer = when {
                tick < 200 -> 0f
                tick < 230 -> 0.65f
                else -> -0.45f
            }
            val input = ControlInput(throttle = 1f, steer = steer)
            engineStock.submitInput("p1", input)
            engineCosmetic.submitInput("p1", input)
            snapA = engineStock.step(0.016f)
            snapB = engineCosmetic.step(0.016f)
        }

        val rA = snapA.racers.single()
        val rB = snapB.racers.single()
        assertEquals(0, rA.equippedUpgradesMask)
        assertEquals(CarVisualUpgrades.ALL_SLOTS_MASK, rB.equippedUpgradesMask)
        assertEquals(rA.x, rB.x, 0.0001f)
        assertEquals(rA.y, rB.y, 0.0001f)
        assertEquals(rA.vx, rB.vx, 0.0001f)
        assertEquals(rA.vy, rB.vy, 0.0001f)
        assertEquals(rA.angleRad, rB.angleRad, 0.0001f)
        assertEquals(rA.steerVisual, rB.steerVisual, 0.0001f)
        assertEquals(rA.lap, rB.lap)
    }

    @Test
    fun telemetryCodec_roundTripsEquippedCosmeticMask() {
        val snap = RaceSnapshot(
            raceTimeMs = 12_340L,
            phase = RacePhase.RACING.name,
            trackId = TrackId.NEON_ROOFTOP.name,
            totalLaps = 3,
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            gameMode = GameMode.RACE.name,
            racers = listOf(
                RacerState(
                    playerId = "u1",
                    name = "Alex",
                    carType = RcCarType.APEX_FORMULA.name,
                    primaryColorArgb = 0xFFFF5722,
                    accentColorArgb = 0xFFFFD54F,
                    equippedUpgradesMask = 85,
                    x = 1200f,
                    y = 950f,
                    angleRad = 0.25f
                ),
                RacerState(
                    playerId = "u2",
                    name = "Sam",
                    carType = RcCarType.DRIFT_PHANTOM.name,
                    primaryColorArgb = 0xFF00E5FF,
                    accentColorArgb = 0xFF0B0E14,
                    equippedUpgradesMask = CarVisualUpgrades.ALL_SLOTS_MASK,
                    x = 1140f,
                    y = 980f,
                    angleRad = 0.20f
                )
            )
        )
        val roster = listOf(
            LobbyPlayer("u1", "Alex", isHost = true, carType = RcCarType.APEX_FORMULA.name, equippedUpgradesMask = 85),
            LobbyPlayer("u2", "Sam", isHost = false, carType = RcCarType.DRIFT_PHANTOM.name, equippedUpgradesMask = CarVisualUpgrades.ALL_SLOTS_MASK)
        )
        val encoded = RaceTelemetryCodec.encodeSnapshot(44L, snap).toString(Charsets.UTF_8)
        val decoded = RaceTelemetryCodec.decodeSnapshot(encoded, roster, null)
        assertNotNull(decoded)
        assertEquals(85, decoded!!.snapshot.racers[0].equippedUpgradesMask)
        assertEquals(CarVisualUpgrades.ALL_SLOTS_MASK, decoded.snapshot.racers[1].equippedUpgradesMask)
    }

    @Test
    fun garageScreen_lockedAndUnlockedStatesRenderAndToggleLiveWithoutChangingStats() {
        val initialProfile = PlayerProfile(
            playerId = "uid_test",
            name = "Acro",
            carType = RcCarType.APEX_FORMULA,
            earnedUpgradeTokens = 1,
            carUpgradesByCarId = emptyMap()
        )

        composeTestRule.setContent {
            var profileState by remember { mutableStateOf(initialProfile) }
            MicroRCTheme {
                GarageScreen(
                    profile = profileState,
                    playStreak = PlayStreakState(
                        currentStreakDays = 5,
                        bestStreakDays = 5,
                        lastActiveDateKey = "20261126",
                        activeToday = true
                    ),
                    onSaveCarAndColors = { car, p, a ->
                        profileState = profileState.copy(carType = car, primaryColorArgb = p, accentColorArgb = a)
                    },
                    onUnlockUpgrade = { car, slot ->
                        if (profileState.availableUpgradeTokens > 0) {
                            val map = profileState.carUpgradesByCarId.toMutableMap()
                            val current = profileState.upgradesFor(car)
                            map[car.storageId] = current.withUnlockedAndEquipped(slot)
                            profileState = profileState.copy(carUpgradesByCarId = map)
                        }
                    },
                    onToggleUpgrade = { car, slot, equipped ->
                        val map = profileState.carUpgradesByCarId.toMutableMap()
                        val current = profileState.upgradesFor(car)
                        map[car.storageId] = current.withEquipped(slot, equipped)
                        profileState = profileState.copy(carUpgradesByCarId = map)
                    },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Streak & Token header pill + workbench preview
        composeTestRule.onNodeWithTag("garage_tokens_pill").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Available Upgrade Tokens: 1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Next unlock in 5 streak days • Streak: 5d").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("garage_saved_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved to Garage").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("0/8 Equipped").performScrollTo().assertIsDisplayed()

        // Carousel arrows removed; chassis silhouettes live inside the 4 chassis pills
        composeTestRule.onNodeWithTag("garage_prev_car_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("garage_next_car_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("car_option_VOLT_BUGGY").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_silhouette_VOLT_BUGGY", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_option_APEX_FORMULA").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_silhouette_APEX_FORMULA", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_option_TUNDRA_MONSTER").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_silhouette_TUNDRA_MONSTER", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_option_DRIFT_PHANTOM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("car_silhouette_DRIFT_PHANTOM", useUnmergedTree = true).assertIsDisplayed()

        // Preview locked RC Whip Antenna on workbench without spending token
        composeTestRule.onNodeWithTag("cosmetic_card_whip_antenna").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("garage_previewing_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Previewing • RC Whip Antenna").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cosmetic_card_whip_antenna").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("previewing_badge_whip_antenna", useUnmergedTree = true).assertIsDisplayed()

        // Base stats for Apex Formula: 96 / 74 / 78 / 64
        composeTestRule.onNodeWithText("96").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("74").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("78").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("64").performScrollTo().assertIsDisplayed()

        // Unlock 8th cosmetic slot (Underglow Neon Kit) using the 1 available streak token
        val unlockUnderglow = composeTestRule.onNodeWithTag("unlock_upgrade_underglow_neon").performScrollTo()
        unlockUnderglow.assertIsDisplayed()
        unlockUnderglow.assertIsEnabled()
        unlockUnderglow.performClick()
        composeTestRule.waitForIdle()

        // Token spent -> 0 available; remaining locked parts now disabled with streak prompt
        composeTestRule.onNodeWithText("Available Upgrade Tokens: 0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1/8 Equipped").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("unlock_upgrade_bigger_tires").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Equipped").performScrollTo().assertIsDisplayed()

        // Base stats remain completely unchanged
        composeTestRule.onNodeWithText("96").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("74").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("78").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("64").performScrollTo().assertIsDisplayed()

        // Toggle Underglow Neon off via instant switch -> stays unlocked ("Unequipped"), equipped count returns to 0/8
        composeTestRule.onNodeWithTag("toggle_upgrade_underglow_neon").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unequipped").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("0/8 Equipped").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Apex Formula • 1/8 Unlocked").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun garageScreen_shuffleLook_randomizesPaintAndUnlockedPartsOnly() {
        val initialPrimary = 0xFFFF5722
        val initialAccent = 0xFFFFD54F
        val unlockedMask = CosmeticUpgradeSlot.REAR_SPOILER.bitFlag or CosmeticUpgradeSlot.UNDERGLOW_NEON.bitFlag
        val initialProfile = PlayerProfile(
            playerId = "uid_shuffle",
            name = "Acro",
            carType = RcCarType.APEX_FORMULA,
            primaryColorArgb = initialPrimary,
            accentColorArgb = initialAccent,
            earnedUpgradeTokens = 2,
            carUpgradesByCarId = mapOf(
                RcCarType.APEX_FORMULA.storageId to CarVisualUpgrades(
                    unlockedMask = unlockedMask,
                    equippedMask = 0
                )
            )
        )
        var latestProfile = initialProfile

        composeTestRule.setContent {
            var profileState by remember { mutableStateOf(initialProfile) }
            MicroRCTheme {
                GarageScreen(
                    profile = profileState,
                    playStreak = PlayStreakState(currentStreakDays = 10, bestStreakDays = 10),
                    onSaveCarAndColors = { car, p, a ->
                        profileState = profileState.copy(carType = car, primaryColorArgb = p, accentColorArgb = a)
                        latestProfile = profileState
                    },
                    onApplyCarLook = { car, p, a, equippedMask ->
                        val map = profileState.carUpgradesByCarId.toMutableMap()
                        val cur = profileState.upgradesFor(car)
                        map[car.storageId] = cur.copy(equippedMask = equippedMask and cur.sanitizedUnlockedMask)
                        profileState = profileState.copy(
                            carType = car,
                            primaryColorArgb = p,
                            accentColorArgb = a,
                            carUpgradesByCarId = map
                        )
                        latestProfile = profileState
                    },
                    onUnlockUpgrade = { _, _ -> },
                    onToggleUpgrade = { _, _, _ -> },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("garage_shuffle_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Never unlocks locked slots and equips only from unlocked slots
        val afterUpgrades = latestProfile.upgradesFor(RcCarType.APEX_FORMULA)
        assertEquals(unlockedMask, afterUpgrades.sanitizedUnlockedMask)
        assertEquals(0, afterUpgrades.sanitizedEquippedMask and unlockedMask.inv())
        assertTrue(
            latestProfile.primaryColorArgb != initialPrimary ||
                latestProfile.accentColorArgb != initialAccent ||
                afterUpgrades.sanitizedEquippedMask != 0
        )
    }
}
