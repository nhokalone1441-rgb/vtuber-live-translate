package com.xyq.livetranslate

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleStabilizerTest {
    @Test
    fun keepsEverySentenceFromOneServerFragment() {
        val renders = mutableListOf<Pair<String, String>>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        stabilizer.onFragment("第一句。第二句！第三句？")

        assertEquals("第一句。第二句！第三句？", renders.last().first)
        assertEquals("", renders.last().second)
    }

    @Test
    fun longUtteranceStaysLiveInsteadOfBeingSplitAtTheOldShortLimit() {
        val renders = mutableListOf<Pair<String, String>>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            maxCurrentChars = 240,
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        val longUtterance = "这是一段比较长的连续对话内容".repeat(8)
        stabilizer.onFragment(longUtterance)

        assertEquals("", renders.last().first)
        assertEquals(longUtterance, renders.last().second)
    }

    @Test
    fun suppressesStandaloneMoaningSounds() {
        val renders = mutableListOf<Pair<String, String>>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            maxCurrentChars = 1,
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        stabilizer.onFragment("um, á, ớ")

        assertEquals("", renders.last().first)
        assertEquals("", renders.last().second)
    }

    @Test
    fun preservesMeaningfulExclamationAndDialogue() {
        val renders = mutableListOf<Pair<String, String>>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            maxCurrentChars = 1,
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        stabilizer.onFragment("Á! Có ai không?")

        assertEquals("Á! Có ai không?", renders.last().first)
        assertEquals("", renders.last().second)
    }

    @Test
    fun suppressesBareMoanSyllableButKeepsExclaimedAh() {
        assertEquals(true, SubtitleSpeechFilter.isNonSpeechVocalization("á"))
        assertEquals(false, SubtitleSpeechFilter.isNonSpeechVocalization("Á!"))
        assertEquals(false, SubtitleSpeechFilter.isNonSpeechVocalization("Á! Có ai không?"))
    }
}
