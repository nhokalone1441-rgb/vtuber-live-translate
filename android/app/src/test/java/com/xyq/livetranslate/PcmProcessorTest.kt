package com.xyq.livetranslate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmProcessorTest {
    @Test
    fun emitsFortyMillisecondPcmChunksOnlyWhenFull() {
        val chunks = mutableListOf<ByteArray>()
        val processor = PcmProcessor(srcRate = 16_000, srcChannels = 1) { chunks += it }

        processor.feed(ShortArray(639), 639)
        assertTrue(chunks.isEmpty())

        processor.feed(shortArrayOf(123), 1)
        assertEquals(1, chunks.size)
        assertEquals(1_280, chunks.single().size)
    }

    @Test
    fun preservesChunkBoundariesAcrossConsecutiveInputBuffers() {
        val chunks = mutableListOf<ByteArray>()
        val processor = PcmProcessor(srcRate = 16_000, srcChannels = 1) { chunks += it }

        processor.feed(ShortArray(400) { it.toShort() }, 400)
        processor.feed(ShortArray(880) { (it + 400).toShort() }, 880)

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.size == 1_280 })
        // First sample is encoded as little-endian PCM16.
        assertEquals(0, chunks.first()[0].toInt())
        assertEquals(0, chunks.first()[1].toInt())
    }
}
