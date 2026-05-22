package com.nte.soundtrigger.trigger

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 通过 Shizuku UserService 向系统注入手柄按键事件。
 *
 * 映射:
 *   RB  (KEYCODE_BUTTON_R1 = 103) → 闪避
 *   X   (KEYCODE_BUTTON_X  = 99)  → 反击/攻击
 */
object KeyInjector {

    private const val TAG = "KeyInjector"
    const val KEYCODE_RB = 103
    const val KEYCODE_X  = 99

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Log.i(TAG, "Shizuku permission: ${if (grantResult == 0) "granted" else "denied"}")
    }

    fun init() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun destroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun pressRB() = press(KEYCODE_RB)
    fun pressX() = press(KEYCODE_X)

    fun isAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (_: Exception) { false }
    }

    fun hasPermission(): Boolean {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return false
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
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

    private fun press(keyCode: Int) {
        try {
            // Use ProcessBuilder with su as fallback since Shizuku v13
            // made newProcess private; su is universally compatible
            val cmd = arrayOf("su", "-c", "input keyevent $keyCode")
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
            process.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "按键注入失败 (keyCode=$keyCode): ${e.message}")
        }
    }
}
