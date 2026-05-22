package com.nte.soundtrigger.util

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 轻量 WAV 文件解析器
 *
 * 支持: 16-bit PCM, 单声道/立体声
 * 输出: 归一化 FloatArray (-1.0 ~ 1.0)
 */
object WavLoader {

    data class WavData(
        val samples: FloatArray,   // 归一化浮点采样
        val sampleRate: Int,        // 原始采样率
        val channels: Int           // 声道数
    )

    fun load(input: InputStream): WavData {
        val bytes = input.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF 头
        val riff = CharArray(4) { buf.get().toInt().toChar() }
        require(riff.concatToString() == "RIFF") { "Not a RIFF file" }
        buf.getInt() // file size

        val wave = CharArray(4) { buf.get().toInt().toChar() }
        require(wave.concatToString() == "WAVE") { "Not a WAVE file" }

        // 块解析
        var audioFormat = 0
        var numChannels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataBytes: ByteArray? = null

        while (buf.hasRemaining()) {
            val chunkId = CharArray(4) { buf.get().toInt().toChar() }.concatToString()
            val chunkSize = buf.getInt()

            when (chunkId) {
                "fmt " -> {
                    audioFormat = buf.getShort().toInt() and 0xFFFF
                    numChannels = buf.getShort().toInt() and 0xFFFF
                    sampleRate = buf.getInt()
                    buf.getInt() // byte rate
                    buf.getShort() // block align
                    bitsPerSample = buf.getShort().toInt() and 0xFFFF

                    // 跳过额外 fmt 头
                    val fmtRead = 16
                    if (chunkSize > fmtRead) {
                        buf.position(buf.position() + (chunkSize - fmtRead))
                    }
                }
                "data" -> {
                    dataBytes = ByteArray(chunkSize)
                    buf.get(dataBytes)
                }
                else -> {
                    buf.position(buf.position() + chunkSize)
                }
            }
        }

        require(audioFormat == 1) { "Only PCM format supported, got $audioFormat" }
        require(bitsPerSample == 16) { "Only 16-bit PCM supported, got $bitsPerSample" }
        require(dataBytes != null) { "No data chunk found" }

        // PCM → Float
        val dataBuf = ByteBuffer.wrap(dataBytes!!).order(ByteOrder.LITTLE_ENDIAN)
        val totalSamples = dataBytes!!.size / 2
        val samples = FloatArray(totalSamples) {
            dataBuf.getShort().toFloat() / 32768f
        }

        // 立体声 → 单声道 (取平均)
        return if (numChannels == 2) {
            val monoSamples = FloatArray(totalSamples / 2) { i ->
                (samples[2 * i] + samples[2 * i + 1]) * 0.5f
            }
            WavData(monoSamples, sampleRate, numChannels)
        } else {
            WavData(samples, sampleRate, numChannels)
        }
    }
}
