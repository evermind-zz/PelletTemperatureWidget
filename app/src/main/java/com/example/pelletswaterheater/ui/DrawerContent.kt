package com.example.pelletswaterheater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable for the navigation drawer content in the Pellets Water Heater app.
 * Displays menu items for navigating to DataScreen, ValueSelectionScreen, LogsScreen, and SettingsScreen.
 *
 * @param onItemClick Callback invoked when a menu item is clicked, passing the route to navigate to.
 */
@Composable
fun DrawerContent(
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(16.dp))
        DrawerItem(
            label = "Data",
            route = "data",
            onClick = onItemClick
        )
        DrawerItem(
            label = "Value Selection",
            route = "selection",
            onClick = onItemClick
        )
        DrawerItem(
            label = "Logs",
            route = "logs",
            onClick = onItemClick
        )
        DrawerItem(
            label = "Settings",
            route = "settings",
            onClick = onItemClick
        )
        DrawerItem(
            label = "About",
            route = "about",
            onClick = onItemClick
        )
        DrawerItem(
            label = "Donate",
            route = "donate",
            onClick = onItemClick
        )
    }
}

/**
 * Composable for a single drawer item.
 *
 * @param label The text to display for the item.
 * @param route The navigation route to pass to onClick.
 * @param onClick Callback invoked when the item is clicked, passing the route.
 */
@Composable
private fun DrawerItem(
    label: String,
    route: String,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(route) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
