package com.xyq.livetranslate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiLiveStreamingConfigTest {
    @Test
    fun liveTranslateDoesNotInterruptGenerationOnIncomingSpeech() {
        val root = JSONObject(GeminiLiveClient.buildSetupJson())
        val setup = root.getJSONObject("setup")
        val realtime = setup.getJSONObject("realtimeInputConfig")
        val vad = realtime.getJSONObject("automaticActivityDetection")

        assertEquals("NO_INTERRUPTION", realtime.getString("activityHandling"))
        assertEquals(false, vad.getBoolean("disabled"))
        assertEquals("START_SENSITIVITY_HIGH", vad.getString("startOfSpeechSensitivity"))
        assertEquals("END_SENSITIVITY_HIGH", vad.getString("endOfSpeechSensitivity"))
        assertEquals(40, vad.getInt("prefixPaddingMs"))
        assertEquals(500, vad.getInt("silenceDurationMs"))
        assertEquals(GeminiLiveClient.MODEL, setup.getString("model"))
    }
}
