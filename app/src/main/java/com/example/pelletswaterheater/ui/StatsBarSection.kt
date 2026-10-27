package com.example.pelletswaterheater.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew


@Composable()
fun StatsBarWrapper(
    viewModel: TemperatureViewModelNew,
    modifier: Modifier = Modifier
) {
    val systemLastSuccessTimestamp = viewModel.systemStats.lastTimestamp.collectAsState()
    val systemState = viewModel.systemStats.state.collectAsState()
    val systemError = viewModel.systemStats.error.collectAsState()

    StatsBar(
        systemLastSuccessTimestamp.value,
        systemState.value,
        systemError.value,
        modifier
    )
}

@Composable
fun StatsBar(
    timestamp: String,
    stateText: String,
    errorText: String?,
    modifier: Modifier = Modifier
) {
    var showErrorDialog by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.labelSmall

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
    ) {
        Text(
            text = "⏱ $timestamp",
            style = textStyle.copy(fontFamily = FontFamily.Companion.Monospace),
            modifier = Modifier.Companion.width(90.dp)
        )

        Text(
            text = "⚙️ $stateText",
            style = textStyle,
            modifier = Modifier.Companion.padding(start = 8.dp)
        )

        if (!errorText.isNullOrBlank()) {
            Text(
                text = "⚠️ ${errorText.take(30)}${if (errorText.length > 30) "…" else ""}",
                style = textStyle.copy(color = MaterialTheme.colorScheme.error),
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis,
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(start = 8.dp)
                    .widthIn(max = 120.dp)
                    .clickable { showErrorDialog = true }
            )
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            },
            title = { Text("Error Details") },
            text = { Text(errorText ?: "") }
        )
    }
}