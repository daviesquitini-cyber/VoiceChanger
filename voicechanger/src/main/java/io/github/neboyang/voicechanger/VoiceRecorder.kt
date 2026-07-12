package io.github.neboyang.voicechanger

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** 一次录音的结果。 */
data class RecordingResult(
    /** 裸 PCM 文件（16-bit LE，参数见 [config]）。 */
    val file: File,
    /** 录音时长（毫秒，不含暂停时间）。 */
    val durationMs: Long,
    val config: AudioConfig,
)

/**
 * PCM 录音器。数据边录边写入文件（1.x 版本全量驻留内存，长录音会 OOM）。
 *
 * - 状态通过 [state] 观察，实时音量通过 [amplitude]（0~1 的 RMS 归一化值）观察
 * - 暂停采用锁等待实现（1.x 为忙等空转），暂停期间关闭采集、不占用麦克风缓冲
 * - 实例可复用：stop 之后可再次 start
 *
 * 调用方需自行申请并持有 RECORD_AUDIO 运行时权限。
 */
class VoiceRecorder(val config: AudioConfig = AudioConfig()) {

    enum class State { IDLE, RECORDING, PAUSED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    /** 当前音量，RMS 归一化到 0~1，可直接绑定进度条。 */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val lock = Object()

    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false
    private var pending: CompletableDeferred<RecordingResult>? = null

    /**
     * 开始录音，PCM 数据流式写入 [outputFile]。
     *
     * @throws IllegalStateException 已在录音中，或 AudioRecord 初始化失败（通常是未授予录音权限）
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(outputFile: File) {
        check(_state.value == State.IDLE) { "录音器忙，当前状态: ${_state.value}" }

        val channelMask = if (config.channels == 1)
            AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "设备不支持该音频配置: $config" }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate, channelMask,
            AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("AudioRecord 初始化失败，请确认已授予 RECORD_AUDIO 权限")
        }

        stopRequested = false
        pauseRequested = false
        val deferred = CompletableDeferred<RecordingResult>()
        pending = deferred
        _state.value = State.RECORDING

        thread(name = "VoiceRecorder") { recordLoop(audioRecord, outputFile, deferred) }
    }

    /** 暂停录音。暂停期间释放采集，不会丢数据也不空转 CPU。 */
    fun pause() {
        if (_state.value == State.RECORDING) {
            pauseRequested = true
            synchronized(lock) { lock.notifyAll() }
        }
    }

    /** 恢复录音。 */
    fun resume() {
        if (pauseRequested) {
            pauseRequested = false
            synchronized(lock) { lock.notifyAll() }
        }
    }

    /**
     * 停止录音并等待收尾完成。
     *
     * @return 录音结果；录音线程内发生的异常会在此处抛出
     */
    suspend fun stop(): RecordingResult {
        val deferred = pending ?: throw IllegalStateException("当前没有进行中的录音")
        stopRequested = true
        synchronized(lock) { lock.notifyAll() }
        return deferred.await().also { pending = null }
    }

    private fun recordLoop(
        audioRecord: AudioRecord,
        outputFile: File,
        deferred: CompletableDeferred<RecordingResult>,
    ) {
        var totalFrames = 0L
        try {
            outputFile.parentFile?.mkdirs()
            audioRecord.startRecording()

            // 约 100ms 一个读取块
            val buffer = ShortArray(config.sampleRate / 10 * config.channels)
            val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

            BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                while (!stopRequested) {
                    if (pauseRequested) {
                        audioRecord.stop()
                        _amplitude.value = 0f
                        _state.value = State.PAUSED
                        synchronized(lock) {
                            while (pauseRequested && !stopRequested) lock.wait()
                        }
                        if (stopRequested) break
                        audioRecord.startRecording()
                        _state.value = State.RECORDING
                    }

                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read < 0) throw IllegalStateException("AudioRecord.read 失败: $read")
                    if (read == 0) continue

                    _amplitude.value = rms(buffer, read)

                    byteBuffer.clear()
                    byteBuffer.asShortBuffer().put(buffer, 0, read)
                    out.write(byteBuffer.array(), 0, read * 2)
                    totalFrames += read / config.channels
                }
            }
            deferred.complete(
                RecordingResult(outputFile, totalFrames * 1000L / config.sampleRate, config))
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            _amplitude.value = 0f
            _state.value = State.IDLE
        }
    }

    private fun rms(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        return (sqrt(sum / length) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }
}
