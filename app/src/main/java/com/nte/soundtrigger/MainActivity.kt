package com.nte.soundtrigger

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nte.soundtrigger.audio.AudioCaptureService
import com.nte.soundtrigger.monitor.MonitorScreen
import com.nte.soundtrigger.monitor.MonitorViewModel
import com.nte.soundtrigger.trigger.KeyInjector
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val TAG = "NTE-Main"
    private lateinit var mpManager: MediaProjectionManager
    private lateinit var monitorVM: MonitorViewModel

    // 先请求通知权限再请求录屏权限
    private var pendingMediaProjection = false

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingMediaProjection) {
            pendingMediaProjection = false
            if (granted) {
                doRequestMediaProjection()
            } else {
                Toast.makeText(this, "通知权限被拒绝，运行中的服务将不显示通知", Toast.LENGTH_SHORT).show()
                doRequestMediaProjection()
            }
        }
    }

    private val mpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                AudioCaptureService.mediaProjection =
                    mpManager.getMediaProjection(result.resultCode, result.data!!)
                startCaptureService()
            } else {
                Toast.makeText(this, "需要音频捕获权限", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 初始化失败", e)
            Toast.makeText(this, "音频捕获初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        KeyInjector.init()

        mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        monitorVM = MonitorViewModel()

        checkShizuku()

        AudioCaptureService.onScoreUpdate = { d, c -> monitorVM.updateScores(d, c) }
        AudioCaptureService.onTriggerLog = { m -> monitorVM.addLog(m) }

        setContent {
            val isRunning by monitorVM.isRunning.collectAsState()
            val hasRoot by monitorVM.hasRoot.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D1A))
            ) {
                MonitorScreen(
                    viewModel = monitorVM,
                    modifier = Modifier.weight(1f)
                )
                ControlBar(
                    isRunning = isRunning,
                    hasRoot = hasRoot,
                    onStart = { requestMediaProjection() },
                    onStop = { stopCaptureService() },
                    onFixShizuku = { fixShizuku() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        monitorVM.setRunning(AudioCaptureService.isRunning)
        checkShizuku()
    }

    override fun onDestroy() {
        super.onDestroy()
        KeyInjector.destroy()
        AudioCaptureService.onScoreUpdate = null
        AudioCaptureService.onTriggerLog = null
    }

    // ── Shizuku ────────────────────────────

    private fun checkShizuku() {
        if (!KeyInjector.isAvailable()) {
            monitorVM.setRoot(false)
            monitorVM.addLog("⚠ Shizuku 未运行 — 请启动 Shizuku App")
            return
        }
        if (!KeyInjector.hasPermission()) {
            monitorVM.setRoot(false)
            monitorVM.addLog("⚠ Shizuku 未授权 — 请授予权限")
            return
        }
        monitorVM.setRoot(true)
        monitorVM.addLog("✓ Shizuku 已连接")
    }

    private fun fixShizuku() {
        if (!KeyInjector.isAvailable()) {
            Toast.makeText(this, "请先安装并启动 Shizuku App", Toast.LENGTH_LONG).show()
            return
        }
        if (!KeyInjector.hasPermission()) {
            KeyInjector.requestPermission(this, 42)
        }
    }

    // ── 权限与启动 ─────────────────────────

    private fun requestMediaProjection() {
        // Android 13+: 先请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                pendingMediaProjection = true
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        doRequestMediaProjection()
    }

    private fun doRequestMediaProjection() {
        mpLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startCaptureService() {
        try {
            if (!KeyInjector.isAvailable() || !KeyInjector.hasPermission()) {
                Toast.makeText(this, "⚠ Shizuku 未就绪 — 按键注入不可用", Toast.LENGTH_SHORT).show()
            }
            startForegroundService(Intent(this, AudioCaptureService::class.java))
            monitorVM.setRunning(true)
            monitorVM.addLog("音频捕获已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动捕获服务失败", e)
            monitorVM.addLog("✗ 启动失败: ${e.message}")
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCaptureService() {
        stopService(Intent(this, AudioCaptureService::class.java))
        monitorVM.setRunning(false)
        monitorVM.addLog("音频捕获已停止")
    }
}

// ── 控制栏 ────────────────────────────────

@Composable
private fun ControlBar(
    isRunning: Boolean,
    hasRoot: Boolean?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFixShizuku: () -> Unit
) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF00E5FF)

    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                hasRoot == null -> {
                    Text("检查 Shizuku 状态…", color = Color(0xFF8888AA), fontSize = 11.sp)
                }
                hasRoot == false -> {
                    TextButton(onClick = onFixShizuku) {
                        Text("⚠ 点击修复 Shizuku 连接", color = Color(0xFFFFAB00), fontSize = 12.sp)
                    }
                }
            }

            Button(
                onClick = { if (isRunning) onStop() else onStart() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFD32F2F) else accent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    if (isRunning) "■ 停止监听" else "▶ 开始监听",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
