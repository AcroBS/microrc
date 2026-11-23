package com.example

import com.example.data.DailyChallengeCatalog
import com.example.data.FriendStandingsCalculator
import com.example.data.PlayStreakCalculator
import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.LeaderboardEntryDoc
import com.example.model.LocalLeaderboardRecord
import com.example.model.RcCarType
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAndStandingsUnitTest {

    @Test
    fun playStreakCalculator_tracksFirstSameDayConsecutiveAndResetCorrectly() {
        val utc = TimeZone.getTimeZone("UTC")

        // 1. First activity ever -> 1 day streak, activeToday = true
        val day1 = PlayStreakCalculator.recordActivity(
            storedCurrent = 0,
            storedBest = 0,
            lastDateKey = "",
            todayKey = "20261118",
            timeZone = utc
        )
        assertEquals(1, day1.currentStreakDays)
        assertEquals(1, day1.bestStreakDays)
        assertEquals("20261118", day1.lastActiveDateKey)
        assertTrue(day1.activeToday)

        // 2. Repeated activity on same day is idempotent
        val sameDay = PlayStreakCalculator.recordActivity(
            storedCurrent = day1.currentStreakDays,
            storedBest = day1.bestStreakDays,
            lastDateKey = day1.lastActiveDateKey,
            todayKey = "20261118",
            timeZone = utc
        )
        assertEquals(1, sameDay.currentStreakDays)
        assertEquals(1, sameDay.bestStreakDays)
        assertTrue(sameDay.activeToday)

        // 3. Display check next day before racing -> streak alive (at risk), activeToday = false
        val morningDay2 = PlayStreakCalculator.resolveForDisplay(
            storedCurrent = sameDay.currentStreakDays,
            storedBest = sameDay.bestStreakDays,
            lastDateKey = sameDay.lastActiveDateKey,
            todayKey = "20261119",
            timeZone = utc
        )
        assertEquals(1, morningDay2.currentStreakDays)
        assertEquals(1, morningDay2.bestStreakDays)
        assertFalse(morningDay2.activeToday)

        // 4. Consecutive day race increments streak and best
        val day2 = PlayStreakCalculator.recordActivity(
            storedCurrent = sameDay.currentStreakDays,
            storedBest = sameDay.bestStreakDays,
            lastDateKey = sameDay.lastActiveDateKey,
            todayKey = "20261119",
            timeZone = utc
        )
        assertEquals(2, day2.currentStreakDays)
        assertEquals(2, day2.bestStreakDays)
        assertTrue(day2.activeToday)

        val day3 = PlayStreakCalculator.recordActivity(
            storedCurrent = day2.currentStreakDays,
            storedBest = day2.bestStreakDays,
            lastDateKey = day2.lastActiveDateKey,
            todayKey = "20261120",
            timeZone = utc
        )
        assertEquals(3, day3.currentStreakDays)
        assertEquals(3, day3.bestStreakDays)

        // 5. Missing a full calendar day expires display streak to 0 while preserving best = 3
        val expiredDisplay = PlayStreakCalculator.resolveForDisplay(
            storedCurrent = day3.currentStreakDays,
            storedBest = day3.bestStreakDays,
            lastDateKey = day3.lastActiveDateKey,
            todayKey = "20261122",
            timeZone = utc
        )
        assertEquals(0, expiredDisplay.currentStreakDays)
        assertEquals(3, expiredDisplay.bestStreakDays)
        assertFalse(expiredDisplay.activeToday)

        // 6. Racing after gap restarts current streak at 1 while preserving best = 3
        val restarted = PlayStreakCalculator.recordActivity(
            storedCurrent = day3.currentStreakDays,
            storedBest = day3.bestStreakDays,
            lastDateKey = day3.lastActiveDateKey,
            todayKey = "20261122",
            timeZone = utc
        )
        assertEquals(1, restarted.currentStreakDays)
        assertEquals(3, restarted.bestStreakDays)
        assertTrue(restarted.activeToday)
    }

    @Test
    fun friendStandingsCalculator_mergesLocalAndCloudAndRanksCrew() {
        val myUid = "uid_me"
        val friendAlexUid = "uid_alex"
        val friendSamUid = "uid_sam"
        val todayDate = "20261123"
        val dailyTrackKey = DailyChallengeCatalog.trackIdForDate(todayDate)
        val dailyBoardKey = DailyChallengeCatalog.boardKeyForDate(todayDate)

        val friends = listOf(
            FriendUiModel(
                friendshipId = "f_alex",
                friendUid = friendAlexUid,
                displayName = "TurboAlex",
                friendCode = "RCALEX11",
                carType = RcCarType.APEX_FORMULA,
                primaryColorArgb = 0xFFFF5722,
                accentColorArgb = 0xFFFFD54F,
                effectivelyOnline = true,
                activityStatus = "ONLINE",
                activeRoomId = "",
                activeRoomCode = "",
                activeRoomName = "",
                statusLabel = "Online"
            ),
            FriendUiModel(
                friendshipId = "f_sam",
                friendUid = friendSamUid,
                displayName = "DriftSam",
                friendCode = "RCSAM222",
                carType = RcCarType.VOLT_BUGGY,
                primaryColorArgb = 0xFF00E5FF,
                accentColorArgb = 0xFFFFFFFF,
                effectivelyOnline = false,
                activityStatus = "OFFLINE",
                activeRoomId = "",
                activeRoomCode = "",
                activeRoomName = "",
                statusLabel = "Offline"
            )
        )

        val cloudEntries = listOf(
            // Daily GP: Friend Alex = 42_000ms (1st), Me in cloud = 43_500ms (2nd, +1.50s)
            LeaderboardEntryDoc(
                id = "${friendAlexUid}_$dailyBoardKey",
                userId = friendAlexUid,
                driverName = "TurboAlex",
                gameMode = GameMode.RACE.name,
                trackKey = dailyTrackKey,
                boardKey = dailyBoardKey,
                totalLaps = 3L,
                bestRaceTimeMs = 42_000L
            ),
            LeaderboardEntryDoc(
                id = "${myUid}_$dailyBoardKey",
                userId = myUid,
                driverName = "MeDriver",
                gameMode = GameMode.RACE.name,
                trackKey = dailyTrackKey,
                boardKey = dailyBoardKey,
                totalLaps = 3L,
                bestRaceTimeMs = 43_500L
            ),
            // Track GP_APEX: Friend Sam = 38_000ms, cloud Me = 39_000ms, but local PB is faster at 36_500ms!
            LeaderboardEntryDoc(
                id = "${friendSamUid}_RACE_GP_APEX_3L",
                userId = friendSamUid,
                driverName = "DriftSam",
                gameMode = GameMode.RACE.name,
                trackKey = "GP_APEX",
                boardKey = "RACE_GP_APEX_3L",
                totalLaps = 3L,
                bestRaceTimeMs = 38_000L
            ),
            LeaderboardEntryDoc(
                id = "${myUid}_RACE_GP_APEX_3L",
                userId = myUid,
                driverName = "MeDriver",
                gameMode = GameMode.RACE.name,
                trackKey = "GP_APEX",
                boardKey = "RACE_GP_APEX_3L",
                totalLaps = 3L,
                bestRaceTimeMs = 39_000L
            ),
            // Stunts Gauntlet: Me = 4200 pts vs Alex = 3900 pts (Me leads by +300)
            LeaderboardEntryDoc(
                id = "${myUid}_STUNTS_GAUNTLET_4R",
                userId = myUid,
                driverName = "MeDriver",
                gameMode = GameMode.STUNTS.name,
                trackKey = "STUNTS_GAUNTLET",
                boardKey = "STUNTS_GAUNTLET_4R",
                bestScore = 4200L
            ),
            LeaderboardEntryDoc(
                id = "${friendAlexUid}_STUNTS_GAUNTLET_4R",
                userId = friendAlexUid,
                driverName = "TurboAlex",
                gameMode = GameMode.STUNTS.name,
                trackKey = "STUNTS_GAUNTLET",
                boardKey = "STUNTS_GAUNTLET_4R",
                bestScore = 3900L
            ),
            // Unchallenged friend board where only Sam has set a time
            LeaderboardEntryDoc(
                id = "${friendSamUid}_RACE_LIVING_ROOM_GP_3L",
                userId = friendSamUid,
                driverName = "DriftSam",
                gameMode = GameMode.RACE.name,
                trackKey = "LIVING_ROOM_GP",
                boardKey = "RACE_LIVING_ROOM_GP_3L",
                totalLaps = 3L,
                bestRaceTimeMs = 31_000L
            )
        )

        val localRecords = listOf(
            LocalLeaderboardRecord(
                boardKey = "RACE_GP_APEX_3L",
                gameMode = GameMode.RACE.name,
                trackKey = "GP_APEX",
                totalLaps = 3,
                driverName = "MeDriver",
                carType = RcCarType.VOLT_BUGGY.name,
                primaryColorArgb = 0xFFFF5722,
                accentColorArgb = 0xFFFFD54F,
                bestRaceTimeMs = 36_500L
            )
        )

        val summary = FriendStandingsCalculator.compute(
            myUid = myUid,
            myName = "MeDriver",
            friends = friends,
            cloudEntries = cloudEntries,
            localRecords = localRecords,
            todayDateKey = todayDate,
            maxHighlights = 3
        )

        // Me won GP_APEX (via faster local PB) & STUNTS_GAUNTLET (2 contested wins), trailed Daily GP (1)
        assertEquals(2, summary.leadingBoardsCount)
        assertEquals(1, summary.trailingBoardsCount)
        assertEquals(1, summary.unchallengedFriendBoardsCount)
        assertEquals(3, summary.crewSizeWithEntries)
        assertEquals(1, summary.myCrewRank)

        // Highlight #0 must be today's Daily GP trailing TurboAlex by +1500ms
        assertEquals(3, summary.highlightBoards.size)
        val firstHighlight = summary.highlightBoards.first()
        assertEquals(dailyBoardKey, firstHighlight.boardKey)
        assertEquals(2, firstHighlight.myRank)
        assertEquals("TurboAlex", firstHighlight.topFriendName)
        assertEquals(1_500L, firstHighlight.deltaVsTopFriendMs)

        // GP_APEX must reflect merged faster local PB (36_500ms vs Sam's 38_000ms -> -1_500ms)
        val apexItem = summary.highlightBoards.find { it.boardKey == "RACE_GP_APEX_3L" }
        assertNotNull(apexItem)
        assertEquals(1, apexItem!!.myRank)
        assertEquals(36_500L, apexItem.myTimeMs)
        assertEquals(-1_500L, apexItem.deltaVsTopFriendMs)

        // Stunts must reflect +300 score delta
        val stuntsItem = summary.highlightBoards.find { it.boardKey == "STUNTS_GAUNTLET_4R" }
        assertNotNull(stuntsItem)
        assertEquals(300, stuntsItem!!.deltaVsTopFriendScore)
    }
}
