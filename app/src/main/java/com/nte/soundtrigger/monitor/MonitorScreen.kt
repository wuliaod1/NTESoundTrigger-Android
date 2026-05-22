package com.nte.soundtrigger.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nte.soundtrigger.Config
import kotlin.math.max

// ── 颜色方案 ──────────────────────────────
val Bg = Color(0xFF0D0D1A)
private val Surface = Color(0xFF1A1A2E)
private val Dodge = Color(0xFF00E5FF)
private val Counter = Color(0xFFFF5252)
private val TextDim = Color(0xFF8888AA)
private val TextBright = Color(0xFFE0E0FF)
private val GridLine = Color(0xFF2A2A3E)

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier
) {
    val dodge by viewModel.dodgeHistory.collectAsState()
    val counter by viewModel.counterHistory.collectAsState()
    val dodgeScore by viewModel.dodgeScore.collectAsState()
    val counterScore by viewModel.counterScore.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()
    val rawLevel by viewModel.rawLevel.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp)
    ) {
        // ── 标题栏 ─────────────────────────
        Text(
            "NTE Sound Trigger v${Config.VERSION}",
            color = TextBright,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))

        // 状态指示
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isRunning) Color(0xFF00C853) else Color(0xFF616161)
            ) {
                Text(
                    if (isRunning) "● 运行中" else "○ 已停止",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            if (hasRoot != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (hasRoot == true) Color(0xFF00C853) else Color(0xFFD32F2F)
                ) {
                    Text(
                        if (hasRoot == true) "Shizuku ✓" else "Shizuku ✗",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            if (isRunning) {
                val rawColor = when {
                    rawLevel < 0.001f -> Color(0xFFD32F2F)  // 静音 = 红色警告
                    rawLevel < 0.01f -> Color(0xFFFFAB00)   // 微弱
                    else -> Color(0xFF00C853)                // 正常
                }
                val rawText = java.lang.String.format(java.util.Locale.US, "%.4f", rawLevel)
                Text(
                    "\uD83D\uDD0A $rawText",
                    color = rawColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 当前分数 ───────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScoreCard("闪避 RB", dodgeScore, Dodge)
            ScoreCard("反击 X", counterScore, Counter)
        }

        Spacer(Modifier.height(12.dp))

        // ── 波形图 ─────────────────────────
        WaveformChart(
            dodge = dodge,
            counter = counter,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Spacer(Modifier.height(12.dp))

        // ── 触发日志 ───────────────────────
        Text(
            "触发日志",
            color = TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))

        val listState = rememberLazyListState()

        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Surface, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(logs.reversed()) { log ->
                val color = when {
                    log.contains("闪避") -> Dodge
                    log.contains("反击") -> Counter
                    else -> TextDim
                }
                Text(
                    log,
                    color = color,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

// ── 分数卡片 ──────────────────────────────

@Composable
private fun ScoreCard(label: String, score: Float, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = TextDim, fontSize = 11.sp)
            Text(
                String.format("%.5f", score),
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── 波形图 ────────────────────────────────

@Composable
private fun WaveformChart(
    dodge: FloatArray,
    counter: FloatArray,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            val n = dodge.size
            if (n < 2) return@Canvas

            // 阈值线
            val maxY = max(dodge.maxOrNull() ?: 0f, counter.maxOrNull() ?: 0f)
            val yMax = max(maxY * 1.5f, 0.3f)

            // 网格
            for (i in 1..3) {
                val y = h * (1f - i / 4f)
                drawLine(GridLine, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
            }

            // 闪避曲线
            val dodgePath = Path()
            dodgePath.moveTo(0f, h)
            for (i in 0 until n) {
                val x = w * i / (n - 1)
                val y = h * (1f - dodge[i] / yMax)
                dodgePath.lineTo(x, y)
            }
            drawPath(dodgePath, Dodge, style = Stroke(width = 3f))

            // 反击曲线
            val counterPath = Path()
            counterPath.moveTo(0f, h)
            for (i in 0 until n) {
                val x = w * i / (n - 1)
                val y = h * (1f - counter[i] / yMax)
                counterPath.lineTo(x, y)
            }
            drawPath(counterPath, Counter, style = Stroke(width = 3f))
        }
    }
}
