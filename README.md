# NTESoundTrigger-Android

基于《异环》游戏音效波形识别的自动闪避/反击触发器 — Android 移植版（完全重写，不依赖原项目代码）。

> ⚠️ **当前状态**: 基础框架功能正常（音频捕获→滤波→FFT匹配→按键注入），但**音效识别成功率较低**。
> **Shizuku 免 Root 键码注入尚未实现**（`UserService` / `injectInputEvent` 待做），当前临时用 `su` 注入占位。
> 详情见下方 [已知问题](#已知问题--求助)，欢迎 PR / Issue 指教。

## 功能

- 🎯 **实时音频捕获**: AudioPlaybackCapture API (Android 10+) 捕获系统内部游戏音频
- 🌊 **高通滤波**: 4 阶 Butterworth 滤波器，去除低频噪声
- 🔁 **FFT 交叉相关**: 频域匹配，精准识别预设的闪避/反击音效
- 🎮 **手柄按键注入**: 当前 `su` 占位；Shizuku 免 Root 方案（`UserService` / `injectInputEvent`）待实现
- ⏱️ **独立冷却**: 闪避 0.8s / 反击 1.5s 冷却，杜绝重复触发
- 📊 **实时监控**: Compose Canvas 波形图 + 峰值分数 + 触发日志

## 系统要求

| 要求 | 说明 |
|------|------|
| Android 版本 | ≥ 10 (API 29) — AudioPlaybackCapture |
| Shizuku | **必需** — 跨应用注入手柄按键（免 Root，使用方法详见下方已知问题） |
| 音频捕获权限 | 首次启动需授权 MediaProjection |
| 游戏支持 | 游戏需允许音频捕获（未设置 `ALLOW_CAPTURE_BY_SYSTEM` 标记） |

> 💡 Shizuku 安装: 从 Google Play 或 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 下载，通过无线调试或 ADB 启动即可，**完全不需要 Root/解锁**。

## 架构

```
┌───────────────────────────────────────────────────────┐
│  AudioPlaybackCapture (系统内部音频)                    │
│         ↓                                             │
│  FilterBank (高通滤波) — 逐帧在线处理                    │
│         ↓                                             │
│  ┌──────────────┬──────────────┐                      │
│  │ Watcher 闪避  │ Watcher 反击  │                     │
│  │ RingBuffer   │ RingBuffer   │                     │
│  │ → RMS 归一化  │ → RMS 归一化  │                     │
│  │ → FFT 交叉相关│ → FFT 交叉相关│                     │
│  │ → 阈值 + 冷却 │ → 阈值 + 冷却 │                     │
│  └──────┬───────┴──────┬───────┘                      │
│         ↓              ↓                              │
│    KeyInjector     KeyInjector                        │
│    pressRB()       pressX()                           │
│         ↓              ↓                              │
│  Shizuku input keyevent KEYCODE_BUTTON_R1 (103)       │
│  Shizuku input keyevent KEYCODE_BUTTON_X  (99)        │
└───────────────────────────────────────────────────────┘
```

## Quick Start

```bash
git clone https://github.com/wuliaod1/NTESoundTrigger-Android.git
cd NTESoundTrigger-Android
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

或直接从 [GitHub Actions](https://github.com/wuliaod1/NTESoundTrigger-Android/actions) 下载最新构建产物。

## 使用

1. 安装 [Shizuku](https://github.com/RikkaApps/Shizuku/releases) 并启动（无线调试或 ADB）
2. 安装本应用 APK
3. 打开应用，授权 Shizuku 权限
4. 点击「开始监听」，授权录屏/音频捕获权限
5. 启动游戏，音效命中时自动触发对应按键

## 当前配置

| 参数 | 值 | 说明 |
|------|-----|------|
| `SAMPLE_RATE` | 32000 Hz | 音频采样率 |
| `FRAME_SEC` | 0.08s | 处理帧长 (12.5 FPS) |
| `HP_ORDER` | 4 | Butterworth 高通阶数 |
| `HP_CUTOFF` | 1000 Hz | 截止频率 |
| `DODGE_THRESH` | 0.08 | 闪避匹配阈值 |
| `COUNTER_THRESH` | 0.07 | 反击匹配阈值 |
| `DODGE_COOLDOWN` | 0.8s | 闪避冷却 |
| `COUNTER_COOLDOWN` | 1.5s | 反击冷却 |
| `MAX_REF_SAMPLES` | 16000 | 参考样本最大长度 (限制 FFT 尺寸) |

## 性能优化历程

| 阶段 | 方案 | 效果 |
|------|------|------|
| v1 | 两路 FFT 每帧全跑（32K + 65K 点） | 手机 CPU 根本跟不上，主循环严重阻塞 |
| v2 | 闪避每 2 帧 / 反击每 4 帧跑 FFT | 性能缓解但识别率太低，大量音效漏过 |
| v3 | 反击样本截断 16000 点 → FFT 65K→32K，两路每帧全跑 | 目前状态，识别率仍不理想 |

**核心瓶颈**: 频域交叉相关的 32K 点复数 FFT 在移动端 ARM 处理器上运行开销过大（约 30-50ms/次），两路各一次 ≈ 60-100ms，刚好压在 80ms 帧预算的边缘。

## 已知问题 & 求助

### 1. 音效匹配识别率低 ⭐ 急需优化

**现象**: 即使降低阈值、提高 FFT 频率，匹配分数仍偏低（峰值 ~0.07-0.12），与噪声区分度不足，导致触发率低或误触发多。

**可能原因**:
- 参考样本来自 PC 端录制，与 Android AudioPlaybackCapture 捕获的音频在采样率转换、音量、EQ 上存在差异
- 手机上游戏音频可能经过额外处理（动态范围压缩、虚拟环绕声等），导致波形畸变
- 32K FFT 每帧运行在移动端已是实时性极限，无法再做更精细的频域分析

**欢迎指教**:
- 是否有更轻量的音效匹配算法替代方案？（MFCC + DTW / 小波变换 / 互相关峰值检测 等）
- 如何在 Android 端高效实现低延迟的频域匹配？
- 音频捕获的 `USAGE` 白名单是否需要在不同设备/ROM 上动态调整？

### 2. Shizuku 免 Root 键码注入（待实现）

**现状**: 当前使用 `Runtime.exec("su -c input keyevent ...")` 作为临时占位。Shizuku 完全有能力免 Root 实现，只是还没做。

**待实现方案**:
- **UserService** — 在 Shizuku 进程上下文中运行后台服务，拥有 shell 级权限，可直接执行 `input keyevent`
- **injectInputEvent** — 通过 Shizuku 获取 `INJECT_EVENTS` 权限，直接注入按键事件，延迟更低

参考:
- [Shizuku UserService 文档](https://github.com/RikkaApps/Shizuku-API#user-service)
- [injectInputEvent 使用条件](https://developer.android.com/reference/android/hardware/input/InputManager#injectInputEvent(android.view.InputEvent,%20int))

### 3. 仅 Debug 构建，缺少 Release 签名

如需发布到应用商店或分享给非开发者，需要配置 Gradle signing config 和 keystore。

## 贡献

任何形式的贡献都欢迎：
- 🐛 提 Issue 报告问题或分享使用体验
- 💡 讨论算法优化方案
- 🔧 提 PR 改进代码

## CI/CD

每次 push to `master` 自动通过 GitHub Actions (`ubuntu-latest`) 编译 Debug APK，产物可在 Actions Artifacts 中下载。

## 技术栈

- **语言**: Kotlin 100%
- **UI**: Jetpack Compose
- **音频**: Android AudioRecord + AudioPlaybackCapture (API 29)
- **DSP**: 自实现 Butterworth 滤波器 + 标准复数 FFT
- **提权**: Shizuku API 13.1.5
- **构建**: AGP 8.2.2 + Kotlin 1.9.22

## 许可

MIT

## 免责声明

本项目仅供学习交流使用。使用本项目所产生的一切问题与项目本身及开发者无关。
