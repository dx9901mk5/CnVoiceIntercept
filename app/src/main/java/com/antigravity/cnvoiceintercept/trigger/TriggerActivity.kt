package com.antigravity.cnvoiceintercept.trigger

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.antigravity.cnvoiceintercept.R
import com.antigravity.cnvoiceintercept.config.AppPrefs
import com.antigravity.cnvoiceintercept.config.InterceptLogStore

class TriggerActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val delayedFinish = Runnable {
        if (!isFinishing && !isDestroyed) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val vibrate = AppPrefs.isVibrateEnabled(this)
        val ok = VisTrigger.trigger(this, vibrate)
        if (ok) {
            InterceptLogStore.add("已成功啟動 Gemini 視窗" + if (vibrate) " (含震動回饋)" else "")
        } else {
            Toast.makeText(this, getString(R.string.trigger_failed), Toast.LENGTH_SHORT).show()
        }

        // 延後 300ms 再結束透明 Activity，避免系統 AssistStructure 擷取時因 Activity 瞬間銷毀而觸發 3 秒逾時
        mainHandler.postDelayed(delayedFinish, 300L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedFinish)
        super.onDestroy()
    }
}
