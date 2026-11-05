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
    fun preservesEverySampleAtNativeSixteenKhzAcrossBuffers() {
        val chunks = mutableListOf<ByteArray>()
        val processor = PcmProcessor(srcRate = 16_000, srcChannels = 1) { chunks += it }

        processor.feed(ShortArray(400) { it.toShort() }, 400)
        processor.feed(ShortArray(240) { (it + 400).toShort() }, 240)

        val samples = decodePcm16(chunks.single())
        assertEquals(640, samples.size)
        assertEquals(0, samples[0].toInt())
        assertEquals(1, samples[1].toInt())
        assertEquals(400, samples[400].toInt())
        assertEquals(639, samples[639].toInt())
    }

    @Test
    fun resamplesFortyEightKhzWithoutSkippingTheFirstSampleOfEachBuffer() {
        val chunks = mutableListOf<ByteArray>()
        val processor = PcmProcessor(srcRate = 48_000, srcChannels = 1) { chunks += it }

        processor.feed(ShortArray(1_920) { it.toShort() }, 1_920)

        val samples = decodePcm16(chunks.single())
        assertEquals(640, samples.size)
        assertEquals(0, samples[0].toInt())
        assertEquals(3, samples[1].toInt())
        assertEquals(6, samples[2].toInt())
        assertEquals(9, samples[3].toInt())
    }

    @Test
    fun resamplingPhaseRemainsContinuousAcrossAudioRecordReads() {
        val chunks = mutableListOf<ByteArray>()
        val processor = PcmProcessor(srcRate = 48_000, srcChannels = 1) { chunks += it }

        processor.feed(ShortArray(1_920) { it.toShort() }, 1_920)
        processor.feed(ShortArray(1_920) { (it + 1_920).toShort() }, 1_920)

        assertEquals(2, chunks.size)
        assertEquals(0, decodePcm16(chunks[0])[0].toInt())
        assertEquals(3, decodePcm16(chunks[0])[1].toInt())
        assertEquals(1_920, decodePcm16(chunks[1])[0].toInt())
        assertEquals(1_923, decodePcm16(chunks[1])[1].toInt())
    }

    @Test
    fun resamplesFortyFourPointOneKhzToOneHundredMillisecondsOfPcm() {
        val chunks = mutableListOf<ByteArray>()
        val processor = PcmProcessor(srcRate = 44_100, srcChannels = 1) { chunks += it }

        processor.feed(ShortArray(1_764) { it.toShort() }, 1_764)

        val samples = decodePcm16(chunks.single())
        assertEquals(640, samples.size)
        assertEquals(0, samples[0].toInt())
        assertEquals(2, samples[1].toInt())
        assertEquals(5, samples[2].toInt())
    }

    private fun decodePcm16(bytes: ByteArray): ShortArray =
        ShortArray(bytes.size / 2) { index ->
            val low = bytes[index * 2].toInt() and 0xFF
            val high = bytes[index * 2 + 1].toInt()
            (low or (high shl 8)).toShort()
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
