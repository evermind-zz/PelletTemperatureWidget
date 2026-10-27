package com.example.pelletswaterheater.di

import com.example.pelletswaterheater.NetworkAccess
import com.example.pelletswaterheater.SettingsViewModel
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.AndroidContextProvider
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.NetworkFingerprint
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureLoggerCsvImpl
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Koin module for dependency injection in the PelletsWaterHeater app.
 */
val appModule = module {
    // Provide application Context
    //single<Context> { androidContext() }

    // Provide ContextProvider
    single<ContextProvider> { AndroidContextProvider(get()) }

    // Provide ILogger
    single<ILogger> { Logger }

    // Provide OkHttpClient
    single { OkHttpClient() }
    single { NetworkAccess() }

    single { NetworkFingerprint }
    // Provide TemperatureLogger
    single<TemperatureLogger> { TemperatureLoggerCsvImpl(get(), get()) }

    //single<SharedPreferences> {
    //    get<Context>().getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
    //}

    // Provide TemperatureViewModelNew
    single { TemperatureViewModelNew(get(), get(), get(), get()) }

    single { SettingsViewModel(get(), get(), get(), get(), get()) }
}
