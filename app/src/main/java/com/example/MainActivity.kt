package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.ui.MicroRCApp
import com.example.ui.auth.GoogleAuthManager
import com.example.ui.theme.MicroRCTheme
import com.example.util.DailyChallengeScheduler
import com.example.util.InviteLinkHelper
import com.example.util.PendingDailyChallengeRoute
import com.example.util.PendingLeaderboardRoute
import com.example.util.PendingLobbyInvite
import com.example.util.SessionNotificationHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingInviteState by mutableStateOf<PendingLobbyInvite?>(null)
    private var pendingDailyRouteState by mutableStateOf<PendingDailyChallengeRoute?>(null)
    private var pendingLeaderboardRouteState by mutableStateOf<PendingLeaderboardRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GoogleAuthManager.ensureFirebaseInitialized(this)
        SessionNotificationHelper.ensureChannel(this)
        DailyChallengeScheduler.scheduleDailyReminder(this)
        DailyChallengeScheduler.scheduleLeaderboardSync(this)
        lifecycleScope.launch {
            DailyChallengeScheduler.ensureDailyNotificationPostedIfNewDay(this@MainActivity)
            DailyChallengeScheduler.syncPendingFriendLeaderboardNotifications(this@MainActivity)
        }
        handleIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            MicroRCTheme {
                MicroRCApp(
                    pendingInvite = pendingInviteState,
                    onConsumePendingInvite = { pendingInviteState = null },
                    pendingDailyRoute = pendingDailyRouteState,
                    onConsumePendingDailyRoute = { pendingDailyRouteState = null },
                    pendingLeaderboardRoute = pendingLeaderboardRouteState,
                    onConsumePendingLeaderboardRoute = { pendingLeaderboardRouteState = null }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            DailyChallengeScheduler.syncPendingFriendLeaderboardNotifications(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        InviteLinkHelper.parseInviteFromIntent(intent)?.let { parsed ->
            pendingInviteState = parsed
        }
        InviteLinkHelper.parseDailyFromIntent(intent)?.let { parsed ->
            pendingDailyRouteState = parsed
        }
        InviteLinkHelper.parseLeaderboardFromIntent(intent)?.let { parsed ->
            pendingLeaderboardRouteState = parsed
        }
    }
}
