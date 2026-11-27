package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.GitHubReleaseInfo
import com.example.util.InAppUpdateDialogState

@Composable
fun InAppUpdateDialog(
    state: InAppUpdateDialogState,
    onConfirmUpdate: (GitHubReleaseInfo) -> Unit,
    onDismissLater: (tagName: String?) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenReleasePageFallback: (String) -> Unit
) {
    when (state) {
        InAppUpdateDialogState.Hidden -> Unit

        is InAppUpdateDialogState.Prompt -> {
            val release = state.release
            val bullets = release.changelogBullets.ifEmpty {
                listOf("Bug fixes and performance improvements.")
            }
            AlertDialog(
                onDismissRequest = { onDismissLater(release.tagName) },
                icon = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "MicroRC Update",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Update Available",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "v${state.currentVersion} → ${release.tagName}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                                    .testTag("update_dialog_version_badge")
                            )
                        }
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_dialog_changelog")
                    ) {
                        Text(
                            text = "What's new",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        bullets.take(5).forEach { bullet ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmUpdate(release) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("update_dialog_confirm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Now", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onDismissLater(release.tagName) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("update_dialog_later_button")
                    ) {
                        Text("Later", style = MaterialTheme.typography.labelLarge)
                    }
                },
                modifier = Modifier.testTag("in_app_update_dialog")
            )
        }

        is InAppUpdateDialogState.Downloading -> {
            val pct = state.progressPercent.coerceIn(0, 100)
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloading Update",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                },
                title = {
                    Text(
                        text = "Downloading ${state.release.tagName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.indeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .testTag("update_dialog_progress")
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .testTag("update_dialog_progress")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloading MicroRC APK…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (state.indeterminate) "…" else "$pct%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("update_dialog_progress_text")
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("update_dialog_cancel_button")
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                },
                modifier = Modifier.testTag("in_app_update_dialog")
            )
        }

        is InAppUpdateDialogState.NeedsInstallPermission -> {
            AlertDialog(
                onDismissRequest = { onDismissLater(null) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Install permission required",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                },
                title = {
                    Text(
                        text = "Allow App Installs",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "MicroRC ${state.release.tagName} is ready to install. Enable \"Allow from this source\" in Android Settings and return here — installation will continue automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("update_dialog_permission_explanation")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onOpenUnknownSourcesSettings,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("update_dialog_open_settings_button")
                    ) {
                        Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onDismissLater(null) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("update_dialog_later_button")
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                },
                modifier = Modifier.testTag("in_app_update_dialog")
            )
        }

        is InAppUpdateDialogState.Error -> {
            AlertDialog(
                onDismissRequest = { onDismissLater(null) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Update error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Failed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmUpdate(state.release) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("update_dialog_retry_button")
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { onOpenReleasePageFallback(state.release.releasePageUrl) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("GitHub Page")
                        }
                        TextButton(
                            onClick = { onDismissLater(null) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Close")
                        }
                    }
                },
                modifier = Modifier.testTag("in_app_update_dialog")
            )
        }
    }
}
