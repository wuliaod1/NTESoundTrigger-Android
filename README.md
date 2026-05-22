# NTESoundTrigger-Android

基于《异环》游戏音效波形识别的自动闪避/反击触发器 — Android 移植版。

## 功能

- 🎯 **实时音频捕获**: 使用 AudioPlaybackCapture API (Android 10+) 捕获系统内部游戏音频
- 🌊 **高通滤波**: 4 阶 Butterworth 滤波器，去除低频噪声
- 🔁 **FFT 交叉相关**: 频域匹配，精准识别预设的闪避/反击音效
- 🎮 **手柄按键注入**: 检测到音效后自动发送 RB (闪避) / X (反击) 键码
- ⏱️ **独立冷却**: 闪避 0.5s / 反击 1.2s 冷却，杜绝重复触发
- 📊 **实时监控**: Compose Canvas 波形图 + 触发日志

## 系统要求

| 要求 | 说明 |
|------|------|
| Android 版本 | ≥ 10 (API 29) — AudioPlaybackCapture |
| Root 权限 | **必需** — 跨应用注入手柄按键 |
| 音频捕获权限 | 首次启动需要授权 MediaProjection |

## 架构

```
┌─────────────────────────────────────────────────┐
│  AudioPlaybackCapture (系统内部音频)              │
│         ↓                                       │
│  FilterBank (高通滤波) — 逐帧在线处理              │
│         ↓                                       │
│  ┌──────────────┬──────────────┐                │
│  │ Watcher 闪避  │ Watcher 反击  │               │
│  │ RingBuffer   │ RingBuffer   │               │
│  │ → RMS 归一化  │ → RMS 归一化  │               │
│  │ → FFT 交叉相关│ → FFT 交叉相关│               │
│  │ → 阈值 + 冷却 │ → 阈值 + 冷却 │               │
│  └──────┬───────┴──────┬───────┘                │
│         ↓              ↓                        │
│    KeyInjector     KeyInjector                  │
│    pressRB()       pressX()                     │
│         ↓              ↓                        │
│    su input keyevent 103    su input keyevent 99│
└─────────────────────────────────────────────────┘
```

## 构建

```bash
# Android Studio 打开项目目录
# 或命令行:
./gradlew assembleDebug
```

## 使用

1. 将闪避/反击的 WAV 音效文件放入 `app/src/main/assets/` (命名为 `dodge.wav` / `counter.wav`)
2. 安装 APK，启动应用
3. 首次点击"开始监听"，授权 MediaProjection 权限
4. 系统音频将被实时监听，命中音效后自动发送手柄按键

## 配置

编辑 `Config.kt` 调整参数:

```kotlin
SAMPLE_RATE = 32000      // 采样率
DODGE_THRESH = 0.13f     // 闪避匹配阈值
COUNTER_THRESH = 0.10f   // 反击匹配阈值
DODGE_COOLDOWN = 0.5f    // 闪避冷却 (秒)
COUNTER_COOLDOWN = 1.2f  // 反击冷却 (秒)
```

## 许可

本项目为原 [NTESoundTrigger](https://github.com/Wattls/NTESoundTrigger) 的 Android 移植版本，架构与代码完全重写。

GPLv3

## 免责声明

使用本项目所产生的一切问题与项目本身及开发者无关。本项目仅供学习交流使用。
