package com.nte.soundtrigger.trigger

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.IOException

/**
 * 通过 Shizuku 向系统注入手柄按键事件
 *
 * Shizuku 以 ADB/root 权限运行后台服务，App 通过其 API 执行特权命令。
 * 不需要完整 Root，只需 ADB 启动一次 Shizuku 服务即可。
 *
 * 映射:
 *   RB  (KEYCODE_BUTTON_R1 = 103) → 闪避
 *   X   (KEYCODE_BUTTON_X  = 99)  → 反击/攻击
 *
 * 延迟: 约 10-30ms (Shizuku IPC + input dispatcher)
 */
object KeyInjector {

    private const val TAG = "KeyInjector"

    const val KEYCODE_RB = 103  // KEYCODE_BUTTON_R1
    const val KEYCODE_X  = 99   // KEYCODE_BUTTON_X

    // ── 生命周期 ──────────────────────────

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Log.i(TAG, "Shizuku permission: ${if (grantResult == 0) "granted" else "denied"}")
    }

    fun init() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun destroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // ── 公开 API ──────────────────────────

    fun pressRB() = press(KEYCODE_RB)
    fun pressX() = press(KEYCODE_X)

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            false
        } else {
            try {
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }
    }

    fun requestPermission(activity: android.app.Activity, requestCode: Int = 0) {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            Log.w(TAG, "Shizuku version too old, need >= 11")
            return
        }
        if (!hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    // ── 内部 ──────────────────────────────

    private fun press(keyCode: Int) {
        try {
            val process = Shizuku.newProcess(
                arrayOf("input", "keyevent", keyCode.toString()),
                null, null
            )
            process.waitFor()
            process.destroy()
        } catch (e: IOException) {
            Log.e(TAG, "按键注入 IOException: ${e.message}")
        } catch (e: InterruptedException) {
            Log.e(TAG, "按键注入中断: ${e.message}")
            Thread.currentThread().interrupt()
        } catch (e: SecurityException) {
            Log.e(TAG, "Shizuku 权限不足: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "按键注入失败: ${e.message}", e)
        }
    }
}
