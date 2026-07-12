# VoiceChanger

基于 [SoundTouch](https://codeberg.org/soundtouch/soundtouch) 的 Android 变声库：录音 → 变声（男变女、女变男、萝莉、大叔、汤姆猫…）→ 保存/播放，全流程开箱即用。

> **2.0 完全重写**：Kotlin + 协程 API、内置 SoundTouch 2.4.1 源码用 CMake 从源编译（支持 arm64-v8a 等全 ABI）、内置 JNI 绑定（不再缺 `net.surina.soundtouch.SoundTouch`，见 [#2](https://github.com/neboyang/VoiceChanger/issues/2)）、输出 AAC/M4A 与 WAV（替代已被 Android 移除的隐藏 API `AmrInputStream`）、附带完整 Demo（见 [#3](https://github.com/neboyang/VoiceChanger/issues/3)）与[调参指南](docs/voice-tuning.md)（见 [#1](https://github.com/neboyang/VoiceChanger/issues/1)）。迁移说明见 [CHANGELOG](CHANGELOG.md)。

## 特性

- 🎙 **录音**：`AudioRecord` 流式写盘，支持暂停/恢复，实时音量回调，长录音不占内存
- 🎭 **变声**：音调（半音）、节拍（变速不变调）、速率（变速变调）三参数自由组合，内置 7 种预设
- 💾 **输出**：WAV（无损）或 AAC/M4A（压缩，全 Android 版本可用的公开 API）
- 🧵 **现代 API**：Kotlin 协程 + `StateFlow`，也保留 Java 可调用的入口
- 📦 **零额外依赖**：native 层随库源码编译，无需手动放 `.so`；支持 armeabi-v7a / arm64-v8a / x86 / x86_64
- 🔒 **合规存储**：输出到应用专属目录，无需存储权限，兼容 Android 10+ 分区存储

## 快速开始

### 1. 引入依赖

通过 [JitPack](https://jitpack.io)：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.neboyang:VoiceChanger:2.0.0")
}
```

或直接以源码模块引入：把本仓库的 `voicechanger` 目录拷入你的工程，`include(":voicechanger")` 即可（需要 NDK/CMake，Android Studio 会自动下载）。

### 2. 声明权限

库的 Manifest 已声明 `RECORD_AUDIO`，你只需在录音前申请**运行时权限**：

```kotlin
registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> ... }
    .launch(Manifest.permission.RECORD_AUDIO)
```

### 3. 三步完成变声

```kotlin
val changer = VoiceChanger(context)

// ① 录音
changer.startRecording()
// changer.pauseRecording() / changer.resumeRecording()

// ② 停止并变声（suspend 函数，在协程中调用）
lifecycleScope.launch {
    val recording = changer.stopRecording()
    val file = changer.changeVoice(VoiceEffect.UNCLE)   // 输出 .m4a
    // ③ 播放
    changer.play(file) { /* 播放完成 */ }
}
```

观察录音状态与音量：

```kotlin
changer.recorder.state.collect { state -> ... }       // IDLE / RECORDING / PAUSED
changer.recorder.amplitude.collect { amp -> ... }     // 0~1，可直接绑定进度条
```

## 内置音效预设

| 预设 | pitchSemiTones | tempo | rate | 效果 |
|---|---|---|---|---|
| `VoiceEffect.NONE` | 0 | 1.0 | 1.0 | 原声 |
| `VoiceEffect.KITTY` | +4 | 1.02 | 1.2 | 小猫，尖细急促 |
| `VoiceEffect.ROSE` | +12.8 | 1.0 | 1.0 | 娃娃音（夸张） |
| `VoiceEffect.WOMAN` | +7 | 1.0 | 1.0 | 男声 → 女声（自然） |
| `VoiceEffect.UNCLE` | −3.9 | 1.0 | 1.0 | 大叔 |
| `VoiceEffect.MAN` | −7 | 1.0 | 1.0 | 女声 → 男声 |
| `VoiceEffect.TOM` | +10 | 1.005 | 0.993 | 汤姆猫 |

自定义音色只需构造 `VoiceEffect`：

```kotlin
val myEffect = VoiceEffect(pitchSemiTones = 8.5f, tempo = 1.1f)
```

三个参数怎么调、想要某种音色该用什么值，请看 **[音色调参指南](docs/voice-tuning.md)**。

## 进阶用法

不用门面类，直接组合底层组件（自定义路径、格式、进度）：

```kotlin
// 录音到指定文件
val recorder = VoiceRecorder(AudioConfig(sampleRate = 16000, channels = 1))
recorder.start(File(dir, "input.pcm"))
val result = recorder.stop()

// 变声：输出 .wav 得到无损 WAV，其他扩展名得到 AAC(M4A)
VoiceProcessor.process(
    input = result.file,
    output = File(dir, "output.wav"),
    effect = VoiceEffect(pitchSemiTones = -6f),
    config = result.config,
    onProgress = { p -> ... },     // 0~1
)

// 也可以直接使用 SoundTouch 处理任意 PCM 流
SoundTouch(sampleRate = 44100, channels = 1).use { st ->
    st.setPitchSemiTones(6f)
    st.putSamples(pcmChunk)
    val buf = ShortArray(4096)
    while (true) { val n = st.receiveSamples(buf); if (n <= 0) break; /* 消费 */ }
    st.flush()  // 尾部残留
    while (true) { val n = st.receiveSamples(buf); if (n <= 0) break; /* 消费 */ }
}
```

完整接口说明见 [docs/api.md](docs/api.md)。

## Demo

仓库自带可运行的演示工程（`app` 模块）：录音控制、7 种预设一键切换、三参数滑杆实时微调、变声进度与播放。

```bash
./gradlew :app:installDebug
```

## 构建要求

- Android Studio（Ladybug 及以上）/ AGP 8.7、Gradle 8.10（wrapper 已内置）
- JDK 17
- NDK 与 CMake 3.22 由 Android Studio 按需自动下载
- minSdk 21，compileSdk 35

## 工程结构

```
VoiceChanger/
├── voicechanger/                  # 库模块
│   └── src/main
│       ├── cpp/
│       │   ├── soundtouch/        # SoundTouch 2.4.1 源码（LGPL-2.1）
│       │   ├── soundtouch-jni.cpp # JNI 绑定
│       │   └── CMakeLists.txt
│       └── java/io/github/neboyang/voicechanger/
│           ├── VoiceChanger.kt    # 门面：录音→变声→播放
│           ├── VoiceRecorder.kt   # PCM 录音（流式写盘）
│           ├── VoiceProcessor.kt  # 变声管线（SoundTouch → WAV/M4A）
│           ├── VoiceEffect.kt     # 音效参数与预设
│           ├── SoundTouch.kt      # SoundTouch Kotlin 封装
│           ├── VoicePlayer.kt     # 播放器
│           ├── AacEncoder.kt      # PCM→AAC(M4A)（MediaCodec）
│           ├── WavFile.kt         # WAV 头读写
│           └── AudioConfig.kt     # 采样率/声道配置
├── app/                           # Demo
└── docs/                          # 调参指南、API 文档
```

数据流：`AudioRecord → PCM 文件 → SoundTouch(变调/变速) → flush → WAV 或 MediaCodec AAC → MediaPlayer`

## FAQ

**为什么输出 M4A 而不是 1.x 的 AMR？**
1.x 依赖隐藏 API `android.media.AmrInputStream`，普通工程无法编译，且该类已在 Android 9 中移除。AAC 音质更好、体积相近，且走公开 API。需要无损可输出 WAV。

**能实时变声（边说边听）吗？**
当前版本是离线管线（录完再处理）。SoundTouch 本身支持流式处理，把 `SoundTouch` 类接到 `AudioRecord`→`AudioTrack` 实时链路即可，在规划中（欢迎 PR）。

**机器音（robot）怎么做？**
机器音本质是环形调制/共振峰处理，不在 SoundTouch 的能力范围内，见[调参指南](docs/voice-tuning.md#机器音)中的说明。

## 许可证

- 本项目代码：[Apache License 2.0](LICENSE)
- 内置的 SoundTouch 库：[LGPL v2.1](voicechanger/src/main/cpp/soundtouch/COPYING.TXT)（以独立 `libsoundtouch.so` 动态链接方式使用；商用请遵循 LGPL 条款）
