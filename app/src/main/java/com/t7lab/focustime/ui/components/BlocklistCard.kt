package com.t7lab.focustime.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.R
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlocklistCard(
    apps: List<BlockedItem>,
    urls: List<BlockedItem>,
    onAddApp: () -> Unit,
    onAddUrl: () -> Unit,
    onRemove: (BlockedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val isEmpty = apps.isEmpty() && urls.isEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Block during focus",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isEmpty) {
                // Empty state with illustration
                EmptyBlocklistContent(
                    onAddApp = onAddApp,
                    onAddUrl = onAddUrl
                )
            } else {
                // Tabbed content
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                if (apps.isNotEmpty()) "Apps (${apps.size})" else "Apps"
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                if (urls.isNotEmpty()) "URLs (${urls.size})" else "URLs"
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val currentItems = if (selectedTab == 0) apps else urls
                val onAdd = if (selectedTab == 0) onAddApp else onAddUrl

                if (currentItems.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentItems.forEach { item ->
                            BlockedItemChip(
                                item = item,
                                onRemove = { onRemove(item) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (selectedTab == 0) Icons.Default.Add else Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (selectedTab == 0) "Add Apps" else "Add URLs")
                }
            }
        }
    }
}

@Composable
private fun EmptyBlocklistContent(
    onAddApp: () -> Unit,
    onAddUrl: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_empty_focus),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ready to focus?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Add the apps and sites that distract you most",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        FilledTonalButton(
            onClick = onAddApp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Add distracting apps")
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onAddUrl,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Add distracting sites")
        }
    }
}
