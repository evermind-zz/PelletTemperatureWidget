package com.example.pelletswaterheater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DonateComponent(
    val name: String,
    val isCopyable: Boolean,
    val link: String?,
    val address: String?
)

@Composable
fun DonateScreen(
    modifier: Modifier = Modifier
) {
    val donateComponents = listOf(
        DonateComponent(
            name = "PayPal",
            isCopyable = false,
            // Demo placeholder - replace with actual PayPal.me URL for production
            link = "https://paypal.me/demoaccount",
            address = null
        ),
        DonateComponent(
            name = "Bitcoin",
            isCopyable = true,
            link = null,
            address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        ),
        DonateComponent(
            name = "Patreon",
            isCopyable = false,
            link = "https://patreon.com/youraccount",
            address = null
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Support the Pellets Water Heater App",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Your donations help keep this app free and open-source.",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Donation Options",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(donateComponents) { component ->
            DonateComponentItem(component)
        }
    }
}

@Composable
private fun DonateComponentItem(component: DonateComponent) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = component.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (component.isCopyable && component.address != null) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier
                            .clickable {
                                clipboardManager.setText(AnnotatedString(component.address))
                            }
                            .padding(start = 8.dp)
                    )
                }
                if (component.link != null) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Open Link",
                        modifier = Modifier
                            .clickable { uriHandler.openUri(component.link) }
                            .padding(start = 8.dp)
                    )
                }
            }
            component.address?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(component.isCopyable) {
                            clipboardManager.setText(AnnotatedString(it))
                        }
                )
            }
        }
    }
}
