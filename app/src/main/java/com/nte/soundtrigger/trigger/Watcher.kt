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
 *   1. 一个参考波形 (ref)
 *   2. 一个环形缓冲区 (累积流式音频)
 *   3. FFT 交叉相关匹配
 *   4. 阈值判定 + 冷却计时
 *
 * 架构对齐原始项目的 Listener.Watcher，完全重写。
 */
class Watcher(
    /** 名称 (用于日志) */
    val name: String,

    /** 参考波形样本 (已高通滤波 + RMS归一化) */
    private val ref: FloatArray,

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
    }

    // 参考波形 RMS 归一化缓存
    private val refNormalized: FloatArray

    // 缓冲区: 窗口长度 = max(参考长度/采样率, 0.5秒)
    private val winSec: Float
    private val buffer: RingBuffer

    // FFT 点数
    private val fftN: Int

    // 冷却
    private val cooldownMs = (cooldownSec * 1000).toLong()
    private var lastFireTime = 0L
    private var ready = true

    init {
        // RMS 归一化参考信号
        val refDouble = DoubleArray(ref.size) { ref[it].toDouble() }
        var sumSq = 0.0
        for (v in refDouble) sumSq += v * v
        val rms = sqrt(sumSq / ref.size + 1e-6)
        refNormalized = FloatArray(ref.size) { (refDouble[it] / rms).toFloat() }

        // 窗口长度 = max(参考长度, 0.5s)
        val refSec = ref.size.toFloat() / sampleRate
        winSec = max(refSec, 0.5f)

        val winSamples = (winSec * sampleRate).toInt()
        buffer = RingBuffer(winSamples)

        // FFT 长度 = 最小 2^k ≥ (窗口 + 参考 - 1)
        fftN = RealFFT.nextPowerOf2(winSamples + ref.size - 1)

        Log.i(TAG, "$name: winSec=$winSec, winSamples=$winSamples, fftN=$fftN, thresh=$threshold, cd=${cooldownSec}s")
    }

    // ── 公开 API ──────────────────────────

    /**
     * 喂入一帧音频数据
     *
     * @param frame 高通滤波后的音频帧 (Double)
     * @return 当前帧的匹配分数 (Float)
     */
    fun feed(frame: DoubleArray): Float {
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
            } else {
                ready = true
            }
        }

        return score
    }

    // ── 内部 ──────────────────────────────

    private fun fire(score: Float) {
        try {
            val t0 = System.nanoTime()
            action()
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000f
            val msg = "⚡ ${name}触发! score=%.4f (%.1fms)".format(score, elapsedMs)
            Log.i(TAG, msg)
            onFire?.invoke(msg)
        } catch (e: Exception) {
            Log.e(TAG, "$name 触发失败: ${e.message}", e)
        }
    }

    /** 重置缓冲区 */
    fun reset() {
        buffer.reset()
        ready = true
        lastFireTime = 0L
    }
}
