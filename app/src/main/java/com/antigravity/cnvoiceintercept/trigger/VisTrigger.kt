package com.antigravity.cnvoiceintercept.trigger

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.antigravity.cnvoiceintercept.config.InterceptLogStore
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 整合自 parallelcc/MiCTS (VISTrigger_2.6) 的核心喚起方法：
 * 直接透過 Binder 呼叫系統隱藏服務 IVoiceInteractionManagerService.showSessionFromSession，
 * 繞過已失效或被陸版 ROM 攔截的 android.intent.action.VOICE_COMMAND。
 */
object VisTrigger {
    private const val TAG = "VisTrigger"

    // VoiceInteractionSession flags: SHOW_WITH_ASSIST (1) | SHOW_WITH_SCREENSHOT (2) | 4 = 7
    private const val VIS_SHOW_FLAGS = 7

    @SuppressLint("PrivateApi")
    fun trigger(context: Context?, vibrate: Boolean): Boolean {
        val result = runCatching {
            val bundle = Bundle()
            val iVimsClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")
            val vis = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "voiceinteraction") as IBinder
            val vims = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, vis)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HiddenApiBypass.invoke(
                    iVimsClass,
                    vims,
                    "showSessionFromSession",
                    null,
                    bundle,
                    VIS_SHOW_FLAGS,
                    "hyperOS_home"
                ) as Boolean
            } else {
                HiddenApiBypass.invoke(
                    iVimsClass,
                    vims,
                    "showSessionFromSession",
                    null,
                    bundle,
                    VIS_SHOW_FLAGS
                ) as Boolean
            }
        }.onFailure { e ->
            Log.e(TAG, "showSessionFromSession failed", e)
            InterceptLogStore.add("VISTrigger 失敗: ${e.javaClass.simpleName}: ${e.message}")
        }.getOrDefault(false)

        if (result && vibrate && context != null) {
            performHapticFeedback(context)
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun performHapticFeedback(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setFlags(128)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), attr)
            } else {
                vibrator.vibrate(longArrayOf(0, 1, 75, 76), -1, attr)
            }
        }
    }
}
