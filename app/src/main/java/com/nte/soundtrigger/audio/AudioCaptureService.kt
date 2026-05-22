package com.nte.soundtrigger.audio

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nte.soundtrigger.Config
import com.nte.soundtrigger.MainActivity
import com.nte.soundtrigger.dsp.FilterBank
import com.nte.soundtrigger.trigger.KeyInjector
import com.nte.soundtrigger.trigger.Watcher
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "NTE"
        private const val CHANNEL_ID = "nte_capture"
        private const val NOTIFY_ID = 1

        const val ACTION_SETUP = "com.nte.soundtrigger.SETUP"
        const val ACTION_START_CAPTURE = "com.nte.soundtrigger.START_CAPTURE"

        @Volatile var isRunning = false; private set
        @Volatile var dodgeScore = 0f; private set
        @Volatile var counterScore = 0f; private set

        var onTriggerLog: ((String) -> Unit)? = null
        var onScoreUpdate: ((Float, Float) -> Unit)? = null

        /**
         * 由 MainActivity 在 MediaProjection 授权后设置。
         * Android 14+ 要求前台服务必须先运行才能调用 getMediaProjection()，
         * 因此 MainActivity 先启动服务（ACTION_SETUP），再请求权限，最后设置此字段并发送 ACTION_START_CAPTURE。
         */
        @Volatile var mediaProjection: MediaProjection? = null
    }

    private var audioRecord: AudioRecord? = null
    private var capturing = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var fb: FilterBank
    private var dodgeWatcher: Watcher? = null
    private var counterWatcher: Watcher? = null

    override fun onCreate() {
        super.onCreate()
        makeChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SETUP

        when (action) {
            ACTION_SETUP -> {
                // 只启动前台服务，不开始捕获
                startForegroundCompat(NOTIFY_ID, buildNotify())
                Log.i(TAG, "前台服务已启动，等待 MediaProjection 授权...")
            }
            ACTION_START_CAPTURE -> {
                startForegroundCompat(NOTIFY_ID, buildNotify())
                scope.launch { runCapture() }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    // ────────────────────────────────────────
    //  捕获循环
    // ────────────────────────────────────────

    private suspend fun runCapture() = withContext(Dispatchers.IO) {
        val mp = mediaProjection
        if (mp == null) {
            Log.e(TAG, "缺少 MediaProjection — 请先在主界面授权音频捕获")
            isRunning = false
            stopSelf()
            return@withContext
        }

        // WakeLock
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NTE:capture")
            .apply { acquire(30 * 60 * 1000L) }

        // DSP
        fb = FilterBank()
        initWatchers()

        val sr = Config.SAMPLE_RATE
        val ch = Config.CHANNELS
        val frameSamples = Config.FRAME_SAMPLES
        val bufSamples = maxOf(
            AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) / 2,
            frameSamples * ch
        )

        val capConf = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(capConf)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build())
                .setBufferSizeInBytes(bufSamples * 2)
                .build()
        } else null

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败")
            isRunning = false; stopSelf(); return@withContext
        }

        audioRecord!!.startRecording()
        capturing = true; isRunning = true
        Log.i(TAG, "捕获开始 sr=$sr ch=$ch frame=$frameSamples bufsz=$bufSamples")

        val shortBuf = ShortArray(frameSamples * ch)
        val mono = DoubleArray(frameSamples)

        while (capturing && isActive) {
            val n = audioRecord!!.read(shortBuf, 0, shortBuf.size)
            if (n <= 0) { delay(50); continue }

            val samples = n / ch
            for (i in 0 until samples) {
                var sum = 0.0
                for (c in 0 until ch) sum += shortBuf[i * ch + c].toDouble() / 32768.0
                mono[i] = sum / ch
            }

            val filtered = fb.process(mono)
            val ds = dodgeWatcher?.feed(filtered) ?: 0f
            val cs = counterWatcher?.feed(filtered) ?: 0f

            dodgeScore = ds; counterScore = cs
            onScoreUpdate?.invoke(ds, cs)
        }
    }

    private fun stopCapture() {
        capturing = false
        audioRecord?.apply { stop(); release() }
        audioRecord = null
        wakeLock?.release()
        mediaProjection?.stop()
        mediaProjection = null
        isRunning = false
    }

    private fun initWatchers() {
        fun loadRaw(path: String): FloatArray {
            val bytes = assets.open(path).readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) floats[i] = buf.float
            Log.i(TAG, "$path: ${floats.size} samples")
            return floats
        }

        try {
            val ref = loadRaw(Config.DODGE_RAW)
            dodgeWatcher = Watcher("闪避", ref,
                action = { KeyInjector.pressRB() },
                threshold = Config.DODGE_THRESH, cooldownSec = Config.DODGE_COOLDOWN,
                sampleRate = Config.SAMPLE_RATE, allowRepeat = Config.ALLOW_REPEAT)
            Log.i(TAG, "闪避 Watcher OK")
        } catch (e: Exception) {
            Log.e(TAG, "闪避样本加载失败: ${e.message}", e)
        }

        try {
            val ref = loadRaw(Config.COUNTER_RAW)
            counterWatcher = Watcher("反击", ref,
                action = { KeyInjector.pressX() },
                threshold = Config.COUNTER_THRESH, cooldownSec = Config.COUNTER_COOLDOWN,
                sampleRate = Config.SAMPLE_RATE, allowRepeat = Config.ALLOW_REPEAT)
            Log.i(TAG, "反击 Watcher OK")
        } catch (e: Exception) {
            Log.e(TAG, "反击样本加载失败: ${e.message}", e)
        }
    }

    // ────────────────────────────────────────
    //  通知 & 工具
    // ────────────────────────────────────────

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(id, notification)
        }
    }

    private fun makeChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "音频捕获", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotify(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NTE Sound Trigger")
            .setContentText("正在监听游戏音频")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
