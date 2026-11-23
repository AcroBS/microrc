package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DailyChallengeCatalog
import com.example.model.GameMode
import com.example.model.LeaderboardNotificationDoc
import com.example.model.SessionInviteDoc
import java.util.Locale

data class PendingLobbyInvite(
    val roomCode: String,
    val joinToken: String = "",
    val inviteId: String = ""
)

data class PendingDailyChallengeRoute(
    val dateKey: String
)

data class PendingLeaderboardRoute(
    val boardKey: String,
    val trackKey: String,
    val gameMode: String,
    val isDaily: Boolean = false
)

object InviteLinkHelper {
    private const val WEB_HOST = "microrc.game"
    private const val CUSTOM_SCHEME = "microrc"
    private val URL_REGEX = Regex("""https?://microrc\.game/join[^\s]*|microrc://join[^\s]*""", RegexOption.IGNORE_CASE)
    private val HASH_CODE_REGEX = Regex("""#([A-Z0-9]{4,12})\b""", RegexOption.IGNORE_CASE)
    private val CODE_ONLY_REGEX = Regex("""^[A-Z0-9]{4,12}$""")
    private val HEX_TOKEN_REGEX = Regex("""^[a-fA-F0-9]{1,64}$""")
    private val SAFE_ID_REGEX = Regex("""^[a-zA-Z0-9_\-]{1,128}$""")

    fun buildInviteUrl(roomCode: String, joinToken: String = ""): String {
        val cleanCode = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
        val builder = Uri.Builder()
            .scheme("https")
            .authority(WEB_HOST)
            .appendPath("join")
            .appendQueryParameter("code", cleanCode)
        val cleanToken = joinToken.trim()
        if (cleanToken.isNotEmpty() && HEX_TOKEN_REGEX.matches(cleanToken)) {
            builder.appendQueryParameter("token", cleanToken)
        }
        return builder.build().toString()
    }

    fun buildShareMessage(
        roomName: String,
        roomCode: String,
        gameMode: String,
        hostName: String,
        joinToken: String = ""
    ): String {
        val cleanCode = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
        val modeLabel = runCatching { GameMode.valueOf(gameMode).displayName }.getOrDefault(gameMode)
        val link = buildInviteUrl(cleanCode, joinToken)
        return "🏎️ Join $hostName in MicroRC!\n" +
            "Lobby: $roomName ($modeLabel)\n" +
            "Room Code: #$cleanCode\n" +
            "Tap to join: $link"
    }

    fun buildFriendCodeShareMessage(driverName: String, friendCode: String): String {
        return "🏁 Add me on MicroRC!\n" +
            "Driver: $driverName\n" +
            "Friend Code: $friendCode"
    }

