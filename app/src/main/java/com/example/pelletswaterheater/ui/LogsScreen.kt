package com.example.pelletswaterheater.ui

import android.content.Intent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.pelletswaterheater.utils.TemperatureLogger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun LogsScreen(logger: TemperatureLogger = koinInject()) {
    val logFiles = logger.getLogFiles().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    LazyColumn {
        items(logFiles.value) { file ->
            Text(text = file.name)
            Button(onClick = {
                coroutineScope.launch {
                    val uri = logger.getLogUri(file)
                    uri?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_STREAM, it)
                            type = "text/csv"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        Intent.createChooser(shareIntent, "Share Log").run {
                            // Assuming context is available; adjust if needed
                            // context.startActivity(this)
                        }
                    }
                }
            }) {
                Text("Share")
            }
            Button(onClick = {
                coroutineScope.launch {
                    logger.deleteLogFile(file)
                }
            }) {
                Text("Delete")
            }
        }
    }
}