package io.github.neboyang.voicechanger

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * 裸 PCM → AAC(M4A) 编码器，基于公开 API MediaCodec + MediaMuxer。
 *
 * 替代 1.x 版本使用的 android.media.AmrInputStream——那是一个 @hide API，
 * 普通工程无法编译，且已在 Android 9 (API 28) 中被移除。
 */
internal object AacEncoder {

    private const val TIMEOUT_US = 10_000L
    private const val PCM_CHUNK_BYTES = 16 * 1024

    fun encode(
        pcmFile: File,
        outFile: File,
        config: AudioConfig,
        bitRate: Int = 96_000,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, config.sampleRate, config.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PCM_CHUNK_BYTES)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val totalBytes = pcmFile.length().coerceAtLeast(1)
        var readBytes = 0L
        var totalFrames = 0L
        var inputDone = false

        codec.start()
        try {
            pcmFile.inputStream().buffered().use { input ->
                val chunk = ByteArray(PCM_CHUNK_BYTES)
                while (true) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)!!
                            inBuf.clear()
                            val maxRead = minOf(chunk.size, inBuf.remaining())
                            val n = input.read(chunk, 0, maxRead)
                            val ptsUs = totalFrames * 1_000_000L / config.sampleRate
                            if (n < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                inBuf.put(chunk, 0, n)
                                codec.queueInputBuffer(inIndex, 0, n, ptsUs, 0)
                                totalFrames += n / config.bytesPerFrame
                                readBytes += n
                                onProgress?.invoke(readBytes.toFloat() / totalBytes)
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxerStarted) { "输出格式变化了多次" }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0 // CSD 已包含在 outputFormat 中
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (muxerStarted) muxer.stop() }
            muxer.release()
        }
    }
}
