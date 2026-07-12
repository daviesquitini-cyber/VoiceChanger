package io.github.neboyang.voicechanger

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 标准 44 字节 WAV（RIFF/PCM）头的读写工具。
 * 修复了 1.x 版本中头部采样率与实际数据不一致的问题——
 * 头部字段一律取自 [AudioConfig]，与录音/处理链路共用同一配置。
 */
object WavFile {

    /** 标准 PCM WAV 头长度。 */
    const val HEADER_SIZE = 44

    /** 生成 44 字节 WAV 头。[dataLength] 为 PCM 数据段的字节数。 */
    fun header(dataLength: Int, config: AudioConfig): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataLength)                       // RIFF chunk size
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)                                    // fmt chunk size
        buf.putShort(1)                                   // audio format = PCM
        buf.putShort(config.channels.toShort())
        buf.putInt(config.sampleRate)
        buf.putInt(config.bytesPerSecond)
        buf.putShort(config.bytesPerFrame.toShort())      // block align
        buf.putShort(16)                                  // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataLength)
        return buf.array()
    }

    /** 将裸 PCM 文件包装为 WAV 文件。 */
    fun pcmToWav(pcm: File, wav: File, config: AudioConfig) {
        val dataLength = pcm.length()
        require(dataLength <= Int.MAX_VALUE - 36) { "PCM 文件过大: $dataLength 字节" }
        wav.outputStream().buffered().use { out ->
            out.write(header(dataLength.toInt(), config))
            pcm.inputStream().buffered().use { it.copyTo(out) }
        }
    }

    /** 判断文件是否为 WAV（RIFF....WAVE 魔数）。 */
    fun isWav(file: File): Boolean {
        if (file.length() < HEADER_SIZE) return false
        val head = ByteArray(12)
        file.inputStream().use { if (it.read(head) != 12) return false }
        return head.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
                head.copyOfRange(8, 12).contentEquals("WAVE".toByteArray(Charsets.US_ASCII))
    }
}
