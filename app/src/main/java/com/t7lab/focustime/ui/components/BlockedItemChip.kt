package com.t7lab.focustime.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.res.painterResource
import com.t7lab.focustime.R
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemType

@Composable
fun BlockedItemChip(
    item: BlockedItem,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onRemove != null) {
        InputChip(
            selected = false,
            onClick = {},
            label = {
                Text(
                    text = item.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    painter = when (item.type) {
                        BlockedItemType.APP -> painterResource(R.drawable.ic_app_block)
                        BlockedItemType.URL -> painterResource(R.drawable.ic_web_block)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(InputChipDefaults.IconSize)
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(InputChipDefaults.IconSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            },
            modifier = modifier
        )
    } else {
        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = item.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    painter = when (item.type) {
                        BlockedItemType.APP -> painterResource(R.drawable.ic_app_block)
                        BlockedItemType.URL -> painterResource(R.drawable.ic_web_block)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = modifier
        )
    }
}
