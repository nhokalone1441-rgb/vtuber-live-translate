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

        val item = HistoryStore.list(context).single()
        val session = HistoryStore.load(context, item.fileName)!!
        assertEquals(
            listOf("Go!", "Go!"),
            session.segments.map { it.translatedText },
        )

        HistoryStore.delete(context, item.fileName)
    }
}
