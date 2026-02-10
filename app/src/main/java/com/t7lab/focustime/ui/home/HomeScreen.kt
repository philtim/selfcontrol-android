package com.t7lab.focustime.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.t7lab.focustime.R
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.ui.components.BlocklistCard
import com.t7lab.focustime.ui.components.DurationBottomSheet
import com.t7lab.focustime.ui.components.FocusTimerRing
import com.t7lab.focustime.ui.components.RotatingQuoteCard
import com.t7lab.focustime.ui.components.SessionCompleteOverlay
import com.t7lab.focustime.ui.theme.FocusTimeTheme
import com.t7lab.focustime.ui.theme.LocalSessionColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onNavigateToAppPicker: () -> Unit,
    onNavigateToUrlManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onVpnPermissionNeeded: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDurationSheet by remember { mutableStateOf(false) }

    // Session completion celebration overlay
    if (uiState.showSessionComplete) {
        SessionCompleteOverlay(
            completedDurationMs = uiState.completedDurationMs,
            onDismiss = { viewModel.dismissSessionComplete() }
        )
        return
    }

    AnimatedContent(
        targetState = uiState.isSessionActive,
        transitionSpec = {
            (fadeIn(tween(800)) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(800)
            )) togetherWith (fadeOut(tween(400)) + scaleOut(
                targetScale = 1.05f,
                animationSpec = tween(400)
            ))
        },
        label = "session_transition"
    ) { isActive ->
        if (isActive) {
            FocusTimeTheme(isSessionActive = true) {
                ActiveSessionScreen(
                    remainingTimeMs = uiState.remainingTimeMs,
                    endTime = uiState.activeSession?.endTime ?: 0L,
                    durationMs = uiState.activeSession?.durationMs ?: 1L,
                    onUnlockClick = { showPasswordDialog = true }
                )
            }
        } else {
            SetupScreen(
                uiState = uiState,
                onNavigateToAppPicker = onNavigateToAppPicker,
                onNavigateToUrlManager = onNavigateToUrlManager,
                onNavigateToSettings = onNavigateToSettings,
                onStartFocus = { showDurationSheet = true },
                onRemoveItem = viewModel::removeItem
            )
        }
    }

    // Duration bottom sheet
    if (showDurationSheet) {
        DurationBottomSheet(
            lastSelectedDurationMs = uiState.selectedDurationMs,
            itemCount = uiState.blockedItems.size,
            onStartFocus = { duration ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.selectDuration(duration)
                viewModel.startSession(onVpnPermissionNeeded)
                showDurationSheet = false
            },
            onDismiss = { showDurationSheet = false }
        )
    }

    if (showPasswordDialog) {
        PasswordUnlockDialog(
            result = uiState.passwordUnlockResult,
            onDismiss = {
                showPasswordDialog = false
                viewModel.clearPasswordResult()
            },
            onSubmit = { password ->
                viewModel.tryUnlockWithPassword(password)
            }
        )
    }
}

@Composable
private fun ActiveSessionScreen(
    remainingTimeMs: Long,
    endTime: Long,
    durationMs: Long,
    onUnlockClick: () -> Unit
) {
    val sessionColors = LocalSessionColors.current
    val haptic = LocalHapticFeedback.current

    // Ambient glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius"
    )

    // Haptic tick at minute boundaries
    var lastMinute by remember { mutableStateOf(-1L) }
    val currentMinute = remainingTimeMs / 60_000
    LaunchedEffect(currentMinute) {
        if (lastMinute >= 0 && currentMinute != lastMinute) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastMinute = currentMinute
    }

    // Unlock trigger state
    var showUnlockSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Ambient radial gradient glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.42f)
            val radius = size.width * glowRadius
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        sessionColors.ambientGlow.copy(alpha = 0.3f),
                        sessionColors.ambientGlow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )
        }

        // Main content centered
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Shield icon
            Icon(
                painter = painterResource(R.drawable.ic_focus_shield),
                contentDescription = null,
                tint = sessionColors.timerRingFill.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Timer ring — the hero element
            FocusTimerRing(
                remainingTimeMs = remainingTimeMs,
                endTimeMs = endTime,
                durationMs = durationMs,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Motivational quote (no card wrapper)
            RotatingQuoteCard(
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        // Nearly invisible unlock trigger at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showUnlockSheet = true }
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Emergency unlock",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }

    // Unlock confirmation bottom sheet
    if (showUnlockSheet) {
        UnlockConfirmationSheet(
            onConfirm = {
                showUnlockSheet = false
                onUnlockClick()
            },
            onDismiss = { showUnlockSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Break focus session?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Breaking focus early reduces effectiveness. Are you sure you want to unlock?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Keep focusing")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Emergency override",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    uiState: HomeUiState,
    onNavigateToAppPicker: () -> Unit,
    onNavigateToUrlManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartFocus: () -> Unit,
    onRemoveItem: (BlockedItem) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isReady = uiState.blockedItems.isNotEmpty()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("FocusTime") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NewSessionContent(
                    blockedItems = uiState.blockedItems,
                    onAddApps = onNavigateToAppPicker,
                    onAddUrls = onNavigateToUrlManager,
                    onRemoveItem = onRemoveItem
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            StartFocusButton(
                itemCount = uiState.blockedItems.size,
                isReady = isReady,
                onStartFocus = onStartFocus,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewSessionContent(
    blockedItems: List<BlockedItem>,
    onAddApps: () -> Unit,
    onAddUrls: () -> Unit,
    onRemoveItem: (BlockedItem) -> Unit
) {
    val apps = blockedItems.filter { it.type == BlockedItemType.APP }
    val urls = blockedItems.filter { it.type == BlockedItemType.URL }

    BlocklistCard(
        apps = apps,
        urls = urls,
        onAddApp = onAddApps,
        onAddUrl = onAddUrls,
        onRemove = onRemoveItem
    )
}

@Composable
private fun StartFocusButton(
    itemCount: Int,
    isReady: Boolean,
    onStartFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isReady) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Button(
        onClick = onStartFocus,
        enabled = isReady,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(pulseScale),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isReady) 6.dp else 0.dp
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isReady) Icons.Default.PlayArrow else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Focus",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (isReady) {
                Text(
                    text = "$itemCount items to block",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PasswordUnlockDialog(
    result: PasswordUnlockResult?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency Override") },
        text = {
            Column {
                Text(
                    text = "Enter the master password to end this focus session early.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = result == PasswordUnlockResult.WRONG_PASSWORD ||
                            result == PasswordUnlockResult.LOCKED_OUT,
                    supportingText = {
                        when (result) {
                            PasswordUnlockResult.WRONG_PASSWORD -> Text("Incorrect password")
                            PasswordUnlockResult.NO_PASSWORD_SET -> Text("No master password has been set")
                            PasswordUnlockResult.LOCKED_OUT -> Text("Too many attempts. Try again later.")
                            else -> {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