    fun parseInviteFromIntent(intent: Intent?): PendingLobbyInvite? {
        if (intent == null) return null
        parseInviteFromUri(intent.data)?.let { return it }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) {
            return parseInviteFromText(sharedText)
        }
        return null
    }

    fun parseDailyFromIntent(intent: Intent?): PendingDailyChallengeRoute? {
        val uri = intent?.data ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme == CUSTOM_SCHEME && host == "daily") {
            val rawDate = uri.getQueryParameter("date")
            return PendingDailyChallengeRoute(dateKey = DailyChallengeCatalog.normalizeDateKey(rawDate))
        }
        return null
    }

    fun parseLeaderboardFromIntent(intent: Intent?): PendingLeaderboardRoute? {
        val uri = intent?.data ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme == CUSTOM_SCHEME && host == "leaderboard") {
            val boardKey = uri.getQueryParameter("boardKey")?.trim().orEmpty().take(96)
            val trackKey = uri.getQueryParameter("trackKey")?.trim().orEmpty().take(64)
            val gameMode = uri.getQueryParameter("gameMode")?.trim().orEmpty().ifEmpty { GameMode.RACE.name }
            if (trackKey.isNotEmpty() || boardKey.isNotEmpty()) {
                return PendingLeaderboardRoute(
                    boardKey = boardKey,
                    trackKey = trackKey,
                    gameMode = gameMode,
                    isDaily = DailyChallengeCatalog.isDailyTrackId(trackKey)
                )
            }
        }
        return null
    }

    fun parseInviteFromUri(uri: Uri?): PendingLobbyInvite? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val isWebJoin = (scheme == "https" || scheme == "http") && host == WEB_HOST
        val isCustomJoin = scheme == CUSTOM_SCHEME && host == "join"
        if (!isWebJoin && !isCustomJoin) return null

        val rawCode = uri.getQueryParameter("code")
            ?: uri.pathSegments.lastOrNull()?.takeIf { it.lowercase() != "join" }
            ?: return null
        val cleanCode = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (!CODE_ONLY_REGEX.matches(cleanCode)) return null

        val rawToken = uri.getQueryParameter("token")?.trim().orEmpty()
        val safeToken = if (HEX_TOKEN_REGEX.matches(rawToken)) rawToken.lowercase() else ""

        val rawInviteId = uri.getQueryParameter("inviteId")?.trim().orEmpty()
        val safeInviteId = if (SAFE_ID_REGEX.matches(rawInviteId)) rawInviteId else ""

        return PendingLobbyInvite(
            roomCode = cleanCode,
            joinToken = safeToken,
            inviteId = safeInviteId
        )
    }

    fun parseInviteFromText(rawText: String): PendingLobbyInvite? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        // 1. Check for embedded https://microrc.game/join... or microrc://join... URL
        val urlMatch = URL_REGEX.find(trimmed)?.value
        if (urlMatch != null) {
            val cleanUrl = urlMatch.trimEnd('.', ',', ')', ']', '!')
            parseInviteFromUri(Uri.parse(cleanUrl))?.let { return it }
        }

        // 2. Check for #CODE inside shared message text
        val hashMatch = HASH_CODE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
        if (hashMatch != null) {
            return PendingLobbyInvite(roomCode = hashMatch.uppercase())
        }

        // 3. Direct room code
        val normalized = trimmed.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (CODE_ONLY_REGEX.matches(normalized) && trimmed.length <= 16) {
            return PendingLobbyInvite(roomCode = normalized)
        }
        return null
    }

    fun shareViaWhatsApp(context: Context, message: String) {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            val waIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = runCatching {
                context.startActivity(waIntent)
                true
            }.getOrDefault(false)
            if (resolved) return
        }
        shareViaSystemChooser(context, message, "Share Invite via WhatsApp or Messenger")
    }

    fun shareViaSystemChooser(
        context: Context,
        message: String,
        chooserTitle: String = "Share MicroRC Lobby Invite"
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

object SessionNotificationHelper {
    private const val CHANNEL_ID = "microrc_session_invites"
    private const val DAILY_CHANNEL_ID = "microrc_daily_challenge"
    private const val LEADERBOARD_CHANNEL_ID = "microrc_leaderboard_alerts"
    private const val NOTIF_PREFS_NAME = "microrc_notif_prefs"
    private const val KEY_DELIVERED_LB_IDS = "delivered_leaderboard_notif_ids"
    private const val KEY_DELIVERED_INVITE_IDS = "delivered_session_invite_ids"

    private val deliveredLock = Any()

    /**
     * Atomically marks a friend leaderboard notification ID as delivered across both
     * foreground/background Firestore observers and periodic background WorkManager/alarm syncs.
     */
    internal fun claimLeaderboardNotificationDelivery(context: Context, notifId: String): Boolean {
        return claimNotificationDelivery(context, KEY_DELIVERED_LB_IDS, notifId)
    }

    /**
     * Atomically marks a friend session invite notification ID as delivered across both
     * live Firestore observers and background sync workers.
     */
    internal fun claimSessionInviteNotificationDelivery(context: Context, inviteId: String): Boolean {
        return claimNotificationDelivery(context, KEY_DELIVERED_INVITE_IDS, inviteId)
    }

    private fun claimNotificationDelivery(context: Context, prefKey: String, notifId: String): Boolean {
        val cleanId = notifId.trim()
        if (cleanId.isEmpty()) return false
        synchronized(deliveredLock) {
            val prefs = context.applicationContext.getSharedPreferences(NOTIF_PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getStringSet(prefKey, emptySet()).orEmpty()
            if (cleanId in existing) return false
            val updated = existing.toMutableList()
            updated.add(cleanId)
            val trimmed = if (updated.size > 120) updated.takeLast(120) else updated
            prefs.edit().putStringSet(prefKey, trimmed.toSet()).apply()
            return true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val inviteChannel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.session_invite_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.session_invite_channel_desc)
                enableVibration(true)
            }
            val dailyChannel = NotificationChannel(
                DAILY_CHANNEL_ID,
                context.getString(R.string.daily_challenge_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.daily_challenge_channel_desc)
            }
            val leaderboardChannel = NotificationChannel(
                LEADERBOARD_CHANNEL_ID,
                context.getString(R.string.leaderboard_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.leaderboard_alert_channel_desc)
            }
            nm.createNotificationChannel(inviteChannel)
            nm.createNotificationChannel(dailyChannel)
            nm.createNotificationChannel(leaderboardChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showSessionInviteNotification(context: Context, invite: SessionInviteDoc): Boolean {
        if (!hasNotificationPermission(context)) return false
        val dedupeKey = invite.id.ifBlank { "${invite.senderUid}_${invite.roomCode}_${invite.joinToken}" }
        if (!claimSessionInviteNotificationDelivery(context, dedupeKey)) return false
        ensureChannel(context)

        val deepLinkUri = Uri.Builder()
            .scheme("https")
            .authority("microrc.game")
            .appendPath("join")
            .appendQueryParameter("code", invite.roomCode)
            .apply {
                if (invite.joinToken.isNotBlank()) {
                    appendQueryParameter("token", invite.joinToken)
                }
                if (invite.id.isNotBlank()) {
                    appendQueryParameter("inviteId", invite.id)
                }
            }
            .build()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLinkUri
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = dedupeKey.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = runCatching { GameMode.valueOf(invite.gameMode).displayName }
            .getOrDefault(invite.gameMode)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_race_invite)
            .setContentTitle("${invite.senderName} invited you to race!")
            .setContentText("Join ${invite.roomName} (#${invite.roomCode} • $modeLabel)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
            true
        }.getOrDefault(false)
    }

    fun showDailyChallengeAvailableNotification(
        context: Context,
        dateKey: String,
        trackName: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val deepLinkUri = Uri.Builder()
            .scheme("microrc")
            .authority("daily")
            .appendQueryParameter("date", dateKey)
            .build()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLinkUri
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = ("daily_$dateKey").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dailyBody = "$trackName is live now — fair fixed track, fixed portals & 1 Nitro/lap."
        val notification = NotificationCompat.Builder(context, DAILY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_race_invite)
            .setContentTitle("New Daily Challenge Available!")
            .setContentText(dailyBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(dailyBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }

    fun showFriendLeaderboardRecordNotification(
        context: Context,
        notif: LeaderboardNotificationDoc
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        val dedupeKey = notif.id.ifBlank { "${notif.senderUid}_${notif.boardKey}_${notif.bestRaceTimeMs}_${notif.bestScore}" }
        if (!claimLeaderboardNotificationDelivery(context, dedupeKey)) return false
        ensureChannel(context)

        val deepLinkUri = Uri.Builder()
            .scheme("microrc")
            .authority("leaderboard")
            .appendQueryParameter("boardKey", notif.boardKey)
            .appendQueryParameter("trackKey", notif.trackKey)
            .appendQueryParameter("gameMode", notif.gameMode)
            .build()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLinkUri
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = dedupeKey.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val metricText = if (notif.gameMode == GameMode.STUNTS.name) {
            "${notif.bestScore} PTS"
        } else {
            formatNotifTimeMs(notif.bestRaceTimeMs)
        }
        val title = if (notif.dailyChallenge) {
            "${notif.senderName} set a Daily Challenge time!"
        } else {
            "${notif.senderName} posted a new track record!"
        }
        val body = "${notif.trackName} • $metricText — Tap to view leaderboard & race their ghost"

        val notification = NotificationCompat.Builder(context, LEADERBOARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_race_invite)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
            true
        }.getOrDefault(false)
    }

    fun formatNotifTimeMs(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val minutes = (clamped / 60_000L).toInt()
        val seconds = ((clamped % 60_000L) / 1000L).toInt()
        val hundredths = ((clamped % 1000L) / 10L).toInt()
        return if (minutes > 0) {
            String.format(Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
        } else {
            String.format(Locale.US, "%d.%02ds", seconds, hundredths)
        }
    }
}
