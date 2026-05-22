package com.nte.soundtrigger

/**
 * 全局配置 — 对应原始项目的 Config.py
 * 所有参数集中管理，运行时可在设置界面调整
 */
object Config {
    // ── 构建信息 ─────────────────────────
    const val VERSION = "0.2-alpha"
    const val SAMPLE_RATE = 32000        // 采样率 Hz
    const val CHANNELS = 2               // 输入声道数
    const val FRAME_SEC = 0.08f          // 每次处理帧长 (秒)

    // ── 高通滤波 ─────────────────────────
    const val HP_ORDER = 4               // Butterworth 阶数
    const val HP_CUTOFF = 1000f          // 截止频率 Hz

    // ── 闪避 ─────────────────────────────
    const val DODGE_THRESH = 0.08f       // 匹配阈值 (原 0.13，降至噪声线上方)
    const val DODGE_COOLDOWN = 0.8f       // 冷却秒数 (防止连续误触发)

    // ── 反击 ─────────────────────────────
    const val COUNTER_THRESH = 0.07f     // (原 0.10)
    const val COUNTER_COOLDOWN = 1.5f

    // ── 整体 ─────────────────────────────
    const val ALLOW_REPEAT = false        // 是否允许连续触发

    // ── 监控 ─────────────────────────────
    const val MONITOR_SEC = 5             // 波形图历史时长 (秒)

    // ── 参考样本 (assets 目录中的文件名) ──
    // 原始 .npy 已转换为 float32 raw 格式，采样率 32000，已预滤波
    const val DODGE_RAW = "闪避波形.raw"
    const val COUNTER_RAW = "承轨反击波形.raw"

    // ── 手柄键码 ─────────────────────────
    // RB = KEYCODE_BUTTON_R1 (103)
    // X  = KEYCODE_BUTTON_X  (99)
    const val KEYCODE_DODGE = 103         // RB
    const val KEYCODE_COUNTER = 99        // X

    /** 每次处理的采样点数 */
    val FRAME_SAMPLES: Int
        get() = (SAMPLE_RATE * FRAME_SEC).toInt()

    /** 监控窗口缓存采样点数 */
    val MONITOR_SAMPLES: Int
        get() = (MONITOR_SEC / FRAME_SEC).toInt()
}
