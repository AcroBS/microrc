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
import com.example.model.GameMode
import com.example.model.SessionInviteDoc

data class PendingLobbyInvite(
    val roomCode: String,
    val joinToken: String = "",
    val inviteId: String = ""
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

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.session_invite_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.session_invite_channel_desc)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
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

    fun showSessionInviteNotification(context: Context, invite: SessionInviteDoc) {
        if (!hasNotificationPermission(context)) return
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

        val requestCode = invite.id.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = runCatching { GameMode.valueOf(invite.gameMode).displayName }
            .getOrDefault(invite.gameMode)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${invite.senderName} invited you to race!")
            .setContentText("Join ${invite.roomName} (#${invite.roomCode} • $modeLabel)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }
}
