package com.example.pelletswaterheater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pelletswaterheater.utils.DependencyParser
import com.example.pelletswaterheater.utils.SoftwareComponent
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.compose.koinInject

@Composable
fun AboutScreen(
    viewModel: TemperatureViewModelNew = koinInject(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val components = DependencyParser.loadSoftwareComponents(context)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Pellets Water Heater App\nVersion 1.0\nDeveloped by Your Team",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Software Components",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(components) { component ->
            SoftwareComponentItem(component = component)
        }
    }
}

@Composable
private fun SoftwareComponentItem(component: SoftwareComponent) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { component.link?.let { uriHandler.openUri(it) } },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = component.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                component.version?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "© ${component.years ?: "Unknown"} ${component.copyrightOwner}",
                fontSize = 12.sp
            )
            Text(
                text = "License: ${component.license}",
                fontSize = 12.sp
            )
            component.link?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(it) }
                )
            }
        }
    }
}
