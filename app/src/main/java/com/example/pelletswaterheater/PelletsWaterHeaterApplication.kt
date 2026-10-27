package com.example.pelletswaterheater

import android.app.Application
import com.example.pelletswaterheater.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application class to initialize Koin dependency injection.
 */
class PelletsWaterHeaterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TemperatureUtils.appContext = requireNotNull(applicationContext)
        startKoin {
            androidContext(this@PelletsWaterHeaterApplication)
            modules(appModule)
        }
    }
}