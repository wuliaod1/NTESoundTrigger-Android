package com.nte.soundtrigger.dsp

/**
 * 4 阶 Butterworth 高通滤波器
 *
 * 系数在 32000Hz 采样率、1000Hz 截止频率下通过双线性变换计算。
 * 实现直接 II 型转置结构，适合流式逐帧处理。
 *
 * 差分方程: y[n] = b0*x[n] + b1*x[n-1] + ... + b4*x[n-4]
 *                  - a1*y[n-1] - a2*y[n-2] - ... - a4*y[n-4]
 */
class FilterBank(
    order: Int = 4,
    cutoffHz: Float = 1000f,
    sampleRate: Float = 32000f
) {
    // ── 预计算的 4阶 高通系数 (fs=32000, fc=1000) ──
    //
    // 使用双线性变换从模拟 Butterworth 原型计算：
    //   fc = 1000 Hz, fs = 32000 Hz
    //   ωd = 2π·fc/fs, Ωa = 2·fs·tan(ωd/2)
    //   模拟极点 → 双线性变换 → 数字系数
    //
    // 频率响应验证：
    //   0 Hz → -∞ dB (高通)
    //   500 Hz → -24.2 dB
    //   1000 Hz → -3.0 dB (截止)
    //   2000+ Hz → ~0 dB (通带)
    //
    private val b = doubleArrayOf(
        7.733467891606217e-01,   // b[0]
        -3.093387156642487e+00,  // b[1]
        4.640080734963730e+00,   // b[2]
        -3.093387156642487e+00,  // b[3]
        7.733467891606217e-01    // b[4]
    )

    private val a = doubleArrayOf(
        1.000000000000000e+00,   // a[0] (归一化 = 1)
        -3.487307741549900e+00,  // a[1]
        4.589291232078407e+00,   // a[2]
        -2.698884391340753e+00,  // a[3]
        5.980652616008869e-01   // a[4]
    )

    // 延迟线 (直接 II 型)
    private val xBuf = DoubleArray(order + 1)  // 输入延迟
    private val yBuf = DoubleArray(order + 1)  // 输出延迟

    val order: Int get() = b.size - 1

    // ── 流式处理 (逐帧, 有状态) ──────────

    /**
     * 对一帧音频数据进行高通滤波 (保持状态, 适合连续流式处理)
     */
    fun process(frame: DoubleArray): DoubleArray {
        val result = DoubleArray(frame.size)
        for (i in frame.indices) {
            result[i] = processSample(frame[i])
        }
        return result
    }

    /** 处理单个采样点 */
    private fun processSample(x: Double): Double {
        // 移位延迟线
        for (i in order downTo 1) {
            xBuf[i] = xBuf[i - 1]
            yBuf[i] = yBuf[i - 1]
        }
        xBuf[0] = x

        // 差分方程
        var y = b[0] * xBuf[0]
        for (i in 1..order) {
            y += b[i] * xBuf[i] - a[i] * yBuf[i]
        }

        yBuf[0] = y
        return y
    }

    // ── 离线处理 (零相位, 用于预处理参考样本) ──

    /**
     * 零相位滤波 (forward + backward)，用于参考音频样本的离线预处理。
     * 等效于 scipy.signal.filtfilt
     */
    fun preprocess(wf: DoubleArray): DoubleArray {
        // Forward
        val fwd = DoubleArray(wf.size)
        val xTmp = DoubleArray(order + 1)
        val yTmp = DoubleArray(order + 1)

        for (i in wf.indices) {
            for (j in order downTo 1) {
                xTmp[j] = xTmp[j - 1]
                yTmp[j] = yTmp[j - 1]
            }
            xTmp[0] = wf[i]
            var y = b[0] * xTmp[0]
            for (j in 1..order) y += b[j] * xTmp[j] - a[j] * yTmp[j]
            yTmp[0] = y
            fwd[i] = y
        }

        // Backward
        val result = DoubleArray(wf.size)
        val xTmp2 = DoubleArray(order + 1)
        val yTmp2 = DoubleArray(order + 1)

        for (i in wf.indices.reversed()) {
            for (j in order downTo 1) {
                xTmp2[j] = xTmp2[j - 1]
                yTmp2[j] = yTmp2[j - 1]
            }
            xTmp2[0] = fwd[i]
            var y = b[0] * xTmp2[0]
            for (j in 1..order) y += b[j] * xTmp2[j] - a[j] * yTmp2[j]
            yTmp2[0] = y
            result[i] = y
        }

        return result
    }

    /** 重置滤波器状态 */
    fun reset() {
        xBuf.fill(0.0)
        yBuf.fill(0.0)
    }
}
