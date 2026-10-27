package com.example.pelletswaterheater.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

data class FileRowActione(
    val label: String,
    val onClick: @Composable (filename: String) -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true
)

//@Composable
//fun FileRowe(
//    filename: String,
//    actions: List<FileRowActione>
//) {
//    var expanded by remember { mutableStateOf(false) }
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(8.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Text(
//            text = filename,
//            modifier = Modifier.weight(1f)
//        )
//
//        IconButton(onClick = { expanded = true }) {
//            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
//        }
//
//        DropdownMenu(
//            expanded = expanded,
//            onDismissRequest = { expanded = false }
//        ) {
//            actions.forEach { action ->
//                DropdownMenuItem(
//                    onClick = {
//                        expanded = false
//                        if (action.enabled) action.onClick(filename)
//                    },
//                    enabled = action.enabled,
//                    text = { Text(action.label) },
//                    leadingIcon = action.icon?.let {
//                        { Icon(imageVector = it, contentDescription = null) }
//                    }
//                )
//            }
//        }
//    }
//}
