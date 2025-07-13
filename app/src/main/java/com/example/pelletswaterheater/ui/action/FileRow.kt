package com.example.pelletswaterheater.ui.action

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class FileRowAction(
    val label: String,
    val onClick: (filename: String) -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true
)

@Composable
fun FileRow(
    filename: String,
    actions: List<FileRowAction>,
    modifier: Modifier = Modifier,
    isDefault: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val backgroundColor = if (isDefault) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        if (isDefault) {
            DefaultLabel()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = filename,
                modifier = Modifier.weight(1f)
            )

            // Wrap IconButton in a Box aligned to the end to have the
            // DropDownMenu also align to the End aka right side
            Box(
                modifier = Modifier.align(Alignment.Top),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(max = 220.dp)
                ) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            onClick = {
                                expanded = false
                                if (action.enabled) action.onClick(filename)
                            },
                            enabled = action.enabled,
                            text = { Text(action.label) },
                            leadingIcon = action.icon?.let {
                                { Icon(imageVector = it, contentDescription = null) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultLabel() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Default",
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "Default",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

