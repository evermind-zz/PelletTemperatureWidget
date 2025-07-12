package com.example.pelletswaterheater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.ui.action.FileRow
import com.example.pelletswaterheater.ui.action.FileRowAction
import com.example.pelletswaterheater.ui.action.ShareActionWrapper
import com.example.pelletswaterheater.ui.dialog.ConfirmDeleteDialog
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

    val shareActionWrapper = ShareActionWrapper(
        coroutineScope,
        { filename -> logger.getLogUri(File(filename)) },
        context,
        "text/csv",
        "Share Log"
    )

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
                FileRow(
                    filename = file.name,
                    actions = listOf(
                        FileRowAction(
                            "Share",
                            onClick = { shareActionWrapper.doAction(it) },
                            icon = Icons.Default.Share
                        ),
                        FileRowAction(
                            "Delete",
                            onClick = { fileToDeleteState.value = File(it) },
                            icon = Icons.Default.Delete
                        )
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    fileToDeleteState.value?.let { file ->
        ConfirmDeleteDialog(
            item = file,
            itemNameProvider = { it.name },
            onDelete = {
                coroutineScope.launch {
                    logger.deleteLogFile(it)
                    fileToDeleteState.value = null
                }
            },
            onDismiss = { fileToDeleteState.value = null }
        )
    }
}