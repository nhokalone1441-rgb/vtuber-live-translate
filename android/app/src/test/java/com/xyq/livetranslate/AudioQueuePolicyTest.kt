package com.xyq.livetranslate

import java.util.concurrent.LinkedBlockingDeque
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioQueuePolicyTest {
    @Test
    fun replayKeepsQueueWithinBudgetAndPreservesChronologicalOrder() {
        val queue = LinkedBlockingDeque<Int>().apply {
            (1..30).forEach(::offerLast)
        }

        prependBounded(queue, (31..40).toList(), maxSize = 30)

        assertEquals(30, queue.size)
        assertEquals((31..40).toList() + (11..30).toList(), queue.toList())
    }

    @Test
    fun replayLargerThanBudgetKeepsOnlyNewestReplayChunks() {
        val queue = LinkedBlockingDeque<Int>().apply {
            (1..5).forEach(::offerLast)
        }

        prependBounded(queue, (6..40).toList(), maxSize = 30)

        assertEquals(30, queue.size)
        assertEquals((11..40).toList(), queue.toList())
    }
}
