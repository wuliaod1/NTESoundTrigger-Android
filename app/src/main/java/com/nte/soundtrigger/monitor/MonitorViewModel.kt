package com.nte.soundtrigger.monitor

import androidx.lifecycle.ViewModel
import com.nte.soundtrigger.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MonitorViewModel : ViewModel() {

    // 波形数据
    private val maxSize = Config.MONITOR_SAMPLES

    private val _dodgeHistory = MutableStateFlow(FloatArray(maxSize))
    val dodgeHistory: StateFlow<FloatArray> = _dodgeHistory.asStateFlow()

    private val _counterHistory = MutableStateFlow(FloatArray(maxSize))
    val counterHistory: StateFlow<FloatArray> = _counterHistory.asStateFlow()

    // 触发日志
    private val _logs = MutableStateFlow(listOf<String>())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // 最新分数
    private val _dodgeScore = MutableStateFlow(0f)
    val dodgeScore: StateFlow<Float> = _dodgeScore.asStateFlow()

    private val _counterScore = MutableStateFlow(0f)
    val counterScore: StateFlow<Float> = _counterScore.asStateFlow()

    // 服务状态
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Root 状态
    private val _hasRoot = MutableStateFlow<Boolean?>(null)
    val hasRoot: StateFlow<Boolean?> = _hasRoot.asStateFlow()

    // ── 公开方法 ──────────────────────────

    fun updateScores(dodge: Float, counter: Float) {
        _dodgeScore.value = dodge
        _counterScore.value = counter

        // 追加到历史
        val d = _dodgeHistory.value.copyOf()
        val c = _counterHistory.value.copyOf()
        System.arraycopy(d, 1, d, 0, maxSize - 1)
        System.arraycopy(c, 1, c, 0, maxSize - 1)
        d[maxSize - 1] = dodge
        c[maxSize - 1] = counter
        _dodgeHistory.value = d
        _counterHistory.value = c
    }

    fun addLog(msg: String) {
        _logs.value = (_logs.value + msg).takeLast(30)
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setRoot(has: Boolean) {
        _hasRoot.value = has
    }
}
