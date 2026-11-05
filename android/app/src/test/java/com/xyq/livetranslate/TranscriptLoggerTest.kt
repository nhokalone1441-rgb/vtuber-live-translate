package com.xyq.livetranslate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TranscriptLoggerTest {
    @Test
    fun recordsRepeatedIdenticalConfirmedDialogueAsSeparateSegments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HistoryStore.list(context).forEach { HistoryStore.delete(context, it.fileName) }

        val logger = TranscriptLogger(
            context = context,
            mode = TranslationMode.VIDEO,
            plan = TranslationPlan.default(TranslationMode.VIDEO),
            sceneLabel = "Test",
        )
        logger.commitTranslation("Go!")
        logger.commitTranslation("Go!")
        logger.close()

        var savedItem: HistoryStore.HistoryItem? = null
        var savedSession: HistorySession? = null
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            val item = HistoryStore.list(context).singleOrNull()
            val session = item?.let { HistoryStore.load(context, it.fileName) }
            if (session?.segments?.size == 2) {
                savedItem = item
                savedSession = session
                break
            }
            Thread.sleep(20)
        }

        assertEquals(
            listOf("Go!", "Go!"),
            savedSession?.segments?.map { it.translatedText },
        )
        savedItem?.let { HistoryStore.delete(context, it.fileName) }
    }
}
