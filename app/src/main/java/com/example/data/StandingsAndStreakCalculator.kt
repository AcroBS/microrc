package com.example.data

import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.LeaderboardEntryDoc
import com.example.model.LocalLeaderboardRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PlayStreakState(
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastActiveDateKey: String = "",
    val activeToday: Boolean = false
) {
    val isFiveDayMilestone: Boolean
        get() = currentStreakDays >= 5 && currentStreakDays % 5 == 0
}

object PlayStreakCalculator {
    private val DATE_REGEX = Regex("""^\d{8}$""")

    fun localDateKey(
        epochMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return fmt.format(Date(epochMillis))
    }

    fun previousLocalDateKey(
        dateKey: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (!DATE_REGEX.matches(dateKey)) return ""
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            this.timeZone = timeZone
            isLenient = false
        }
        val parsed = runCatching { fmt.parse(dateKey) }.getOrNull() ?: return ""
        val cal = Calendar.getInstance(timeZone, Locale.US).apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return fmt.format(cal.time)
    }

    fun resolveForDisplay(
        storedCurrent: Int,
        storedBest: Int,
        lastDateKey: String?,
        todayKey: String = localDateKey(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PlayStreakState {
        val cleanLast = lastDateKey?.trim().orEmpty()
        val safeCurrent = storedCurrent.coerceAtLeast(0)
        val safeBest = maxOf(storedBest.coerceAtLeast(0), safeCurrent)
        if (cleanLast.isEmpty() || !DATE_REGEX.matches(cleanLast)) {
            return PlayStreakState(
                currentStreakDays = 0,
                bestStreakDays = safeBest,
                lastActiveDateKey = "",
                activeToday = false
            )
        }
        if (cleanLast == todayKey) {
            val activeCount = safeCurrent.coerceAtLeast(1)
            return PlayStreakState(
                currentStreakDays = activeCount,
                bestStreakDays = maxOf(safeBest, activeCount),
                lastActiveDateKey = cleanLast,
                activeToday = true
            )
        }
        val yesterdayKey = previousLocalDateKey(todayKey, timeZone)
        if (cleanLast == yesterdayKey) {
            val aliveCount = safeCurrent.coerceAtLeast(1)
            return PlayStreakState(
                currentStreakDays = aliveCount,
                bestStreakDays = maxOf(safeBest, aliveCount),
                lastActiveDateKey = cleanLast,
                activeToday = false
            )
        }
        // More than 1 local calendar day missed -> streak reset to 0 while preserving personal best
        return PlayStreakState(
            currentStreakDays = 0,
            bestStreakDays = safeBest,
            lastActiveDateKey = cleanLast,
            activeToday = false
        )
    }

    fun recordActivity(
        storedCurrent: Int,
        storedBest: Int,
        lastDateKey: String?,
        todayKey: String = localDateKey(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PlayStreakState {
        val cleanLast = lastDateKey?.trim().orEmpty()
        val safeCurrent = storedCurrent.coerceAtLeast(0)
        val safeBest = storedBest.coerceAtLeast(0)

        val nextCurrent = when {
            cleanLast == todayKey -> safeCurrent.coerceAtLeast(1)
            cleanLast == previousLocalDateKey(todayKey, timeZone) -> safeCurrent + 1
            else -> 1
        }
        val nextBest = maxOf(safeBest, nextCurrent)
        return PlayStreakState(
            currentStreakDays = nextCurrent,
            bestStreakDays = nextBest,
            lastActiveDateKey = todayKey,
            activeToday = true
        )
    }
}

data class FriendBoardStandingItem(
    val boardKey: String,
    val trackKey: String,
    val trackName: String,
    val gameMode: GameMode,
    val totalLaps: Int,
    val isDaily: Boolean,
    val myRank: Int?,
    val totalDriversOnBoard: Int,
    val myTimeMs: Long,
    val myScore: Int,
    val leaderUid: String,
    val leaderName: String,
    val topFriendName: String?,
    val topFriendTimeMs: Long,
    val topFriendScore: Int,
    val deltaVsTopFriendMs: Long?,
    val deltaVsTopFriendScore: Int?
)

data class FriendCrewMemberRank(
    val userId: String,
    val displayName: String,
    val isSelf: Boolean,
    val rank: Int,
    val contestedWins: Int,
    val totalLeads: Int,
    val h2hMatchupsWon: Int,
    val boardsEntered: Int,
    val overallPoints: Int = 0
)

data class FriendStandingsSummary(
    val myCrewRank: Int = 0,
    val totalCrewCount: Int = 0,
    val crewSizeWithEntries: Int = 0,
    val totalFriendsCount: Int = 0,
    val leadingBoardsCount: Int = 0,
    val trailingBoardsCount: Int = 0,
    val soloOnlyBoardsCount: Int = 0,
    val unchallengedFriendBoardsCount: Int = 0,
    val crewLeaderboard: List<FriendCrewMemberRank> = emptyList(),
    val highlightBoards: List<FriendBoardStandingItem> = emptyList()
)

private data class NormalizedStandingEntry(
    val userId: String,
    val driverName: String,
    val boardKey: String,
    val trackKey: String,
    val gameMode: GameMode,
    val totalLaps: Int,
    val bestRaceTimeMs: Long,
    val bestScore: Int
)

object FriendStandingsCalculator {

    private val DAILY_BOARD_DATE_REGEX = Regex("""^RACE_daily_(\d{8})(?:_|$)""")

    internal fun isExpiredDailyBoard(
        trackKey: String,
        boardKey: String,
        todayDateKey: String
    ): Boolean {
        val dateFromTrack = DailyChallengeCatalog.extractDateKey(trackKey)
        if (dateFromTrack != null) {
            return dateFromTrack != todayDateKey
        }
        val boardMatch = DAILY_BOARD_DATE_REGEX.find(boardKey)
        if (boardMatch != null) {
            return boardMatch.groupValues[1] != todayDateKey
        }
        return false
    }

    fun compute(
        myUid: String?,
        myName: String,
        friends: List<FriendUiModel>,
        cloudEntries: List<LeaderboardEntryDoc>,
        localRecords: List<LocalLeaderboardRecord>,
        todayDateKey: String = DailyChallengeCatalog.todayDateKey(),
        maxHighlights: Int = 3
    ): FriendStandingsSummary {
        val friendMap = friends.associateBy { it.friendUid }
        val cleanMyUid = myUid?.takeIf { it.isNotBlank() } ?: "local_self"
        val cleanMyName = myName.trim().ifEmpty { "You" }

        // 1. Collect valid cloud entries for self + friends (excluding past/expired Daily Challenges)
        val mergedByUserAndBoard = LinkedHashMap<Pair<String, String>, NormalizedStandingEntry>()
        for (doc in cloudEntries) {
            if (doc.userId.isBlank() || doc.boardKey.isBlank()) continue
            if (doc.userId != cleanMyUid && doc.userId !in friendMap) continue
            if (isExpiredDailyBoard(doc.trackKey, doc.boardKey, todayDateKey)) continue
            val mode = runCatching { GameMode.valueOf(doc.gameMode) }.getOrDefault(GameMode.RACE)
            val isStunts = mode == GameMode.STUNTS || doc.boardKey.startsWith("STUNTS_")
            val valid = if (isStunts) doc.bestScore > 0L else doc.bestRaceTimeMs > 0L
            if (!valid) continue

            val resolvedName = when {
                doc.userId == cleanMyUid -> cleanMyName
                else -> friendMap[doc.userId]?.displayName?.takeIf { it.isNotBlank() }
                    ?: doc.driverName.ifBlank { "Friend" }
            }
            val candidate = NormalizedStandingEntry(
                userId = doc.userId,
                driverName = resolvedName,
                boardKey = doc.boardKey,
                trackKey = doc.trackKey,
                gameMode = mode,
                totalLaps = doc.totalLaps.toInt().coerceIn(1, 9),
                bestRaceTimeMs = doc.bestRaceTimeMs,
                bestScore = doc.bestScore.toInt()
            )
            val key = candidate.userId to candidate.boardKey
            mergedByUserAndBoard[key] = pickBetter(mergedByUserAndBoard[key], candidate, isStunts)
        }

        // 2. Merge local solo PBs for self (excluding past/expired Daily Challenges)
        for (rec in localRecords) {
            if (rec.boardKey.isBlank()) continue
            if (isExpiredDailyBoard(rec.trackKey, rec.boardKey, todayDateKey)) continue
            val mode = runCatching { GameMode.valueOf(rec.gameMode) }.getOrDefault(GameMode.RACE)
            val isStunts = mode == GameMode.STUNTS || rec.boardKey.startsWith("STUNTS_")
            val valid = if (isStunts) rec.bestScore > 0 else rec.bestRaceTimeMs > 0L
            if (!valid) continue

            val candidate = NormalizedStandingEntry(
                userId = cleanMyUid,
                driverName = cleanMyName,
                boardKey = rec.boardKey,
                trackKey = rec.trackKey,
                gameMode = mode,
                totalLaps = rec.totalLaps.coerceIn(1, 9),
                bestRaceTimeMs = rec.bestRaceTimeMs,
                bestScore = rec.bestScore
            )
            val key = cleanMyUid to rec.boardKey
            mergedByUserAndBoard[key] = pickBetter(mergedByUserAndBoard[key], candidate, isStunts)
        }

        // 3. Group by boardKey and rank drivers on every board
        val byBoard = mergedByUserAndBoard.values.groupBy { it.boardKey }
        val contestedWins = mutableMapOf<String, Int>()
        val totalLeads = mutableMapOf<String, Int>()
        val h2hWins = mutableMapOf<String, Int>()
        val boardsEntered = mutableMapOf<String, Int>()
        val crewNames = mutableMapOf<String, String>().apply {
            put(cleanMyUid, cleanMyName)
            friends.forEach { put(it.friendUid, it.displayName.ifBlank { "Friend" }) }
        }

        var leadingBoardsCount = 0
        var trailingBoardsCount = 0
        var soloOnlyBoardsCount = 0
        var unchallengedFriendBoardsCount = 0
        val boardItems = ArrayList<FriendBoardStandingItem>(byBoard.size)
        val todayDailyBoardKey = DailyChallengeCatalog.boardKeyForDate(todayDateKey)

        for ((boardKey, rawEntries) in byBoard) {
            val sample = rawEntries.first()
            val isStunts = sample.gameMode == GameMode.STUNTS || boardKey.startsWith("STUNTS_")
            val sorted = if (isStunts) {
                rawEntries.sortedWith(
                    compareByDescending<NormalizedStandingEntry> { it.bestScore }
                        .thenBy { it.bestRaceTimeMs }
                        .thenBy { it.userId != cleanMyUid }
                )
            } else {
                rawEntries.sortedWith(
                    compareBy<NormalizedStandingEntry> { it.bestRaceTimeMs }
                        .thenBy { it.userId != cleanMyUid }
                )
            }

            sorted.forEachIndexed { idx, entry ->
                boardsEntered[entry.userId] = (boardsEntered[entry.userId] ?: 0) + 1
                crewNames.putIfAbsent(entry.userId, entry.driverName)
                if (idx == 0) {
                    totalLeads[entry.userId] = (totalLeads[entry.userId] ?: 0) + 1
                    if (sorted.size >= 2) {
                        contestedWins[entry.userId] = (contestedWins[entry.userId] ?: 0) + 1
                    }
                }
                if (sorted.size >= 2) {
                    val beatenCount = sorted.size - 1 - idx
                    if (beatenCount > 0) {
                        h2hWins[entry.userId] = (h2hWins[entry.userId] ?: 0) + beatenCount
                    }
                }
            }

            val myIdx = sorted.indexOfFirst { it.userId == cleanMyUid }
            val myEntry = if (myIdx >= 0) sorted[myIdx] else null
            val friendEntries = sorted.filter { it.userId != cleanMyUid }
            val topFriend = friendEntries.firstOrNull()
            val leader = sorted.first()

            if (myEntry != null && topFriend != null) {
                if (myIdx == 0) leadingBoardsCount++ else trailingBoardsCount++
            } else if (myEntry != null) {
                soloOnlyBoardsCount++
            } else if (topFriend != null) {
                unchallengedFriendBoardsCount++
            }

            val deltaMs = if (!isStunts && myEntry != null && topFriend != null) {
                myEntry.bestRaceTimeMs - topFriend.bestRaceTimeMs
            } else {
                null
            }
            val deltaScore = if (isStunts && myEntry != null && topFriend != null) {
                myEntry.bestScore - topFriend.bestScore
            } else {
                null
            }

            val isDaily = DailyChallengeCatalog.isDailyTrackId(sample.trackKey)
            boardItems.add(
                FriendBoardStandingItem(
                    boardKey = boardKey,
                    trackKey = sample.trackKey,
                    trackName = DailyChallengeCatalog.resolveTrackDisplayName(sample.trackKey),
                    gameMode = sample.gameMode,
                    totalLaps = sample.totalLaps,
                    isDaily = isDaily,
                    myRank = if (myIdx >= 0) myIdx + 1 else null,
                    totalDriversOnBoard = sorted.size,
                    myTimeMs = myEntry?.bestRaceTimeMs ?: 0L,
                    myScore = myEntry?.bestScore ?: 0,
                    leaderUid = leader.userId,
                    leaderName = leader.driverName,
                    topFriendName = topFriend?.driverName,
                    topFriendTimeMs = topFriend?.bestRaceTimeMs ?: 0L,
                    topFriendScore = topFriend?.bestScore ?: 0,
                    deltaVsTopFriendMs = deltaMs,
                    deltaVsTopFriendScore = deltaScore
                )
            )
        }

        // 4. Overall ranking across ALL friends + yourself (including friends with 0 entries yet)
        val allRosterUids = (listOf(cleanMyUid) + friends.map { it.friendUid } + boardsEntered.keys)
            .filter { it.isNotBlank() }
            .distinct()

        val sortedCrew = allRosterUids.sortedWith(
            compareByDescending<String> { contestedWins[it] ?: 0 }
                .thenByDescending { h2hWins[it] ?: 0 }
                .thenByDescending { totalLeads[it] ?: 0 }
                .thenByDescending { boardsEntered[it] ?: 0 }
                .thenBy { it != cleanMyUid }
                .thenBy { crewNames[it]?.lowercase(Locale.US).orEmpty() }
        )
        val crewLeaderboard = sortedCrew.mapIndexed { index, uid ->
            val wins = contestedWins[uid] ?: 0
            val h2h = h2hWins[uid] ?: 0
            val leads = totalLeads[uid] ?: 0
            val boards = boardsEntered[uid] ?: 0
            val points = wins * 3 + h2h + boards
            FriendCrewMemberRank(
                userId = uid,
                displayName = crewNames[uid] ?: "Driver",
                isSelf = uid == cleanMyUid,
                rank = index + 1,
                contestedWins = wins,
                totalLeads = leads,
                h2hMatchupsWon = h2h,
                boardsEntered = boards,
                overallPoints = points
            )
        }
        val myCrewRank = crewLeaderboard.firstOrNull { it.isSelf }?.rank ?: 1
        val crewWithEntriesCount = boardsEntered.size

        // 5. Prioritize highlights for HomeScreen:
        //    Today's Daily GP > Shared tracks with friends > Unchallenged friend records > Personal PBs
        val highlighted = boardItems.sortedWith(
            compareByDescending<FriendBoardStandingItem> { it.boardKey == todayDailyBoardKey }
                .thenByDescending { it.myRank != null && it.topFriendName != null }
                .thenByDescending { it.myRank == null && it.topFriendName != null }
                .thenByDescending { it.totalDriversOnBoard }
                .thenBy { it.trackName }
        ).take(maxHighlights)

        return FriendStandingsSummary(
            myCrewRank = myCrewRank,
            totalCrewCount = crewLeaderboard.size,
            crewSizeWithEntries = crewWithEntriesCount,
            totalFriendsCount = friends.size,
            leadingBoardsCount = leadingBoardsCount,
            trailingBoardsCount = trailingBoardsCount,
            soloOnlyBoardsCount = soloOnlyBoardsCount,
            unchallengedFriendBoardsCount = unchallengedFriendBoardsCount,
            crewLeaderboard = crewLeaderboard,
            highlightBoards = highlighted
        )
    }

    private fun pickBetter(
        current: NormalizedStandingEntry?,
        candidate: NormalizedStandingEntry,
        isStunts: Boolean
    ): NormalizedStandingEntry {
        if (current == null) return candidate
        return if (isStunts) {
            if (candidate.bestScore > current.bestScore) candidate else current
        } else {
            val candMs = candidate.bestRaceTimeMs
            val curMs = current.bestRaceTimeMs
            if (candMs > 0L && (curMs <= 0L || candMs < curMs)) candidate else current
        }
    }
}
