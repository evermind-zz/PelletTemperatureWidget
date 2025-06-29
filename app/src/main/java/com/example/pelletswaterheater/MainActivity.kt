package com.example.pelletswaterheater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pelletswaterheater.ui.AboutScreen
import com.example.pelletswaterheater.ui.DataScreen
import com.example.pelletswaterheater.ui.DonateScreen
import com.example.pelletswaterheater.ui.DrawerContent
import com.example.pelletswaterheater.ui.LogsScreen
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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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
        Scaffold { padding ->
            NavHost(
                navController = navController,
                startDestination = "data",
                modifier = Modifier.padding(padding)
            ) {
                composable("data") {  DataScreen() }
                composable("selection") { ValueSelectionScreen() }
                composable("logs") { LogsScreen() }
                composable("settings") { SettingsScreen() }
                composable("about") { AboutScreen() }
                composable("donate") { DonateScreen() }
            }
        }
    }
}