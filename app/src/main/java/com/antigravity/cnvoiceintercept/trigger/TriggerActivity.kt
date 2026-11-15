package com.antigravity.cnvoiceintercept.trigger

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.antigravity.cnvoiceintercept.R
import com.antigravity.cnvoiceintercept.config.AppPrefs
import com.antigravity.cnvoiceintercept.config.InterceptLogStore

class TriggerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val vibrate = AppPrefs.isVibrateEnabled(this)
        val ok = VisTrigger.trigger(this, vibrate)
        if (ok) {
            InterceptLogStore.add("VISTrigger 成功喚起 Gemini 視窗")
        } else {
            Toast.makeText(this, getString(R.string.trigger_failed), Toast.LENGTH_SHORT).show()
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
