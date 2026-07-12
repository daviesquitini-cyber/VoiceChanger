# 音色调参指南

> 回答 [issue #1](https://github.com/neboyang/VoiceChanger/issues/1)：大叔、萝莉等音色的音调如何修改，以及背后的原理。

## 三个参数

`VoiceEffect` 暴露 SoundTouch 的三个核心参数，它们相互独立、可任意组合：

| 参数 | 含义 | 直觉 |
|---|---|---|
| `pitchSemiTones` | **音调**偏移，单位半音（semitone），不改变语速 | 正值→声音变尖（女声/童声方向），负值→变低沉（男声方向）。±12 = 一个八度 |
| `tempo` | **节拍**倍率，变速**不**变调 | 1.2 = 语速加快 20%，音调不变 |
| `rate` | **速率**倍率,变速**且**变调 | 磁带快放/慢放的效果，速度和音调一起变 |

**半音换算**：如果你习惯用"音调倍率"思考（如 1.x 版本的 `setPitch(2.1)`），换算公式为：

```
pitchSemiTones = 12 × log₂(倍率)
```

例如 2.1 倍 ≈ +12.8 半音；0.8 倍 ≈ −3.9 半音。反向：`倍率 = 2^(semitones/12)`。

## 原理速览

SoundTouch 属于时域算法（WSOLA 类）：

- **变速不变调（tempo）**：把音频切成短块，按重叠-相加（overlap-add）方式重排，通过波形相似度对齐拼接点，快放时丢弃部分块、慢放时重复部分块——时长变了，每个块内的波形（音调）没变。
- **变调不变速（pitch）**：先重采样改变音调（此时时长也变了），再用上面的 tempo 算法把时长拉回来。
- **rate** 则是纯重采样，最便宜，音调和时长同比变化。

因此 `pitch` 的处理成本 ≈ `rate` + `tempo`，大幅变调（超过 ±8 半音）后共振峰会整体平移，出现"花栗鼠/怪物"感——这是所有纯变调算法的共性，不是 bug。

## 常用音色配方

| 目标音色 | pitchSemiTones | tempo | rate | 说明 |
|---|---|---|---|---|
| 男声 → 女声 | +6 ~ +9 | 1.0 | 1.0 | +7 最常用；再配 tempo 1.05 更像 |
| 女声 → 男声 | −6 ~ −9 | 1.0 | 1.0 | 过低会闷，−12 以下失真明显 |
| 萝莉/童声 | +8 ~ +12 | 1.05 ~ 1.15 | 1.0 | 童声语速略快更真实 |
| 大叔 | −4 ~ −6 | 0.95 | 1.0 | 微降速增加"沉稳感" |
| 汤姆猫 | +10 ~ +12 | ≈1.0 | ≈1.0 | 经典夸张效果 |
| 花栗鼠 | +12 以上 | 1.2 | 1.0 | 刻意追求卡通感 |
| 巨人/怪物 | −10 ~ −16 | 0.85 | 1.0 | 配慢速更有压迫感 |
| 快放搞笑 | 0 | 1.0 | 1.5 ~ 2.0 | rate 一个参数即可 |

调参建议：

1. **先只动 pitch**，从 ±5 开始按 1 半音步进试听；
2. 音色确定后**微调 tempo**（±5% 内）修正语速带来的违和感；
3. `rate` 一般保持 1.0，只在要"整体快/慢放"效果时使用；
4. Demo app 的三个滑杆就是为快速试参设计的——选中预设后微调滑杆即可。

## 机器音

机器音（robot voice）的本质是**环形调制**（ring modulation，把信号乘以固定频率的正弦波）或声码器（vocoder），不属于变调/变速范畴，SoundTouch 做不了。如需实现，可在拿到 PCM 后自行做环形调制（每个采样乘 `sin(2π·f·t)`，f 取 30~80Hz 可得经典效果），再送入本库封装 WAV/M4A。

## 1.x 预设 → 2.x 参数对照

| 1.x（`VoiceType`） | 1.x 参数 | 2.x（`VoiceEffect`） |
|---|---|---|
| `VT_KITTY` | rate 1.2, +4 半音, tempo +2% | `KITTY` = (+4, 1.02, 1.2) |
| `VT_ROSE` | pitch 2.1 倍 | `ROSE` = (+12.8, 1.0, 1.0) |
| `VT_UNCLE` | pitch 0.8 倍 | `UNCLE` = (−3.9, 1.0, 1.0) |
| `VT_TOM` | +10 半音, rate −0.7%, tempo +0.5% | `TOM` = (+10, 1.005, 0.993) |

## 参考资料

- [SoundTouch 官网](https://www.surina.net/soundtouch/)：算法说明与 FAQ
- [SoundTouch 源码仓库](https://codeberg.org/soundtouch/soundtouch)
- [SoundTouch README：关于算法参数的技术说明](https://codeberg.org/soundtouch/soundtouch#readme)
- WSOLA 论文：Verhelst & Roelands, *An overlap-add technique based on waveform similarity (WSOLA) for high quality time-scale modification of speech*, ICASSP 1993
