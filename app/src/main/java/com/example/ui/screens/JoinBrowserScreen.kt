package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.DiscoveredLobby
import com.example.model.TrackId
import com.example.network.WifiP2pRaceManager
import com.example.ui.theme.AcidLime
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CarbonBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.PaddockOutline
import com.example.ui.theme.PaddockSurface
import com.example.ui.theme.PaddockSurfaceElevated
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextSecondary

@Composable
fun JoinBrowserScreen(
    lobbies: List<DiscoveredLobby>,
    connectionError: String?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onJoinLobby: (DiscoveredLobby) -> Unit,
    onJoinDirectIp: (String, Int) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit
) {
    var showDirectIpDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onStartScan()
        onDispose { onStopScan() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("browser_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("JOIN WI-FI RACE", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Scanning local Wi-Fi & Hotspot", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                IconButton(onClick = onStartScan) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = ElectricCyan)
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (connectionError != null) {
                    Surface(
                        color = AlertRed.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AlertRed)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = connectionError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissError) { Text("Dismiss", color = AlertRed) }
                        }
                    }
                }

                // Active scanning banner
                Surface(
                    color = PaddockSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ElectricCyan,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = if (lobbies.isEmpty()) {
                                "Listening for hosted lobbies on LAN & Hotspot..."
                            } else {
                                "Found ${lobbies.size} live ${if (lobbies.size == 1) "lobby" else "lobbies"} nearby"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                    }
                }

                if (lobbies.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaddockSurface),
                        border = BorderStroke(1.dp, PaddockOutline),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.WifiFind,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("No Races Found Yet", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Ask 1 friend to tap Host Race on the same Wi-Fi network or mobile Hotspot. Still not showing? Use Direct IP below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(lobbies, key = { it.hostId }) { lobby ->
                            val trackName = runCatching {
                                TrackId.valueOf(lobby.trackId).displayName
                            }.getOrDefault(lobby.trackId)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PaddockSurface),
                                border = BorderStroke(1.dp, PaddockOutline),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lobby.lobbyName, style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            text = "Track: $trackName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ElectricCyan
                                        )
                                        Text(
                                            text = "${lobby.hostIp}:${lobby.tcpPort} • ${lobby.playerCount}/${lobby.maxPlayers} Racers",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Button(
                                        onClick = { onJoinLobby(lobby) },
                                        enabled = !lobby.inRace && lobby.playerCount < lobby.maxPlayers,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (lobby.inRace) PaddockSurfaceElevated else AcidLime,
                                            contentColor = CarbonBg
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("join_lobby_${lobby.hostId}")
                                    ) {
                                        Text(if (lobby.inRace) "Racing" else "Join")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = lobbies.isEmpty()))

                OutlinedButton(
                    onClick = { showDirectIpDialog = true },
                    border = BorderStroke(1.dp, SignalOrange.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .height(52.dp)
                        .testTag("direct_ip_button")
                ) {
                    Icon(Icons.Default.Lan, contentDescription = null, tint = SignalOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect by Direct Host IP", color = SignalOrange, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showDirectIpDialog) {
        var ipText by remember { mutableStateOf("192.168.43.1") }
        var portText by remember { mutableStateOf(WifiP2pRaceManager.DEFAULT_TCP_PORT.toString()) }
        AlertDialog(
            onDismissRequest = { showDirectIpDialog = false },
            containerColor = PaddockSurface,
            title = { Text("Direct IP Join", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the Host IP displayed at the top of the host's Lobby screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("Host IP Address") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("direct_ip_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: WifiP2pRaceManager.DEFAULT_TCP_PORT
                        showDirectIpDialog = false
                        onJoinDirectIp(ipText.trim(), port)
                    },
                    modifier = Modifier.testTag("confirm_direct_ip_button")
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showDirectIpDialog = false }) { Text("Cancel") }
            }
        )
    }
}
