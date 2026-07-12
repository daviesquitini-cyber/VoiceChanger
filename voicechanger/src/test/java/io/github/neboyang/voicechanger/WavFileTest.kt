package io.github.neboyang.voicechanger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileTest {

    @Test
    fun `header is 44 bytes with correct magic and fields`() {
        val config = AudioConfig(sampleRate = 44100, channels = 1)
        val header = WavFile.header(dataLength = 1000, config = config)

        assertEquals(WavFile.HEADER_SIZE, header.size)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 1000, buf.getInt(4))          // RIFF chunk size
        assertEquals(1, buf.getShort(20).toInt())       // PCM format
        assertEquals(1, buf.getShort(22).toInt())       // channels
        // 1.x 的 bug：头里写死 16000Hz 而数据是 8000Hz，这里锁死采样率一致性
        assertEquals(44100, buf.getInt(24))             // sample rate
        assertEquals(44100 * 2, buf.getInt(28))         // byte rate
        assertEquals(2, buf.getShort(32).toInt())       // block align
        assertEquals(16, buf.getShort(34).toInt())      // bits per sample
        assertEquals(1000, buf.getInt(40))              // data length
    }

    @Test
    fun `stereo header fields scale with channels`() {
        val config = AudioConfig(sampleRate = 16000, channels = 2)
        val header = WavFile.header(dataLength = 64, config = config)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, buf.getShort(22).toInt())
        assertEquals(16000, buf.getInt(24))
        assertEquals(16000 * 4, buf.getInt(28))
        assertEquals(4, buf.getShort(32).toInt())
    }
}
