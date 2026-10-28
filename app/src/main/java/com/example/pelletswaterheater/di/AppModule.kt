package com.example.pelletswaterheater.di

import com.example.pelletswaterheater.NetworkAccess
import com.example.pelletswaterheater.SettingsViewModel
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.preferences.PreferencesRepositoryImpl
import com.example.pelletswaterheater.repository.LoggingRepository
import com.example.pelletswaterheater.repository.LoggingRepositoryImpl
import com.example.pelletswaterheater.repository.MetadataRepository
import com.example.pelletswaterheater.repository.MetadataRepositoryImpl
import com.example.pelletswaterheater.repository.NetworkRepository
import com.example.pelletswaterheater.repository.NetworkRepositoryImpl
import com.example.pelletswaterheater.repository.SelectionRepository
import com.example.pelletswaterheater.repository.SelectionRepositoryImpl
import com.example.pelletswaterheater.repository.StateRepository
import com.example.pelletswaterheater.repository.StateRepositoryImpl
import com.example.pelletswaterheater.repository.ValueRepository
import com.example.pelletswaterheater.repository.ValueRepositoryImpl
import com.example.pelletswaterheater.utils.AndroidContextProvider
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.NetworkFingerprint
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureLoggerCsvImpl
import com.example.pelletswaterheater.utils.TemperatureUtils
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Koin module for dependency injection in the PelletsWaterHeater app.
 */
val appModule = module {
    // Provide ContextProvider
    single<ContextProvider> { AndroidContextProvider(get()) }

    // Provide ILogger
    single<ILogger> { Logger }

    // Provide OkHttpClient
    single { OkHttpClient() }
    single { NetworkAccess() }

    // Provide TemperatureLogger
    single<TemperatureLogger> { TemperatureLoggerCsvImpl(get(), get()) }

    // Provide PreferencesRepository (DataStore)
    single<PreferencesRepository> { PreferencesRepositoryImpl(get()) }

    // Provide TemperatureUtils
    single { TemperatureUtils(get(), get<ContextProvider>().getContext()) }

    // Provide Repositories
    single<MetadataRepository> { MetadataRepositoryImpl(get(), get(), get(), get(), get()) }
    single<ValueRepository> { ValueRepositoryImpl(get(), get(), get(), get(), get()) }
    single<SelectionRepository> { SelectionRepositoryImpl(get(), get(), get()) }
    single<LoggingRepository> { LoggingRepositoryImpl(get(), get()) }
    single<NetworkRepository> { NetworkRepositoryImpl(get()) }
    single<StateRepository> { StateRepositoryImpl(get()) }

    // Provide TemperatureViewModelNew
    single { TemperatureViewModelNew(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Provide SettingsViewModel
    single { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
}