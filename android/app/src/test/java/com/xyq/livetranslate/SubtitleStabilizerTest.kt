package com.xyq.livetranslate

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

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

        // Punctuation must not roll the subtitle row immediately; keep the full utterance live.
        assertEquals("", renders.last().first)
        assertEquals("第一句。第二句！第三句？", renders.last().second)

        shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS)
        assertEquals("", renders.last().first)
        assertEquals("第一句。第二句！第三句？", renders.last().second)

        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
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
    fun keepsTheSameDialogueWhenItIsRepeatedAfterAPause() {
        val renders = mutableListOf<Pair<String, String>>()
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 100L,
            onCommit = { committed += it },
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        stabilizer.onFragment("Go!")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertEquals("Go!", renders.last().first)
        val committedAt = SubtitleStabilizer::class.java
            .getDeclaredField("lastCommittedAtMs")
            .apply { isAccessible = true }
            .getLong(stabilizer)

        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)
        stabilizer.onFragment("Go!")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

        assertEquals("Go!", renders.last().first)
        assertEquals(listOf("Go!", "Go!"), committed)
        val repeatedAt = SubtitleStabilizer::class.java
            .getDeclaredField("lastCommittedAtMs")
            .apply { isAccessible = true }
            .getLong(stabilizer)
        assertEquals(true, repeatedAt > committedAt)
    }

    @Test
    fun suppressesAnImmediateExactReplayButNotALaterRepeatedLine() {
        val renders = mutableListOf<Pair<String, String>>()
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 100L,
            onCommit = { committed += it },
            onRender = { confirmed, current -> renders += confirmed to current },
        )
        val committedAtField = SubtitleStabilizer::class.java
            .getDeclaredField("lastCommittedAtMs")
            .apply { isAccessible = true }

        stabilizer.onFragment("Go!")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        val firstCommitAt = committedAtField.getLong(stabilizer)

        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        stabilizer.onFragment("Go!")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

        assertEquals(firstCommitAt, committedAtField.getLong(stabilizer))
        assertEquals(listOf("Go!"), committed)
    }

    @Test
    fun doesNotDropAShortLineJustBecauseItWasContainedInThePreviousLine() {
        val renders = mutableListOf<Pair<String, String>>()
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 100L,
            onCommit = { committed += it },
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        stabilizer.onFragment("I don't know.")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)

        stabilizer.onFragment("Know.")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

        assertEquals("Know.", renders.last().first)
        assertEquals(listOf("I don't know.", "Know."), committed)
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
