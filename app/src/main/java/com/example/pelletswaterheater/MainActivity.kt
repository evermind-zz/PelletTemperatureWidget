package com.example.pelletswaterheater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pelletswaterheater.ui.AboutScreen
import com.example.pelletswaterheater.ui.DataScreen
import com.example.pelletswaterheater.ui.DonateScreen
import com.example.pelletswaterheater.ui.DrawerContent
import com.example.pelletswaterheater.ui.LogsScreen
import com.example.pelletswaterheater.ui.MetadataScreen
import com.example.pelletswaterheater.ui.SettingsScreen
import com.example.pelletswaterheater.ui.ValueSelectionScreen
import com.example.pelletswaterheater.ui.theme.PelletsWaterHeaterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TemperatureUtils.forceTemperatureUpdate()
        TemperatureUtils.schedulePeriodicTemperatureUpdate()
        CoroutineScope(Dispatchers.Main).launch {
            TemperatureWidget().forceUpdateAll(this@MainActivity)
        }

        setContent {
            PelletsWaterHeaterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: "data"
    val context = LocalContext.current
    val activity = context as? MainActivity

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentRoute) {
        //activity?.setTitle(currentRoute)
    }

    ModalNavigationDrawer(
        drawerContent = {
            DrawerContent(
                onItemClick = { route ->
                    navController.navigate(route)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        },
        drawerState = drawerState
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(currentRoute.capitalize()) },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }

        )
        { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "metadata",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("data") { DataScreen(snackbarHostState) }
                composable("selection") { ValueSelectionScreen() }
                composable("logs") { LogsScreen() }
                composable("settings") { SettingsScreen() }
                composable("metadata") { MetadataScreen() }
                composable("about") { AboutScreen() }
                composable("donate") { DonateScreen() }
            }
        }
    }
}