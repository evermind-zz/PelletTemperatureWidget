package com.example.pelletswaterheater

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupTest {

    @Test
    fun appLaunchesWithoutCrash() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Verify the activity is not null and is the expected type
                assert(activity is MainActivity)
            }
        }
    }

    @Test
    fun appHasCorrectPackageName() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        assert(context.packageName == "com.example.pelletswaterheater.new")
    }
}