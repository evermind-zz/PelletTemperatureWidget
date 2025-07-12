package com.example.pelletswaterheater.ui.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class ShareActionWrapper(
    val coroutineScope: CoroutineScope,
    val getFileUri: (String) -> Uri?,
    val context: Context,
    val mimeType: String = "*/*",
    val shareTitle: String = "Share"
) : IActionWrapper<String> {

    override fun doAction(filename: String) {
        coroutineScope.launch {
            val uri = getFileUri(filename)
            uri?.let {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, it)
                    type = mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        shareTitle
                    )
                )
            }
        }
    }
}