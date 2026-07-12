package io.github.neboyang.voicechanger

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class PcmFrameReaderTest {

    @Test
    fun `preserves frames across one-byte reads`() {
        val source = ByteArray(20) { it.toByte() }
        val reader = PcmFrameReader(ShortReadInputStream(source, 1), bufferSize = 8, bytesPerFrame = 4)
        val actual = ArrayList<Byte>()

        while (true) {
            val read = reader.read()
            if (read < 0) break
            reader.buffer.copyOf(read).forEach(actual::add)
        }

        assertArrayEquals(source, actual.toByteArray())
        assertEquals(source.size.toLong(), reader.totalBytesRead)
    }

    @Test
    fun `carries remainder between uneven chunks`() {
        val source = ByteArray(32) { (it + 10).toByte() }
        val reader = PcmFrameReader(ShortReadInputStream(source, 7), bufferSize = 16, bytesPerFrame = 4)
        val actual = ArrayList<Byte>()

        while (true) {
            val read = reader.read()
            if (read < 0) break
            reader.buffer.copyOf(read).forEach(actual::add)
        }

        assertArrayEquals(source, actual.toByteArray())
    }

    @Test
    fun `rejects partial frame at EOF`() {
        val reader = PcmFrameReader(ByteArrayInputStream(ByteArray(3)), bufferSize = 8, bytesPerFrame = 4)
        assertThrows(IOException::class.java) { reader.read() }
    }

    private class ShortReadInputStream(
        bytes: ByteArray,
        private val maxRead: Int,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, minOf(length, maxRead))
    }
}
