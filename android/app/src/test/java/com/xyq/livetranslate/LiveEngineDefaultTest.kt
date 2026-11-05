package com.xyq.livetranslate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiveEngineDefaultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences
        get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Before
    fun clearSettings() {
        preferences.edit().clear().commit()
    }

    @After
    fun resetSettings() {
        preferences.edit().clear().commit()
    }

    @Test
    fun firstLaunchSelectsContextAwarePromptedEngine() {
        assertEquals(GeminiLiveClient.ENGINE_PROMPTED, SettingsStore.liveEngine(context))
        assertEquals(GeminiLiveClient.PROMPTED_MODEL, GeminiLiveClient.modelForEngine(SettingsStore.liveEngine(context)))
    }

    @Test
    fun existingInstallMigratesToDialogueBlockEngineOnce() {
        preferences.edit().putString("liveTranslationEngine", GeminiLiveClient.ENGINE_PROMPTED).commit()

        assertEquals(GeminiLiveClient.ENGINE_PROMPTED, SettingsStore.liveEngine(context))

        // The user can still deliberately switch back after the one-time migration.
        SettingsStore.saveLiveEngine(context, GeminiLiveClient.ENGINE_PROMPTED)
        assertEquals(GeminiLiveClient.ENGINE_PROMPTED, SettingsStore.liveEngine(context))
    }
}
