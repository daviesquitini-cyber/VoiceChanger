package io.github.neboyang.voicechanger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * 离线变声处理管线：PCM/WAV 输入 → SoundTouch 变声 → WAV 或 AAC(M4A) 输出。
 *
 * 全程流式处理，内存占用与音频长度无关；支持协程取消。
 * 需要处理任意流（网络、管道等）时用 [processStream]；
 * 需要更底层的逐块控制时直接用 [SoundTouch]。
 */
object VoiceProcessor {

    /** 单次送入 SoundTouch 的帧数。 */
    private const val CHUNK_FRAMES = 4096

    /**
     * 对 [input] 文件应用 [effect]，结果写入 [output] 文件。
     *
     * @param input  裸 PCM（16-bit LE）或标准 WAV 文件（自动识别并跳过文件头）
     * @param output 输出文件，按扩展名决定容器：`.wav` 输出 WAV，其余输出 AAC(M4A)
     * @param config 输入 PCM 的音频参数，必须与录音时一致
     * @param onProgress 进度回调（0~1），在 IO 线程回调
     * @return [output]
     */
    suspend fun process(
        input: File,
        output: File,
        effect: VoiceEffect,
        config: AudioConfig = AudioConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        require(input.exists() && input.length() > 0) { "输入文件不存在或为空: $input" }
        output.parentFile?.mkdirs()

        // 第一阶段：SoundTouch 变声，输出到临时 PCM 文件
        val processedPcm = File.createTempFile("st_", ".pcm", output.parentFile)
        try {
            val headerSkip = if (WavFile.isWav(input)) WavFile.HEADER_SIZE.toLong() else 0L
            val totalBytes = (input.length() - headerSkip).coerceAtLeast(1)
            // 进度权重：WAV 封装很快（变声占 95%），AAC 编码较慢（变声占 60%）
            val stageWeight = if (output.extension.equals("wav", true)) 0.95f else 0.6f

            input.inputStream().buffered().use { ins ->
                ins.skip(headerSkip)
                processedPcm.outputStream().buffered().use { outs ->
                    pump(ins, outs, effect, config) { bytesRead ->
                        onProgress?.invoke(bytesRead.toFloat() / totalBytes * stageWeight)
                    }
                }
            }

            // 第二阶段：封装容器
            coroutineContext.ensureActive()
            if (output.extension.equals("wav", true)) {
                WavFile.pcmToWav(processedPcm, output, config)
                onProgress?.invoke(1f)
            } else {
                AacEncoder.encode(processedPcm, output, config) { p ->
                    onProgress?.invoke(0.6f + p * 0.4f)
                }
            }
            output
        } finally {
            processedPcm.delete()
        }
    }

    /**
     * 流式变声：从 [input] 读入 16-bit LE PCM，变声后的裸 PCM 写入 [output]。
     * 适合来源/去向不是文件的场景（网络流、管道、Socket 等）。
     *
     * 输入必须是**裸 PCM**（不含 WAV 头）；调用方负责关闭两个流。
     *
     * @return 写出的字节数
     */
    suspend fun processStream(
        input: InputStream,
        output: OutputStream,
        effect: VoiceEffect,
        config: AudioConfig = AudioConfig(),
    ): Long = withContext(Dispatchers.IO) {
        pump(input, output, effect, config, onBytesRead = null)
    }

    /**
     * 核心泵：input(PCM) → SoundTouch → output(PCM)，含尾部 flush。
     * 支持协程取消；返回写出的字节数。
     */
    private suspend fun pump(
        input: InputStream,
        output: OutputStream,
        effect: VoiceEffect,
        config: AudioConfig,
        onBytesRead: ((Long) -> Unit)?,
    ): Long {
        var readBytes = 0L
        var writtenBytes = 0L

        SoundTouch(config.sampleRate, config.channels).use { st ->
            st.applyEffect(effect)

            val inBytes = ByteArray(CHUNK_FRAMES * config.bytesPerFrame)
            val inShorts = ShortArray(CHUNK_FRAMES * config.channels)
            val outShorts = ShortArray(CHUNK_FRAMES * 2 * config.channels)
            val outBytes = ByteBuffer.allocate(outShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)

            fun drain() {
                while (true) {
                    val frames = st.receiveSamples(outShorts)
                    if (frames <= 0) break
                    val samples = frames * config.channels
                    outBytes.clear()
                    outBytes.asShortBuffer().put(outShorts, 0, samples)
                    output.write(outBytes.array(), 0, samples * 2)
                    writtenBytes += samples * 2L
                }
            }

            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(inBytes)
                if (n <= 0) break
                val usable = n - n % config.bytesPerFrame
                if (usable == 0) break
                ByteBuffer.wrap(inBytes, 0, usable)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .get(inShorts, 0, usable / 2)
                st.putSamples(inShorts, usable / config.bytesPerFrame)
                drain()

                readBytes += n
                onBytesRead?.invoke(readBytes)
            }

            // 冲出管线尾部残留（1.x 缺这一步，结尾会被截断）
            st.flush()
            drain()
        }
        output.flush()
        return writtenBytes
    }
}
