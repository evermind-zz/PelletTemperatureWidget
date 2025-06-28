package com.example.pelletswaterheater.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.utils.TemperatureLogger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

@Composable
fun LogsScreen(logger: TemperatureLogger = koinInject()) {
    val logFiles = logger.getLogFiles().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fileToDeleteState = remember { mutableStateOf<File?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logFiles.value) { file ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            coroutineScope.launch {
                                val uri = logger.getLogUri(file)
                                uri?.let {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, it)
                                        type = "text/csv"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            "Share Log"
                                        )
                                    )
                                }
                            }
                        }) {
                            Text("Share")
                        }

                        Button(onClick = {
                            fileToDeleteState.value = file // <- Open confirmation dialog
                        }) {
                            Text("Delete")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    // Compose AlertDialog shown when fileToDelete is not null
    if (fileToDeleteState.value != null) {
        AlertDialog(
            onDismissRequest = { fileToDeleteState.value = null },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete '${fileToDeleteState.value?.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        fileToDeleteState.value?.let { logger.deleteLogFile(it) }
                        fileToDeleteState.value = null
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDeleteState.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
