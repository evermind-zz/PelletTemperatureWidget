package com.example.pelletswaterheater.utils

import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Composable
fun CodeEditorDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSaveAs: (String, String) -> Unit // filename passed here
) {
    var editorText by remember { mutableStateOf(initialText) }
    var searchText by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }
    val editTextRef = remember { mutableStateOf<EditText?>(null) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    val searchHelper = SearchHelper()
    val noOfSearchMatches = searchHelper.noOfSearchMatches.collectAsState()
    val currentSearchMatch = searchHelper.currentSearchMatch.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // Search Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            currentMatchIndex = 0
                            searchHelper.highlightAllMatches(editTextRef.value, it)
                        },
                        label = { Text("Search") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(1.dp))
                    Text(
                        text = "${currentSearchMatch.value}/${noOfSearchMatches.value}",
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        searchHelper.jumpToNextMatch(editTextRef.value, searchText, forward = false)
                    }) { Text("<") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        searchHelper.jumpToNextMatch(editTextRef.value, searchText, forward = true)
                    }) { Text(">") }
                }

                Spacer(Modifier.height(8.dp))

                // Editable Area
                AndroidView(
                    factory = { context ->
                        EditText(context).apply {
                            setText(initialText)
                            isSingleLine = false
                            inputType =
                                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            gravity = Gravity.TOP or Gravity.START
                            setBackgroundColor(Color.WHITE)
                            setTextColor(Color.BLACK)
                            setPadding(16, 16, 16, 16)
                            setHorizontallyScrolling(false)
                            setLineSpacing(1.2f, 1.2f)
                            setText(text)
                            //setTextWithLineNumbers(this, text.toString())

                            addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    editorText = s.toString()
                                    //setTextWithLineNumbers(this@apply, s.toString())
                                    searchHelper.highlightAllMatches(this@apply, searchText)
                                }

                                override fun beforeTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    count: Int,
                                    after: Int
                                ) {
                                }

                                override fun onTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    before: Int,
                                    count: Int
                                ) {
                                }
                            })

                            editTextRef.value = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        },
        confirmButton = {
            Row {
                Button(onClick = { onSave(editorText) }) {
                    Text("Save")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showSaveAsDialog = true }) {
                    Text("Save As")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showSaveAsDialog) {
        SaveAsFilenameDialog(
            onConfirm = { filename ->
                onSaveAs(filename, editorText)
                showSaveAsDialog = false
            },
            onDismiss = { showSaveAsDialog = false }
        )
    }
}

class SearchHelper() {

    private val _noOfSearchMatches = MutableStateFlow<Int>(0)
    val noOfSearchMatches: StateFlow<Int> = _noOfSearchMatches.asStateFlow()

    private val _currentSearchMatch = MutableStateFlow<Int>(0)
    val currentSearchMatch: StateFlow<Int> = _currentSearchMatch.asStateFlow()

    fun highlightAllMatches(editText: EditText?, query: String) {
        if (editText == null || query.isEmpty()) return

        val spannable = editText.text as Spannable
        spannable.removeSpans<BackgroundColorSpan>()

        val text = spannable.toString()
        var index = text.indexOf(query, ignoreCase = true)

        var count = 0
        while (index >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(Color.YELLOW),
                index,
                index + query.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = text.indexOf(query, startIndex = index + query.length, ignoreCase = true)
            count++;
        }

        _noOfSearchMatches.value = count
    }

    fun jumpToNextMatch(editText: EditText?, query: String, forward: Boolean) {
        if (editText == null || query.isEmpty()) return

        val text = editText.text.toString()
        val cursor = editText.selectionStart

        val nextIndex = if (forward) {
            _currentSearchMatch.update {
                if (_noOfSearchMatches.value >= (it + 1)) {
                    it + 1
                } else {
                    it
                }
            }
            text.indexOf(query, startIndex = cursor + 1, ignoreCase = true)
        } else {
            _currentSearchMatch.update {
                if ((it - 1 >= 0)) {
                    it - 1
                } else {
                    it
                }
            }
            text.lastIndexOf(query, startIndex = cursor - 1, ignoreCase = true)
        }

        if (nextIndex >= 0) {
            editText.setSelection(nextIndex, nextIndex + query.length)
        }
    }

    inline fun <reified T> Spannable.removeSpans() {
        getSpans(0, length, T::class.java).forEach { removeSpan(it) }
    }

    fun setTextWithLineNumbers(editText: EditText, content: String) {
        val lines = content.split("\n")
        val spannable = SpannableStringBuilder()

        lines.forEachIndexed { index, line ->
            val lineNumber = "${index + 1}".padStart(3, ' ') + " | "
            spannable.append(
                lineNumber,
                ForegroundColorSpan(Color.GRAY),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.append(line + "\n")
        }

        editText.setText(spannable)
        editText.setSelection(editText.text.length)
    }

}

@Composable
fun SaveAsFilenameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var filename by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save As") },
        text = {
            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it },
                label = { Text("Enter filename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (filename.isNotBlank()) {
                    onConfirm(filename)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
