# API 文档

> 回答 [issue #2](https://github.com/neboyang/VoiceChanger/issues/2) / [#3](https://github.com/neboyang/VoiceChanger/issues/3)：库的完整调用方式。

包名：`io.github.neboyang.voicechanger`

## VoiceChanger（门面）

一站式「录音 → 变声 → 播放」。适合大多数场景；需要精细控制时用下方底层组件。

```kotlin
class VoiceChanger(context: Context, config: AudioConfig = AudioConfig())
```

| 成员 | 说明 |
|---|---|
| `recorder: VoiceRecorder` | 底层录音器，可订阅其 `state` / `amplitude` |
| `player: VoicePlayer` | 底层播放器 |
| `lastRecording: File?` | 最近一次录音的 PCM 文件 |
| `startRecording()` | 开始录音（需已授予 RECORD_AUDIO），中间文件在应用 cache 目录 |
| `pauseRecording()` / `resumeRecording()` | 暂停/恢复 |
| `suspend stopRecording(): RecordingResult` | 停止并返回录音结果 |
| `suspend changeVoice(effect, fileName, onProgress): File` | 对最近录音变声；`fileName` 以 `.wav` 结尾输出 WAV，否则输出 AAC(M4A)；默认输出到 `getExternalFilesDir(Music)/voicechanger/`（无需存储权限） |
| `play(file) { onCompletion }` / `stopPlaying()` | 播放/停止 |
| `release()` | 释放播放器并清理录音缓存 |

异常约定：所有失败以异常抛出（协程内可直接 try/catch 或 `runCatching`），不再使用 1.x 的消息码回调。

## VoiceRecorder

PCM 录音器，流式写盘，实例可复用。

```kotlin
class VoiceRecorder(config: AudioConfig = AudioConfig())

fun start(outputFile: File)                  // @RequiresPermission(RECORD_AUDIO)
fun pause() / resume()
suspend fun stop(): RecordingResult          // 录音线程中的异常在此抛出

val state: StateFlow<State>                  // IDLE / RECORDING / PAUSED
val amplitude: StateFlow<Float>              // 实时音量 RMS，0~1
```

`RecordingResult(file: File, durationMs: Long, config: AudioConfig)` —— `file` 为裸 PCM（16-bit LE）。

## VoiceProcessor

离线变声管线（单例对象），全程流式，支持协程取消。

```kotlin
suspend fun process(
    input: File,                       // 裸 PCM 或标准 WAV（自动识别）
    output: File,                      // .wav → WAV；其他 → AAC(M4A)
    effect: VoiceEffect,
    config: AudioConfig = AudioConfig(),
    onProgress: ((Float) -> Unit)? = null,   // 0~1，IO 线程回调
): File

// 流式版本：任意 PCM 流 → 变声后的裸 PCM 流（网络流、管道、Socket 等）
suspend fun processStream(
    input: InputStream,                // 裸 PCM（16-bit LE），不含 WAV 头
    output: OutputStream,
    effect: VoiceEffect,
    config: AudioConfig = AudioConfig(),
): Long                                // 写出的字节数
```

## RealtimeVoiceChanger

实时（流式）变声：麦克风 → SoundTouch → 扬声器/耳机，边说边听。

```kotlin
class RealtimeVoiceChanger(config: AudioConfig = AudioConfig())

fun start()                          // @RequiresPermission(RECORD_AUDIO)
fun stop()                           // 幂等，毫秒级返回

var pitchSemiTones: Float            // [-24, 24]，运行中修改实时生效
var onError: ((Throwable) -> Unit)?  // 音频线程异常回调，此后自动停止
val isRunning: StateFlow<Boolean>
val amplitude: StateFlow<Float>      // 输入音量 0~1
```

限制与建议：

- **请佩戴耳机**，否则会啸叫回授
- 实时模式只支持变调（pitch）；tempo/rate 会改变输出时长，实时链路中会导致缓冲堆积或断流
- 采集源为 `VOICE_COMMUNICATION`（多数设备启用系统回声消除）；端到端延迟约 100~300ms
- 与 `VoiceRecorder` 都占用麦克风，勿同时启动

## VoiceEffect

```kotlin
data class VoiceEffect(
    val pitchSemiTones: Float = 0f,    // [-24, 24]，正=升调
    val tempo: Float = 1f,             // (0.1, 10]，变速不变调
    val rate: Float = 1f,              // (0.1, 10]，变速且变调
)
```

预设：`NONE` / `KITTY` / `ROSE` / `WOMAN` / `UNCLE` / `MAN` / `TOM`；`PRESETS: Map<String, VoiceEffect>` 提供带中文名的全量列表，便于 UI 遍历。参数含义与调参方法见[音色调参指南](voice-tuning.md)。

## SoundTouch

SoundTouch 的直接封装，处理任意 16-bit PCM 流（实时场景也可用）。**非线程安全**，用完必须 `close()`（推荐 `use { }`）。

```kotlin
class SoundTouch(sampleRate: Int, channels: Int) : AutoCloseable

fun setPitchSemiTones(semiTones: Float)      // [-24, 24]
fun setTempo(tempo: Float)                   // (0.1, 10]
fun setRate(rate: Float)                     // (0.1, 10]
fun applyEffect(effect: VoiceEffect)
fun putSamples(samples: ShortArray, frames: Int = samples.size / channels)
fun receiveSamples(buffer: ShortArray): Int  // 返回帧数，0=暂无数据
fun flush()                                  // 输入结束后必须调用，再取空残留
fun availableFrames(): Int
override fun close()

companion object {
    val version: String                      // 底层库版本，如 "2.4.1"
}
```

典型循环：

```kotlin
SoundTouch(44100, 1).use { st ->
    st.applyEffect(effect)
    while (读到 pcmChunk) {
        st.putSamples(pcmChunk)
        while (true) { val n = st.receiveSamples(buf); if (n <= 0) break; 写出(buf, n) }
    }
    st.flush()
    while (true) { val n = st.receiveSamples(buf); if (n <= 0) break; 写出(buf, n) }
}
```

## VoicePlayer

```kotlin
class VoicePlayer

fun play(file: File)                 // 会先停止上一个；IOException 表示格式不支持
fun stop()                           // 幂等
fun currentPosition(): Int           // 毫秒
val isPlaying: Boolean
val currentFile: File?
var onCompletion: (() -> Unit)?      // 自然播完触发；主动 stop 不触发
fun release()
```

## AudioConfig / WavFile

```kotlin
data class AudioConfig(sampleRate: Int = 44100, channels: Int = 1)
// bytesPerFrame / bytesPerSecond

object WavFile {
    const val HEADER_SIZE = 44
    fun header(dataLength: Int, config: AudioConfig): ByteArray
    fun pcmToWav(pcm: File, wav: File, config: AudioConfig)
    fun isWav(file: File): Boolean
}
```

## 线程模型

- `VoiceRecorder` / `RealtimeVoiceChanger` 内部使用独立音频线程；`state`/`amplitude`/`isRunning` 可在任意线程收集
- `VoiceProcessor.process` / `processStream` 在 `Dispatchers.IO` 执行，`onProgress` 在 IO 线程回调（更新 UI 请自行切主线程）
- `VoicePlayer` / `VoiceChanger` 请在主线程调用
- `SoundTouch` 实例限单线程使用
