package com.nte.soundtrigger.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 纯 Kotlin 基-2 复数 FFT + 实数交叉相关
 *
 * 数据格式: [re0, im0, re1, im1, ...]
 */
object RealFFT {

    // ── 公开 API ──────────────────────────

    /**
     * 两实数数组的归一化 FFT 交叉相关
     *
     * @return 归一化后最大相关系数 (0~1)
     */
    fun normalizedMaxCorr(signal: FloatArray, ref: FloatArray): Float {
        val n = nextPowerOf2(signal.size + ref.size - 1)

        // zero-pad → complex
        val sigC = FloatArray(2 * n)
        val refC = FloatArray(2 * n)

        for (i in signal.indices) sigC[2 * i] = signal[i]
        for (i in ref.indices) refC[2 * i] = ref[i]

        // FFT
        fft(sigC, inverse = false)
        fft(refC, inverse = false)

        // sig × conj(ref) in freq domain
        // (a + jb) * (c - jd) = (ac + bd) + j(bc - ad)
        for (k in 0 until n) {
            val idx = 2 * k
            val a = sigC[idx]; val b = sigC[idx + 1]
            val c = refC[idx]; val d = refC[idx + 1]

            sigC[idx]     = a * c + b * d       // Re = ac + bd
            sigC[idx + 1] = b * c - a * d       // Im = bc - ad
        }

        // IFFT
        fft(sigC, inverse = true)

        // 取实部的前 (signal.size + ref.size - 1) 项，找最大值
        val corrLen = signal.size + ref.size - 1
        var maxVal = 0f
        for (i in 0 until corrLen) {
            val v = sigC[2 * i]  // 逆 FFT 后实部 = 相关值
            if (v > maxVal) maxVal = v
        }

        // 归一化: / max(len(sig), len(ref))
        val norm = maxVal / maxOf(signal.size, ref.size)
        return norm
    }

    // ── 核心 FFT ──────────────────────────

    /**
     * 基-2 复数 FFT (原位)
     * @param data [re0, im0, re1, im1, ...] 长度 = 2 * N
     * @param inverse true = IFFT
     */
    fun fft(data: FloatArray, inverse: Boolean) {
        val nn = data.size / 2  // 复数点数
        require(nn and (nn - 1) == 0) { "N must be power of 2, got $nn" }

        // 位逆序置换
        var j = 0
        for (i in 0 until nn) {
            if (i < j) {
                val i2 = 2 * i; val j2 = 2 * j
                swap(data, i2, j2)
                swap(data, i2 + 1, j2 + 1)
            }
            var m = nn / 2
            while (m >= 1 && j >= m) { j -= m; m /= 2 }
            j += m
        }

        // 蝶形运算
        var step = 1
        while (step < nn) {
            val half = step
            step *= 2
            val angle = if (inverse) PI.toFloat() / half else -PI.toFloat() / half

            for (group in 0 until nn step step) {
                for (k in 0 until half) {
                    val wRe = cos(angle * k)
                    val wIm = sin(angle * k)

                    val a = 2 * (group + k)
                    val b = 2 * (group + k + half)

                    val tRe = wRe * data[b] - wIm * data[b + 1]
                    val tIm = wRe * data[b + 1] + wIm * data[b]

                    data[b] = data[a] - tRe
                    data[b + 1] = data[a + 1] - tIm
                    data[a] += tRe
                    data[a + 1] += tIm
                }
            }
        }

        // IFFT 缩放
        if (inverse) {
            val scale = 1f / nn
            for (i in data.indices) data[i] *= scale
        }
    }

    // ── 工具 ──────────────────────────────

    private fun swap(a: FloatArray, i: Int, j: Int) {
        val t = a[i]; a[i] = a[j]; a[j] = t
    }

    fun nextPowerOf2(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    /** RMS 归一化 */
    fun rmsNormalize(wf: FloatArray): FloatArray {
        var sumSq = 0f
        for (v in wf) sumSq += v * v
        val rms = kotlin.math.sqrt(sumSq / wf.size + 1e-6f)
        return FloatArray(wf.size) { wf[it] / rms }
    }
}
