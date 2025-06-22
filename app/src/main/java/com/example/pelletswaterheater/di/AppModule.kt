package com.example.pelletswaterheater.di

import android.content.Context
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.AndroidContextProvider
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureLoggerCsvImpl
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for dependency injection in the PelletsWaterHeater app.
 */
val appModule = module {
    // Provide application Context
    single<Context> { androidContext() }

    // Provide ContextProvider
    single<ContextProvider> { AndroidContextProvider(get()) }

    // Provide ILogger
    single<ILogger> { Logger }

    // Provide OkHttpClient
    single { OkHttpClient() }

    // Provide TemperatureLogger
    single<TemperatureLogger> { TemperatureLoggerCsvImpl(get(), get()) }

    // Provide TemperatureViewModelNew
    viewModel { TemperatureViewModelNew(get(), get(), get(), get()) }
}