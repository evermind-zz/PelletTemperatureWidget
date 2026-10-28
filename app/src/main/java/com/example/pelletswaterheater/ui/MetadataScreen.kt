package com.example.pelletswaterheater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.SettingsViewModel
import com.example.pelletswaterheater.ui.action.FileRow
import com.example.pelletswaterheater.ui.action.FileRowAction
import com.example.pelletswaterheater.ui.action.ShareActionWrapper
import com.example.pelletswaterheater.ui.dialog.CodeEditorDialog
import com.example.pelletswaterheater.ui.dialog.ConfirmDeleteDialog
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

@Composable
fun MetadataScreen(
    viewModel: TemperatureViewModelNew = koinInject(),
    settingsViewModel: SettingsViewModel = koinInject()
) {
    val metadataFiles = viewModel.metadataFiles.collectAsState(initial = emptyList())
    val currentMetadataFile = viewModel.getMetadataFileName() //.collectedAsState()
    //settingsViewModel
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fileToDeleteState = remember { mutableStateOf<File?>(null) }
    val fileToEditState = remember { mutableStateOf<File?>(null) }
    val launcher = createManagedLauncher(context, viewModel)

    val shareActionWrapper = ShareActionWrapper(
        coroutineScope,
        { filename ->
            viewModel.getMetadataUri(File(filename))
        },
        context,
        "text/json",
        "Share Metadata"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { viewModel.resetMetadata() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)

            ) {
                Text("Reset to Default")
            }
            Button(
                onClick = {
                    launcher.launch("application/json")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text("Import New")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(metadataFiles.value) { file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    FileRow(
                        filename = file.name,
                        actions = listOf(
                            FileRowAction(
                                "Mark as Default",
                                onClick = { viewModel.setCurrentMetadata(it) },
                                icon = Icons.Default.Check
                            ),
                            FileRowAction(
                                "Share",
                                onClick = { shareActionWrapper.doAction(it) },
                                icon = Icons.Default.Share
                            ),
                            FileRowAction(
                                "Edit",
                                onClick = { fileToEditState.value = File(it) },
                                icon = Icons.Default.Edit
                            ),
                            FileRowAction(
                                "Copy",
                                onClick = { fileToEditState.value = File(it) },
                                icon = Icons.Default.FileCopy
                            ),
                            FileRowAction(
                                "Rename",
                                onClick = { fileToEditState.value = File(it) },
                                icon = Icons.Default.DriveFileRenameOutline
                            ),
                            FileRowAction(
                                "Delete",
                                onClick = { fileToDeleteState.value = File(it) },
                                icon = Icons.Default.Delete
                            ),
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
                        viewModel.deleteMetadataFile(it)
                        fileToDeleteState.value = null
                    }
                },
                onDismiss = { fileToDeleteState.value = null }
            )
        }

        fileToEditState.value?.let { file ->
            CodeEditorDialog(
                "Edit Metadata",
                initialText = viewModel.getCurrentMetadataJson() ?: "",
                onDismiss = {
                    fileToEditState.value = null
                },
                onSave = { saveText ->
                    fileToEditState.value = null
                    viewModel.saveUserMetadata(saveText)
                },
                onSaveAs = { filename, saveText ->
                    viewModel.setCurrentMetadata(filename)
                    viewModel.saveUserMetadata(saveText)
                    fileToEditState.value = null
                }
            )
        }
    }
}
