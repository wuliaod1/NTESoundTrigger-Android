package com.nte.soundtrigger.trigger

import android.util.Log
import com.nte.soundtrigger.dsp.FilterBank
import com.nte.soundtrigger.dsp.RealFFT
import com.nte.soundtrigger.dsp.RingBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 音效匹配检测器
 *
 * 每个 Watcher 实例管理：
 *   1. 一个参考波形 (ref) — 过长自动截断到 MAX_REF_SAMPLES
 *   2. 一个环形缓冲区 (累积流式音频)
 *   3. FFT 交叉相关匹配 (每帧运行)
 *   4. 阈值判定 + 冷却计时
 */
class Watcher(
    /** 名称 (用于日志) */
    val name: String,

    /** 参考波形样本 (已高通滤波 + RMS归一化) */
    ref: FloatArray,

    /** 触发动作 */
    private val action: () -> Unit,

    /** 匹配阈值 */
    private val threshold: Float,

    /** 冷却时间 (秒) */
    private val cooldownSec: Float,

    /** 采样率 */
    private val sampleRate: Int = 32000,

    /** 是否允许连续触发 */
    private val allowRepeat: Boolean = false,

    /** 触发回调 → UI 日志 */
    private val onFire: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "Watcher"
        /** 参考样本最大长度 (点数) — 限制 FFT 尺寸，保证实时性 */
        const val MAX_REF_SAMPLES = 16000  // 0.5s @ 32kHz
    }

    // 截断过长参考
    private val refTrimmed: FloatArray =
        if (ref.size > MAX_REF_SAMPLES) ref.copyOf(MAX_REF_SAMPLES) else ref

    // 参考波形 RMS 归一化
    private val refNormalized: FloatArray

    // 缓冲区: 窗口长度 = max(参考长度/采样率, 0.3秒)
    private val winSec: Float
    private val buffer: RingBuffer

    // FFT 点数
    private val fftN: Int

    // 冷却
    private val cooldownMs = (cooldownSec * 1000).toLong()
    private var lastFireTime = 0L
    private var ready = true

    init {
        if (ref.size > MAX_REF_SAMPLES) {
            Log.i(TAG, "$name: 参考样本截断 ${ref.size} → $MAX_REF_SAMPLES 点")
        }

        // RMS 归一化参考信号
        val refDouble = DoubleArray(refTrimmed.size) { refTrimmed[it].toDouble() }
        var sumSq = 0.0
        for (v in refDouble) sumSq += v * v
        val rms = sqrt(sumSq / refTrimmed.size + 1e-6)
        refNormalized = FloatArray(refTrimmed.size) { (refDouble[it] / rms).toFloat() }

        // 窗口长度 = max(参考时长, 0.3秒)
        val refSec = refTrimmed.size.toFloat() / sampleRate
        winSec = max(refSec, 0.3f)
        val winSamples = (winSec * sampleRate).toInt()
        buffer = RingBuffer(winSamples)

        // FFT 长度 = 最小 2^k ≥ (窗口 + 参考 - 1)
        fftN = RealFFT.nextPowerOf2(winSamples + refTrimmed.size - 1)

        Log.i(TAG, "$name: ref=${refTrimmed.size}, win=${winSamples}, fftN=$fftN, thresh=$threshold, cd=${cooldownSec}s")
    }

    // ── 公开 API ──────────────────────────

    /**
     * 喂入一帧音频数据，每帧运行 FFT 匹配
     *
     * @param frame 高通滤波后的音频帧 (Double)
     * @return 当前帧的匹配分数 (Float)
     */
    fun feed(frame: DoubleArray): Float {
        // 始终写入缓冲 — 保证音频不丢
        buffer.write(frame)

        if (buffer.size < refNormalized.size) return 0f

        // 从缓冲区读取完整窗口
        val window = buffer.read()

        // RMS 归一化
        var sumSq = 0.0
        for (v in window) sumSq += v * v
        val rms = sqrt(sumSq / window.size + 1e-6)
        val windowNorm = FloatArray(window.size) { (window[it] / rms).toFloat() }

        // FFT 交叉相关
        val score = RealFFT.normalizedMaxCorr(windowNorm, refNormalized)

        // 阈值判断
        val now = System.currentTimeMillis()
        if (now - lastFireTime >= cooldownMs) {
            if (score >= threshold && (ready || allowRepeat)) {
                fire(score)
                lastFireTime = now
                ready = false
            }
        } else {
            // 冷却中 — 持续低于阈值时恢复就绪
            if (score < threshold * 0.5f) ready = true
        }

        return score
    }

    /** 重置缓冲区 */
    fun reset() {
        buffer.reset()
        ready = true
        lastFireTime = 0L
    }

    // ── 内部 ──────────────────────────────

    private fun fire(score: Float) {
        val latencyMs = (winSec * 500).toLong()  // 约半窗口延迟
        val msg = "⚡ $name 触发! score=%.4f (${latencyMs}ms)".format(score)
        Log.i(TAG, msg)
        onFire?.invoke(msg)
        action()
    }
}
