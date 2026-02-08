package com.t7lab.focustime.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.ui.components.BlockedItemChip
import com.t7lab.focustime.ui.components.DurationPicker
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

    var showPasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        if (uiState.isSessionActive) "Focus Active" else "FocusTime"
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
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
                    onStartFocus = { viewModel.startSession(onVpnPermissionNeeded) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
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
    blockedItems: List<com.t7lab.focustime.data.db.BlockedItem>,
    onUnlockClick: () -> Unit
) {
    val progress = if (durationMs > 0) {
        ((endTime - System.currentTimeMillis()).toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progress"
    )

    // Timer card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(180.dp),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Time Remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = formatDuration(remainingTimeMs),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    // Blocked items
    if (blockedItems.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Blocked Items",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    blockedItems.forEach { item ->
                        BlockedItemChip(item = item)
                    }
                }
            }
        }
    }

    // Unlock button
    OutlinedButton(
        onClick = onUnlockClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text("Unlock with Password")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewSessionContent(
    blockedItems: List<com.t7lab.focustime.data.db.BlockedItem>,
    selectedDurationMs: Long?,
    onDurationSelected: (Long) -> Unit,
    onAddApps: () -> Unit,
    onAddUrls: () -> Unit,
    onRemoveItem: (com.t7lab.focustime.data.db.BlockedItem) -> Unit,
    onStartFocus: () -> Unit
) {
    val apps = blockedItems.filter { it.type == BlockedItemType.APP }
    val urls = blockedItems.filter { it.type == BlockedItemType.URL }

    // Apps section
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blocked Apps",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onAddApps) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Add Apps")
                }
            }

            if (apps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    apps.forEach { item ->
                        BlockedItemChip(
                            item = item,
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }
            } else {
                Text(
                    text = "No apps added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    // URLs section
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blocked URLs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onAddUrls) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Add URLs")
                }
            }

            if (urls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    urls.forEach { item ->
                        BlockedItemChip(
                            item = item,
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }
            } else {
                Text(
                    text = "No URLs added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    // Duration picker
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DurationPicker(
                selectedDurationMs = selectedDurationMs,
                onDurationSelected = onDurationSelected
            )
        }
    }

    // Start button
    Button(
        onClick = onStartFocus,
        enabled = blockedItems.isNotEmpty() && selectedDurationMs != null,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = "Start Focus",
            style = MaterialTheme.typography.titleMedium
        )
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
