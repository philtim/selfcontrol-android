package com.t7lab.focustime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.R
import com.t7lab.focustime.util.DURATION_OPTIONS
import com.t7lab.focustime.util.formatDurationShort
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DurationBottomSheet(
    lastSelectedDurationMs: Long?,
    itemCount: Int,
    onStartFocus: (durationMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedDuration by remember { mutableStateOf(lastSelectedDurationMs) }
    var showCustomDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.how_long),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DURATION_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = selectedDuration == option.durationMs,
                        onClick = { selectedDuration = option.durationMs },
                        label = { Text(stringResource(option.labelRes)) }
                    )
                }

                val isCustomSelected = selectedDuration != null &&
                        DURATION_OPTIONS.none { it.durationMs == selectedDuration }

                FilterChip(
                    selected = isCustomSelected,
                    onClick = { showCustomDialog = true },
                    label = {
                        Text(
                            if (isCustomSelected) {
                                formatCustomDuration(selectedDuration!!, context)
                            } else {
                                stringResource(R.string.custom)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { selectedDuration?.let { onStartFocus(it) } },
                enabled = selectedDuration != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.start_focus),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selectedDuration != null) {
                            Text(
                                text = stringResource(R.string.items_summary, itemCount, formatDurationShort(selectedDuration!!, context)),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomDurationDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { hours, minutes ->
                val ms = TimeUnit.HOURS.toMillis(hours.toLong()) +
                        TimeUnit.MINUTES.toMillis(minutes.toLong())
                if (ms > 0) {
                    selectedDuration = ms
                }
                showCustomDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (hours: Int, minutes: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = 1,
        initialMinute = 0,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_custom_duration)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.hours_minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text(stringResource(R.string.set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatCustomDuration(ms: Long, context: android.content.Context): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return formatDurationShort(ms, context)
}
