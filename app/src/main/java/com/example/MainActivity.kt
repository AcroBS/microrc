package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.MicroRCApp
import com.example.ui.auth.GoogleAuthManager
import com.example.ui.theme.MicroRCTheme
import com.example.util.InviteLinkHelper
import com.example.util.PendingLobbyInvite
import com.example.util.SessionNotificationHelper

class MainActivity : ComponentActivity() {
    private var pendingInviteState by mutableStateOf<PendingLobbyInvite?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GoogleAuthManager.ensureFirebaseInitialized(this)
        SessionNotificationHelper.ensureChannel(this)
        InviteLinkHelper.parseInviteFromIntent(intent)?.let { parsed ->
            pendingInviteState = parsed
        }
        enableEdgeToEdge()
        setContent {
            MicroRCTheme {
                MicroRCApp(
                    pendingInvite = pendingInviteState,
                    onConsumePendingInvite = { pendingInviteState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        InviteLinkHelper.parseInviteFromIntent(intent)?.let { parsed ->
            pendingInviteState = parsed
        }
    }
}
