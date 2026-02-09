package com.t7lab.focustime.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.painterResource
import com.t7lab.focustime.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.ui.components.BlockedItemChip
import com.t7lab.focustime.ui.components.DurationPicker
import com.t7lab.focustime.ui.components.RotatingQuoteCard
import com.t7lab.focustime.ui.components.SessionCompleteOverlay
import com.t7lab.focustime.ui.theme.TimerTypography
import com.t7lab.focustime.util.formatDuration

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptic = LocalHapticFeedback.current

    var showPasswordDialog by remember { mutableStateOf(false) }

    // Session completion celebration overlay
    if (uiState.showSessionComplete) {
        SessionCompleteOverlay(
            completedDurationMs = uiState.completedDurationMs,
            onDismiss = { viewModel.dismissSessionComplete() }
        )
        return
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(if (uiState.isSessionActive) "Focus Active" else "FocusTime")
                },
                actions = {
                    if (!uiState.isSessionActive) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        val scrollState = rememberScrollState()
        var contentHeight by remember { mutableIntStateOf(0) }
        var containerHeight by remember { mutableIntStateOf(0) }
        val needsScroll = contentHeight > containerHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { containerHeight = it.height }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (needsScroll) Modifier.verticalScroll(scrollState)
                        else Modifier
                    )
                    .padding(horizontal = 16.dp)
                    .onSizeChanged { contentHeight = it.height },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isSessionActive) {
                    ActiveSessionContent(
                        remainingTimeMs = uiState.remainingTimeMs,
                        endTime = uiState.activeSession?.endTime ?: 0L,
                        durationMs = uiState.activeSession?.durationMs ?: 1L,
                        blockedItems = uiState.blockedItems,
                        onUnlockClick = { showPasswordDialog = true }
                    )
                } else {
                    NewSessionContent(
                        blockedItems = uiState.blockedItems,
                        selectedDurationMs = uiState.selectedDurationMs,
                        onDurationSelected = viewModel::selectDuration,
                        onAddApps = onNavigateToAppPicker,
                        onAddUrls = onNavigateToUrlManager,
                        onRemoveItem = viewModel::removeItem,
                        onStartFocus = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startSession(onVpnPermissionNeeded)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionContent(
    remainingTimeMs: Long,
    endTime: Long,
    durationMs: Long,
    blockedItems: List<BlockedItem>,
    onUnlockClick: () -> Unit
) {
    val progress = if (durationMs > 0) {
        ((endTime - System.currentTimeMillis()).toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progress"
    )

    val appCount = blockedItems.count { it.type == BlockedItemType.APP }
    val urlCount = blockedItems.count { it.type == BlockedItemType.URL }

    // Immersive timer card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_focus_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center) {
                // Progress shows elapsed time (fills up as time passes)
                CircularProgressIndicator(
                    progress = { 1f - animatedProgress },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.primary,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDuration(remainingTimeMs),
                        style = TimerTypography,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val summary = buildString {
                if (appCount > 0) append("$appCount app${if (appCount > 1) "s" else ""}")
                if (appCount > 0 && urlCount > 0) append(", ")
                if (urlCount > 0) append("$urlCount URL${if (urlCount > 1) "s" else ""}")
                append(" blocked")
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }

    // Motivational quote
    RotatingQuoteCard()

    // Unlock button — subtle, pushed down
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(
        onClick = onUnlockClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text("Unlock with password")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewSessionContent(
    blockedItems: List<BlockedItem>,
    selectedDurationMs: Long?,
    onDurationSelected: (Long) -> Unit,
    onAddApps: () -> Unit,
    onAddUrls: () -> Unit,
    onRemoveItem: (BlockedItem) -> Unit,
    onStartFocus: () -> Unit
) {
    val apps = blockedItems.filter { it.type == BlockedItemType.APP }
    val urls = blockedItems.filter { it.type == BlockedItemType.URL }
    val isReady = blockedItems.isNotEmpty() && selectedDurationMs != null

    // Hero Start Focus button with pulse animation
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
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(pulseScale),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isReady) 6.dp else 0.dp
        )
    ) {
        Icon(
            if (isReady) Icons.Default.PlayArrow else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = if (isReady) "Start Focus" else "Add items to start",
            style = MaterialTheme.typography.titleMedium
        )
    }

    // Duration picker — inline
    DurationPicker(
        selectedDurationMs = selectedDurationMs,
        onDurationSelected = onDurationSelected
    )

    // Apps section
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (apps.isNotEmpty()) "Apps (${apps.size})" else "Apps",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(onClick = onAddApps) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Add")
                }
            }
            if (apps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    apps.forEach { item ->
                        BlockedItemChip(item = item, onRemove = { onRemoveItem(item) })
                    }
                }
            } else {
                Text(
                    text = "Tap Add to choose apps to block",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    // URLs section
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (urls.isNotEmpty()) "URLs (${urls.size})" else "URLs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(onClick = onAddUrls) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Add")
                }
            }
            if (urls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    urls.forEach { item ->
                        BlockedItemChip(item = item, onRemove = { onRemoveItem(item) })
                    }
                }
            } else {
                Text(
                    text = "Tap Add to choose URLs to block",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
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
        icon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
        title = { Text("Unlock Session") },
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
                    isError = result == PasswordUnlockResult.WRONG_PASSWORD,
                    supportingText = {
                        when (result) {
                            PasswordUnlockResult.WRONG_PASSWORD -> Text("Incorrect password")
                            PasswordUnlockResult.NO_PASSWORD_SET -> Text("No master password has been set")
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
