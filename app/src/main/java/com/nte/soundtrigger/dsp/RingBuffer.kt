package com.nte.soundtrigger.dsp

/**
 * 环形缓冲区 — O(1) 写入，支持零拷贝顺序读取
 *
 * 用于累积连续音频帧，为 FFT 交叉相关提供固定长度的滑动窗口。
 */
class RingBuffer(private val capacity: Int) {

    private val buf = DoubleArray(capacity)
    private var pos = 0
    private var filled = 0

    val size: Int get() = minOf(capacity, filled)

    /**
     * 写入一帧数据。
     * 如果帧长度 ≥ capacity，直接用帧末尾覆盖整个缓冲区。
     */
    fun write(frame: DoubleArray) {
        val n = frame.size
        if (n >= capacity) {
            frame.copyInto(buf, 0, frame.size - capacity, frame.size)
            pos = 0
            filled = capacity
            return
        }

        val end = pos + n
        if (end <= capacity) {
            frame.copyInto(buf, pos, 0, n)
        } else {
            val part1 = capacity - pos
            frame.copyInto(buf, pos, 0, part1)
            frame.copyInto(buf, 0, part1, n)
        }
        pos = (pos + n) % capacity
        filled = minOf(filled + n, capacity)
    }

    /**
     * 按时间顺序取出缓冲区中的全部数据
     * 返回容量为 size 的新数组
     */
    fun read(): DoubleArray {
        if (filled == 0) return DoubleArray(0)
        val result = DoubleArray(size)
        if (filled < capacity) {
            buf.copyInto(result, 0, 0, filled)
        } else if (pos == 0) {
            buf.copyInto(result, 0, 0, capacity)
        } else {
            val part1 = capacity - pos
            buf.copyInto(result, 0, pos, capacity)
            buf.copyInto(result, part1, 0, pos)
        }
        return result
    }

    fun reset() {
        pos = 0
        filled = 0
    }
}
