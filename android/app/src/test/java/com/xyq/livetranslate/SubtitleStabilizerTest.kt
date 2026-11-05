package com.xyq.livetranslate

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SubtitleStabilizerTest {
    @Test
    fun buffersFragmentsAndCommitsOneCompleteDialogueBlockAfterShortQuietWindow() {
        val committed = mutableListOf<String>()
        val renders = mutableListOf<Pair<String, String>>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 800L,
            onCommit = { committed += it },
            onRender = { confirmed, current -> renders += confirmed to current },
        )

        stabilizer.onFragment("Tôi biết rồi.")
        stabilizer.onFragment(" Nhưng chúng ta phải đi ngay!")
        assertTrue(committed.isEmpty())
        assertEquals("", renders.last().second)

        shadowOf(Looper.getMainLooper()).idleFor(799, TimeUnit.MILLISECONDS)
        assertTrue(committed.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)

        assertEquals(listOf("Tôi biết rồi. Nhưng chúng ta phải đi ngay!"), committed)
        assertEquals("", renders.last().second)
    }

    @Test
    fun doesNotSplitMultipleSentencesIntoKaraokeRows() {
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 800L,
            onCommit = { committed += it },
            onRender = { _, _ -> },
        )

        stabilizer.onFragment("第一句。第二句！第三句？")
        assertTrue(committed.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)

        assertEquals(listOf("第一句。第二句！第三句？"), committed)
    }

    @Test
    fun cumulativeSnapshotsReplaceEarlierPartialTextInsteadOfDuplicatingIt() {
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 800L,
            onCommit = { committed += it },
            onRender = { _, _ -> },
        )

        stabilizer.onFragment("We need to")
        stabilizer.onFragment("We need to leave now.")
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)

        assertEquals(listOf("We need to leave now."), committed)
    }

    @Test
    fun appendsDeltaFragmentsWithoutRepeatingTheirOverlap() {
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 800L,
            onCommit = { committed += it },
            onRender = { _, _ -> },
        )

        stabilizer.onFragment("I thought")
        stabilizer.onFragment("thought you knew.")
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)

        assertEquals(listOf("I thought you knew."), committed)
    }

    @Test
    fun repeatedDialogueAfterARealPauseIsNotSuppressed() {
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 100L,
            onCommit = { committed += it },
            onRender = { _, _ -> },
        )

        stabilizer.onFragment("Go!")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)
        stabilizer.onFragment("Go!")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

        assertEquals(listOf("Go!", "Go!"), committed)
    }

    @Test
    fun shortNonSpeechVocalizationIsFilteredAfterBuffering() {
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 100L,
            onCommit = { committed += it },
            onRender = { _, _ -> },
        )

        stabilizer.onFragment("um")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

        assertTrue(committed.isEmpty())
    }

    @Test
    fun resetClearsPendingTextAndCancelsItsCommit() {
        val committed = mutableListOf<String>()
        val stabilizer = SubtitleStabilizer(
            handler = Handler(Looper.getMainLooper()),
            idleCommitMs = 800L,
            onCommit = { committed += it },
            onRender = { _, _ -> },
        )

        stabilizer.onFragment("This should not survive pause")
        stabilizer.reset()
        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)

        assertTrue(committed.isEmpty())
    }
}
