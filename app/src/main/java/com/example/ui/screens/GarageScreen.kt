package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlayStreakState
import com.example.model.CarVisualUpgrades
import com.example.model.CosmeticUpgradeSlot
import com.example.model.PlayerProfile
import com.example.model.RcCarType
import com.example.model.RcColorPresets
import com.example.ui.components.drawRcCar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.adaptiveOnlineColor
import kotlin.math.PI
import kotlin.random.Random

@Composable
fun GarageScreen(
    profile: PlayerProfile,
    playStreak: PlayStreakState = PlayStreakState(),
    onSaveCarAndColors: (RcCarType, Long, Long) -> Unit,
    onUnlockUpgrade: (RcCarType, CosmeticUpgradeSlot) -> Unit = { _, _ -> },
    onToggleUpgrade: (RcCarType, CosmeticUpgradeSlot, Boolean) -> Unit = { _, _, _ -> },
    onApplyCarLook: ((RcCarType, Long, Long, Int) -> Unit)? = null,
    onBack: () -> Unit
) {
    var selectedCar by remember(profile.carType) { mutableStateOf(profile.carType) }
    var primaryColorArgb by remember(profile.primaryColorArgb) { mutableLongStateOf(profile.primaryColorArgb) }
    var accentColorArgb by remember(profile.accentColorArgb) { mutableLongStateOf(profile.accentColorArgb) }

    var showCustomHue by remember { mutableStateOf(false) }
    var customHue by remember { mutableFloatStateOf(16f) }
    var previewBoostMode by remember { mutableStateOf(false) }
    var previewNightMode by remember { mutableStateOf(false) }
    var previewedLockedSlot by remember(selectedCar) { mutableStateOf<CosmeticUpgradeSlot?>(null) }

    val selectedUpgrades: CarVisualUpgrades = profile.upgradesFor(selectedCar)
    val activePreviewSlot = previewedLockedSlot?.takeUnless { selectedUpgrades.isUnlocked(it) }
    val effectivePreviewUpgrades = remember(selectedUpgrades, activePreviewSlot) {
        if (activePreviewSlot != null) {
            CarVisualUpgrades(
                unlockedMask = selectedUpgrades.sanitizedUnlockedMask or activePreviewSlot.bitFlag,
                equippedMask = selectedUpgrades.sanitizedEquippedMask or activePreviewSlot.bitFlag
            )
        } else {
            selectedUpgrades
        }
    }

    val totalCosmeticSlots = CosmeticUpgradeSlot.entries.size
    val availableTokens = profile.availableUpgradeTokens
    val daysUntilNextUnlock = playStreak.daysUntilNextUnlock
    val cars = RcCarType.entries

    val infiniteTransition = rememberInfiniteTransition(label = "garageSpin")
    val turntableAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "turntable"
    )
    val animTimeSec = (turntableAngle / (2f * PI.toFloat())) * 9f

    val goldTint = adaptiveGoldColor()
    val savedTint = adaptiveOnlineColor()

    val shuffleActiveCarLook: () -> Unit = {
        val rng = Random(System.nanoTime())
        val otherPresets = RcColorPresets.filterNot {
            it.primaryArgb == primaryColorArgb && it.accentArgb == accentColorArgb
        }.ifEmpty { RcColorPresets }
        val nextPreset = otherPresets[rng.nextInt(otherPresets.size)]
        val nextPrimary = nextPreset.primaryArgb
        val nextAccent = nextPreset.accentArgb

        val unlockedSlots = CosmeticUpgradeSlot.entries.filter { selectedUpgrades.isUnlocked(it) }
        var nextEquippedMask = 0
        if (unlockedSlots.isNotEmpty()) {
            unlockedSlots.forEach { slot ->
                if (rng.nextBoolean()) {
                    nextEquippedMask = nextEquippedMask or slot.bitFlag
                }
            }
            if (nextEquippedMask == 0 || (unlockedSlots.size > 1 && nextEquippedMask == selectedUpgrades.sanitizedEquippedMask)) {
                val forcedSlot = unlockedSlots[rng.nextInt(unlockedSlots.size)]
                nextEquippedMask = nextEquippedMask xor forcedSlot.bitFlag
                if (nextEquippedMask == 0) {
                    nextEquippedMask = forcedSlot.bitFlag
                }
            }
        }

        primaryColorArgb = nextPrimary
        accentColorArgb = nextAccent
        previewedLockedSlot = null
        if (onApplyCarLook != null) {
            onApplyCarLook(selectedCar, nextPrimary, nextAccent, nextEquippedMask)
        } else {
            onSaveCarAndColors(selectedCar, nextPrimary, nextAccent)
            unlockedSlots.forEach { slot ->
                val shouldEquip = (nextEquippedMask and slot.bitFlag) != 0
                if (selectedUpgrades.isEquipped(slot) != shouldEquip) {
                    onToggleUpgrade(selectedCar, slot, shouldEquip)
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar (pinned)
            Row(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("garage_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.garage_title).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.garage_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWideTwoPane = maxWidth >= 720.dp
                val isCompactHeight = maxHeight < 500.dp
                val previewBoxHeight: Dp = when {
                    isWideTwoPane -> 228.dp
                    isCompactHeight -> 148.dp
                    else -> 188.dp
                }

                if (isWideTwoPane) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GarageStreakTokenPill(
                                availableTokens = availableTokens,
                                daysUntilNextUnlock = daysUntilNextUnlock,
                                currentStreakDays = playStreak.currentStreakDays,
                                earnedTokens = profile.earnedUpgradeTokens,
                                goldTint = goldTint
                            )
                            GaragePinnedPreviewCard(
                                selectedCar = selectedCar,
                                primaryColor = Color(primaryColorArgb),
                                accentColor = Color(accentColorArgb),
                                effectiveUpgrades = effectivePreviewUpgrades,
                                equippedCount = selectedUpgrades.equippedCount,
                                totalSlots = totalCosmeticSlots,
                                activePreviewSlot = activePreviewSlot,
                                turntableAngle = turntableAngle,
                                animTimeSec = animTimeSec,
                                previewBoostMode = previewBoostMode,
                                previewNightMode = previewNightMode,
                                onToggleNightPreview = { previewNightMode = !previewNightMode },
                                onToggleBoostPreview = { previewBoostMode = !previewBoostMode },
                                savedTint = savedTint,
                                previewHeight = previewBoxHeight
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            GarageChassisSelectorSection(
                                cars = cars,
                                selectedCar = selectedCar,
                                profile = profile,
                                primaryColorArgb = primaryColorArgb,
                                accentColorArgb = accentColorArgb,
                                totalSlots = totalCosmeticSlots,
                                onSelectCar = { car ->
                                    selectedCar = car
                                    previewedLockedSlot = null
                                    onSaveCarAndColors(car, primaryColorArgb, accentColorArgb)
                                }
                            )
                            GarageBaseStatsCard(
                                selectedCar = selectedCar,
                                goldTint = goldTint
                            )
                            GaragePaintShellSection(
                                primaryColorArgb = primaryColorArgb,
                                accentColorArgb = accentColorArgb,
                                showCustomHue = showCustomHue,
                                customHue = customHue,
                                onToggleCustomHue = { showCustomHue = !showCustomHue },
                                onShuffleLook = shuffleActiveCarLook,
                                onSelectPreset = { preset ->
                                    primaryColorArgb = preset.primaryArgb
                                    accentColorArgb = preset.accentArgb
                                    onSaveCarAndColors(selectedCar, primaryColorArgb, accentColorArgb)
                                },
                                onHueChange = { hue ->
                                    customHue = hue
                                    val c = Color.hsv(hue, 0.88f, 0.96f)
                                    primaryColorArgb = c.toArgb().toLong() and 0xFFFFFFFFL
                                    onSaveCarAndColors(selectedCar, primaryColorArgb, accentColorArgb)
                                }
                            )
                            GarageCosmeticUpgradesSection(
                                selectedCar = selectedCar,
                                selectedUpgrades = selectedUpgrades,
                                activePreviewSlot = activePreviewSlot,
                                availableTokens = availableTokens,
                                totalSlots = totalCosmeticSlots,
                                primaryColor = Color(primaryColorArgb),
                                accentColor = Color(accentColorArgb),
                                onUnlockUpgrade = { slot ->
                                    previewedLockedSlot = null
                                    onUnlockUpgrade(selectedCar, slot)
                                },
                                onToggleUpgrade = { slot, equipped ->
                                    onToggleUpgrade(selectedCar, slot, equipped)
                                },
                                onTogglePreviewLocked = { slot ->
                                    previewedLockedSlot = if (previewedLockedSlot == slot) null else slot
                                }
                            )
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("garage_done_button")
                            ) {
                                Text("Save & Return", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                } else {
                    val lowerControlsContent: @Composable () -> Unit = {
                        GarageChassisSelectorSection(
                            cars = cars,
                            selectedCar = selectedCar,
                            profile = profile,
                            primaryColorArgb = primaryColorArgb,
                            accentColorArgb = accentColorArgb,
                            totalSlots = totalCosmeticSlots,
                            onSelectCar = { car ->
                                selectedCar = car
                                previewedLockedSlot = null
                                onSaveCarAndColors(car, primaryColorArgb, accentColorArgb)
                            }
                        )
                        GarageBaseStatsCard(
                            selectedCar = selectedCar,
                            goldTint = goldTint
                        )
                        GaragePaintShellSection(
                            primaryColorArgb = primaryColorArgb,
                            accentColorArgb = accentColorArgb,
                            showCustomHue = showCustomHue,
                            customHue = customHue,
                            onToggleCustomHue = { showCustomHue = !showCustomHue },
                            onShuffleLook = shuffleActiveCarLook,
                            onSelectPreset = { preset ->
                                primaryColorArgb = preset.primaryArgb
                                accentColorArgb = preset.accentArgb
                                onSaveCarAndColors(selectedCar, primaryColorArgb, accentColorArgb)
                            },
                            onHueChange = { hue ->
                                customHue = hue
                                val c = Color.hsv(hue, 0.88f, 0.96f)
                                primaryColorArgb = c.toArgb().toLong() and 0xFFFFFFFFL
                                onSaveCarAndColors(selectedCar, primaryColorArgb, accentColorArgb)
                            }
                        )
                        GarageCosmeticUpgradesSection(
                            selectedCar = selectedCar,
                            selectedUpgrades = selectedUpgrades,
                            activePreviewSlot = activePreviewSlot,
                            availableTokens = availableTokens,
                            totalSlots = totalCosmeticSlots,
                            primaryColor = Color(primaryColorArgb),
                            accentColor = Color(accentColorArgb),
                            onUnlockUpgrade = { slot ->
                                previewedLockedSlot = null
                                onUnlockUpgrade(selectedCar, slot)
                            },
                            onToggleUpgrade = { slot, equipped ->
                                onToggleUpgrade(selectedCar, slot, equipped)
                            },
                            onTogglePreviewLocked = { slot ->
                                previewedLockedSlot = if (previewedLockedSlot == slot) null else slot
                            }
                        )
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("garage_done_button")
                        ) {
                            Text("Save & Return", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (isCompactHeight) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GarageStreakTokenPill(
                                availableTokens = availableTokens,
                                daysUntilNextUnlock = daysUntilNextUnlock,
                                currentStreakDays = playStreak.currentStreakDays,
                                earnedTokens = profile.earnedUpgradeTokens,
                                goldTint = goldTint,
                                compact = true
                            )
                            GaragePinnedPreviewCard(
                                selectedCar = selectedCar,
                                primaryColor = Color(primaryColorArgb),
                                accentColor = Color(accentColorArgb),
                                effectiveUpgrades = effectivePreviewUpgrades,
                                equippedCount = selectedUpgrades.equippedCount,
                                totalSlots = totalCosmeticSlots,
                                activePreviewSlot = activePreviewSlot,
                                turntableAngle = turntableAngle,
                                animTimeSec = animTimeSec,
                                previewBoostMode = previewBoostMode,
                                previewNightMode = previewNightMode,
                                onToggleNightPreview = { previewNightMode = !previewNightMode },
                                onToggleBoostPreview = { previewBoostMode = !previewBoostMode },
                                savedTint = savedTint,
                                previewHeight = previewBoxHeight
                            )
                            lowerControlsContent()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Pinned Header Pill + Live Top-Down Preview Workbench Card
                            GarageStreakTokenPill(
                                availableTokens = availableTokens,
                                daysUntilNextUnlock = daysUntilNextUnlock,
                                currentStreakDays = playStreak.currentStreakDays,
                                earnedTokens = profile.earnedUpgradeTokens,
                                goldTint = goldTint,
                                compact = false
                            )
                            GaragePinnedPreviewCard(
                                selectedCar = selectedCar,
                                primaryColor = Color(primaryColorArgb),
                                accentColor = Color(accentColorArgb),
                                effectiveUpgrades = effectivePreviewUpgrades,
                                equippedCount = selectedUpgrades.equippedCount,
                                totalSlots = totalCosmeticSlots,
                                activePreviewSlot = activePreviewSlot,
                                turntableAngle = turntableAngle,
                                animTimeSec = animTimeSec,
                                previewBoostMode = previewBoostMode,
                                previewNightMode = previewNightMode,
                                onToggleNightPreview = { previewNightMode = !previewNightMode },
                                onToggleBoostPreview = { previewBoostMode = !previewBoostMode },
                                savedTint = savedTint,
                                previewHeight = previewBoxHeight
                            )

                            // Scrollable Lower Pane: Chassis Selector, Specs, Paint Shell & 2x4 Cosmetic Grid
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                lowerControlsContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageStreakTokenPill(
    availableTokens: Int,
    daysUntilNextUnlock: Int,
    currentStreakDays: Int,
    earnedTokens: Int,
    goldTint: Color,
    compact: Boolean = false
) {
    Surface(
        color = if (availableTokens > 0) {
            goldTint.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (availableTokens > 0) goldTint.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("garage_tokens_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 32.dp else 36.dp)
                    .clip(CircleShape)
                    .background(
                        if (availableTokens > 0) goldTint.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak tokens",
                    tint = if (availableTokens > 0) goldTint else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Available Upgrade Tokens: $availableTokens",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Next unlock in $daysUntilNextUnlock streak days • Streak: ${currentStreakDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            ) {
                Text(
                    text = "$earnedTokens earned",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GaragePinnedPreviewCard(
    selectedCar: RcCarType,
    primaryColor: Color,
    accentColor: Color,
    effectiveUpgrades: CarVisualUpgrades,
    equippedCount: Int,
    totalSlots: Int,
    activePreviewSlot: CosmeticUpgradeSlot?,
    turntableAngle: Float,
    animTimeSec: Float,
    previewBoostMode: Boolean,
    previewNightMode: Boolean,
    onToggleNightPreview: () -> Unit,
    onToggleBoostPreview: () -> Unit,
    savedTint: Color,
    previewHeight: Dp
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("garage_preview_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active Car Header (no redundant < / > arrows)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = selectedCar.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedCar.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = savedTint.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, savedTint.copy(alpha = 0.55f)),
                    modifier = Modifier.testTag("garage_saved_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Saved to Garage",
                            tint = savedTint,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.garage_saved_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Large Live Top-Down Workbench Preview Canvas (~2x sprite scale)
            val gridAccent = MaterialTheme.colorScheme.secondary
            val canvasBg = if (previewNightMode) Color(0xFF070A12) else MaterialTheme.colorScheme.surfaceContainerHigh
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(canvasBg)
                    .testTag("garage_car_preview_box")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = gridAccent.copy(alpha = if (previewNightMode) 0.08f else 0.12f)
                    val step = 36f
                    var gx = 0f
                    while (gx < size.width) {
                        drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f)
                        gx += step
                    }
                    var gy = 0f
                    while (gy < size.height) {
                        drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f)
                        gy += step
                    }
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // Scale car sprite generously (~2x previous fixed 2.6f on standard phones) while preventing box overflow
                    val previewScale = (minOf(size.width, size.height) / 76f).coerceIn(2.6f, 6.0f)
                    val shadowW = 46f * previewScale
                    val shadowH = 32f * previewScale
                    drawOval(
                        color = Color.Black.copy(alpha = 0.28f),
                        topLeft = Offset(cx - shadowW / 2f, cy - shadowH / 2f + 4f * previewScale),
                        size = Size(shadowW, shadowH)
                    )
                    drawRcCar(
                        x = cx,
                        y = cy,
                        angleRad = turntableAngle,
                        carType = selectedCar,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        steerVisual = 0.35f,
                        isBoosting = previewBoostMode,
                        scale = previewScale,
                        animTimeSec = animTimeSec,
                        nightIntensity = if (previewNightMode) 0.92f else 0f,
                        visualUpgrades = effectiveUpgrades
                    )
                }

                if (activePreviewSlot != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .testTag("garage_previewing_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Previewing locked part",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Previewing • ${activePreviewSlot.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Equipped Parts Count Pill (bottom-left)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "$equippedCount/$totalSlots Equipped",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Preview Lighting / Boost Inspection Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = previewNightMode,
                    onClick = onToggleNightPreview,
                    label = {
                        Text(
                            text = "Night Light Preview",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NightlightRound,
                            contentDescription = "Toggle night light preview",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("garage_night_preview_chip")
                )
                FilterChip(
                    selected = previewBoostMode,
                    onClick = onToggleBoostPreview,
                    label = {
                        Text(
                            text = "Boost Trail Preview",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Toggle boost trail preview",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("garage_boost_preview_chip")
                )
            }
        }
    }
}

@Composable
private fun GarageChassisSelectorSection(
    cars: List<RcCarType>,
    selectedCar: RcCarType,
    profile: PlayerProfile,
    primaryColorArgb: Long,
    accentColorArgb: Long,
    totalSlots: Int,
    onSelectCar: (RcCarType) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SELECT CHASSIS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cars.forEach { car ->
                    val active = car == selectedCar
                    val carUpgrades = profile.upgradesFor(car)
                    val unlockedOnCar = carUpgrades.unlockedCount
                    Surface(
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectCar(car) }
                            .testTag("car_option_${car.name}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 34.dp)
                                    .testTag("car_silhouette_${car.name}")
                            ) {
                                val silhouetteScale = (minOf(size.width, size.height) / 56f).coerceIn(0.45f, 1.35f)
                                drawRcCar(
                                    x = size.width / 2f,
                                    y = size.height / 2f,
                                    angleRad = (-PI / 2.0).toFloat(),
                                    carType = car,
                                    primaryColor = Color(primaryColorArgb),
                                    accentColor = Color(accentColorArgb),
                                    scale = silhouetteScale,
                                    visualUpgrades = carUpgrades
                                )
                            }
                            Text(
                                text = car.displayName.substringBefore(" "),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (unlockedOnCar > 0) "$unlockedOnCar/$totalSlots" else car.displayName.substringAfter(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageBaseStatsCard(
    selectedCar: RcCarType,
    goldTint: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = stringResource(R.string.garage_cosmetic_notice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatBarRow("Top Speed", selectedCar.topSpeedStat, MaterialTheme.colorScheme.primary)
            StatBarRow("Acceleration", selectedCar.accelStat, MaterialTheme.colorScheme.secondary)
            StatBarRow("Handling", selectedCar.handlingStat, goldTint)
            StatBarRow("Off-Road Grip", selectedCar.gripStat, MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun GaragePaintShellSection(
    primaryColorArgb: Long,
    accentColorArgb: Long,
    showCustomHue: Boolean,
    customHue: Float,
    onToggleCustomHue: () -> Unit,
    onShuffleLook: () -> Unit,
    onSelectPreset: (com.example.model.RcColorPreset) -> Unit,
    onHueChange: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM PAINT SHELL",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onShuffleLook,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("garage_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Look",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (showCustomHue) "Hide Hue Slider" else "Custom Hue",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleCustomHue)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                itemsIndexed(RcColorPresets) { idx, preset ->
                    val isSelected = primaryColorArgb == preset.primaryArgb && accentColorArgb == preset.accentArgb
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(preset.primaryArgb))
                            .border(
                                width = if (isSelected) 3.dp else 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { onSelectPreset(preset) }
                            .testTag("color_preset_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            val swatchCheckTint = if (Color(preset.primaryArgb).luminance() > 0.45f) {
                                Color(0xFF0B0E14)
                            } else {
                                Color.White
                            }
                            Icon(
                                Icons.Default.Check,
                                contentDescription = preset.name,
                                tint = swatchCheckTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (showCustomHue) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Custom Body Hue (${customHue.toInt()} deg)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = customHue,
                            onValueChange = onHueChange,
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(primaryColorArgb),
                                activeTrackColor = Color(primaryColorArgb)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageCosmeticUpgradesSection(
    selectedCar: RcCarType,
    selectedUpgrades: CarVisualUpgrades,
    activePreviewSlot: CosmeticUpgradeSlot?,
    availableTokens: Int,
    totalSlots: Int,
    primaryColor: Color,
    accentColor: Color,
    onUnlockUpgrade: (CosmeticUpgradeSlot) -> Unit,
    onToggleUpgrade: (CosmeticUpgradeSlot, Boolean) -> Unit,
    onTogglePreviewLocked: (CosmeticUpgradeSlot) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "COSMETIC UPGRADES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${selectedCar.displayName} • ${selectedUpgrades.unlockedCount}/$totalSlots Unlocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Tap locked to preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            val rows = CosmeticUpgradeSlot.entries.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { slot ->
                            val isUnlocked = selectedUpgrades.isUnlocked(slot)
                            val isPreviewing = !isUnlocked && activePreviewSlot == slot
                            CosmeticUpgradeCard(
                                slot = slot,
                                isUnlocked = isUnlocked,
                                isEquipped = selectedUpgrades.isEquipped(slot),
                                isPreviewing = isPreviewing,
                                availableTokens = availableTokens,
                                primaryColor = primaryColor,
                                accentColor = accentColor,
                                onUnlock = { onUnlockUpgrade(slot) },
                                onToggle = { eq -> onToggleUpgrade(slot, eq) },
                                onPreviewLocked = { onTogglePreviewLocked(slot) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CosmeticUpgradeCard(
    slot: CosmeticUpgradeSlot,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    isPreviewing: Boolean,
    availableTokens: Int,
    primaryColor: Color,
    accentColor: Color,
    onUnlock: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPreviewLocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canUnlock = !isUnlocked && availableTokens > 0
    val cardBg = when {
        isUnlocked && isEquipped -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        isPreviewing -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.46f)
        isUnlocked -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = when {
        isUnlocked && isEquipped -> MaterialTheme.colorScheme.primary
        isPreviewing -> MaterialTheme.colorScheme.secondary
        isUnlocked -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isEquipped || isPreviewing) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (!isUnlocked) Modifier.clickable(onClick = onPreviewLocked) else Modifier
            )
            .testTag("cosmetic_card_${slot.storageKey}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCosmeticPartPreviewIcon(
                            slot = slot,
                            primary = primaryColor,
                            accent = accentColor,
                            muted = !isUnlocked && !isPreviewing
                        )
                    }
                }

                if (!isUnlocked) {
                    if (isPreviewing) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.testTag("previewing_badge_${slot.storageKey}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Previewing",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Previewing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Locked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = if (isEquipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (isEquipped) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isEquipped) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = slot.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isUnlocked || isPreviewing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = slot.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isUnlocked) {
                Button(
                    onClick = onUnlock,
                    enabled = canUnlock,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("unlock_upgrade_${slot.storageKey}")
                ) {
                    Text(
                        text = if (canUnlock) "Unlock (1 Streak Token)" else "Reach 5-day streak to unlock",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(!isEquipped) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEquipped) "Equipped" else "Unequipped",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isEquipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = isEquipped,
                        onCheckedChange = onToggle,
                        modifier = Modifier.testTag("toggle_upgrade_${slot.storageKey}")
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCosmeticPartPreviewIcon(
    slot: CosmeticUpgradeSlot,
    primary: Color,
    accent: Color,
    muted: Boolean
) {
    val alpha = if (muted) 0.72f else 1f
    val p = primary.copy(alpha = alpha)
    val a = accent.copy(alpha = alpha)
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f

    when (slot) {
        CosmeticUpgradeSlot.BIGGER_TIRES -> {
            drawRoundRect(
                color = Color(0xFF1A222E).copy(alpha = alpha),
                topLeft = Offset(cx - w * 0.24f, cy - h * 0.34f),
                size = Size(w * 0.48f, h * 0.68f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            for (step in -1..1) {
                val y = cy + step * h * 0.14f
                drawLine(a, Offset(cx - w * 0.20f, y), Offset(cx + w * 0.20f, y), strokeWidth = 2.4f)
            }
        }
        CosmeticUpgradeSlot.REAR_SPOILER -> {
            drawRoundRect(
                color = Color(0xFF1A222E).copy(alpha = alpha),
                topLeft = Offset(w * 0.14f, cy - h * 0.12f),
                size = Size(w * 0.72f, h * 0.22f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRect(a, Offset(w * 0.16f, cy - h * 0.12f), Size(w * 0.68f, h * 0.08f))
            drawRoundRect(p, Offset(w * 0.12f, cy - h * 0.24f), Size(w * 0.10f, h * 0.48f), CornerRadius(2f, 2f))
            drawRoundRect(p, Offset(w * 0.78f, cy - h * 0.24f), Size(w * 0.10f, h * 0.48f), CornerRadius(2f, 2f))
        }
        CosmeticUpgradeSlot.RACING_BUMPERS -> {
            drawRoundRect(
                color = Color(0xFF263238).copy(alpha = alpha),
                topLeft = Offset(w * 0.14f, cy - h * 0.16f),
                size = Size(w * 0.72f, h * 0.32f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(a, Offset(w * 0.20f, cy - h * 0.05f), Size(w * 0.60f, h * 0.10f), CornerRadius(2f, 2f))
            drawRoundRect(p, Offset(w * 0.10f, cy - h * 0.24f), Size(w * 0.14f, h * 0.16f), CornerRadius(2f, 2f))
            drawRoundRect(p, Offset(w * 0.76f, cy - h * 0.24f), Size(w * 0.14f, h * 0.16f), CornerRadius(2f, 2f))
        }
        CosmeticUpgradeSlot.WHIP_ANTENNA -> {
            val base = Offset(w * 0.22f, h * 0.80f)
            val ctrl = Offset(w * 0.30f, h * 0.34f)
            val tip = Offset(w * 0.72f, h * 0.22f)
            val path = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
            }
            // High-contrast backing + metallic whip shaft so curve stands out in Light & Dark themes
            drawPath(
                path = path,
                color = Color(0xFF151C26).copy(alpha = alpha),
                style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                color = Color(0xFFE2E8F0).copy(alpha = alpha),
                style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            )
            // Antenna mount grommet
            drawCircle(Color(0xFF263238).copy(alpha = alpha), radius = w * 0.11f, center = base)
            drawCircle(p, radius = w * 0.06f, center = base)
            // Prominent glowing colored tip
            val tipColor = if (accent.luminance() < 0.2f) Color(0xFF00E5FF).copy(alpha = alpha) else a
            drawCircle(tipColor.copy(alpha = 0.36f * alpha), radius = w * 0.19f, center = tip)
            drawCircle(tipColor, radius = w * 0.12f, center = tip)
            drawCircle(Color.White.copy(alpha = alpha), radius = w * 0.055f, center = tip)
        }
        CosmeticUpgradeSlot.ROOF_LED_LIGHTBAR -> {
            drawRoundRect(
                color = Color(0xFF1A222E).copy(alpha = alpha),
                topLeft = Offset(w * 0.12f, cy - h * 0.14f),
                size = Size(w * 0.76f, h * 0.28f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            for (i in 0 until 4) {
                val lx = w * (0.24f + i * 0.17f)
                drawCircle(Color(0xFF00E5FF).copy(alpha = alpha), radius = 3.6f, center = Offset(lx, cy))
                drawCircle(Color.White.copy(alpha = alpha), radius = 1.8f, center = Offset(lx, cy))
            }
        }
        CosmeticUpgradeSlot.RACING_LOGO -> {
            val diamond = Path().apply {
                moveTo(cx, h * 0.18f)
                lineTo(w * 0.82f, cy)
                lineTo(cx, h * 0.82f)
                lineTo(w * 0.18f, cy)
                close()
            }
            drawPath(diamond, p)
            drawPath(diamond, Color.White.copy(alpha = alpha), style = Stroke(width = 2f))
            drawCircle(a, radius = w * 0.12f, center = Offset(cx, cy))
        }
        CosmeticUpgradeSlot.CUSTOM_TRAIL -> {
            val flame = Path().apply {
                moveTo(w * 0.22f, cy - h * 0.22f)
                lineTo(w * 0.84f, cy)
                lineTo(w * 0.22f, cy + h * 0.22f)
                close()
            }
            drawPath(flame, Color(0xFF00E5FF).copy(alpha = alpha))
            val core = Path().apply {
                moveTo(w * 0.24f, cy - h * 0.11f)
                lineTo(w * 0.68f, cy)
                lineTo(w * 0.24f, cy + h * 0.11f)
                close()
            }
            drawPath(core, Color(0xFFE040FB).copy(alpha = alpha))
            drawCircle(Color.White.copy(alpha = alpha), radius = 3.2f, center = Offset(w * 0.34f, cy))
        }
        CosmeticUpgradeSlot.UNDERGLOW_NEON -> {
            val neonColor = if (accent.luminance() < 0.2f) Color(0xFF00E5FF).copy(alpha = alpha) else a
            // Dark floor backing so neon glow pops clearly in both light & dark themes
            drawRoundRect(
                color = Color(0xFF0B111C).copy(alpha = alpha),
                topLeft = Offset(w * 0.08f, h * 0.16f),
                size = Size(w * 0.84f, h * 0.68f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
            )
            // Soft outer neon ground reflection
            drawOval(
                color = neonColor.copy(alpha = 0.42f * alpha),
                topLeft = Offset(w * 0.14f, h * 0.22f),
                size = Size(w * 0.72f, h * 0.56f)
            )
            drawOval(
                color = neonColor,
                topLeft = Offset(w * 0.19f, h * 0.27f),
                size = Size(w * 0.62f, h * 0.46f),
                style = Stroke(width = w * 0.06f)
            )
            // Silhouette car shell over the neon halo
            drawRoundRect(
                color = p,
                topLeft = Offset(w * 0.26f, h * 0.34f),
                size = Size(w * 0.48f, h * 0.32f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
        }
    }
}

@Composable
private fun StatBarRow(label: String, value: Int, color: Color) {
    val animProgress by animateFloatAsState(
        targetValue = (value / 100f).coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = label
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(106.dp)
        )
        LinearProgressIndicator(
            progress = { animProgress },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = color,
            modifier = Modifier.width(28.dp)
        )
    }
}
