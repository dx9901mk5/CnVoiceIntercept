package com.antigravity.cnvoiceintercept.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppPrefs {
    private const val PREFS_NAME = "cn_voice_intercept_prefs"

    private const val KEY_ENABLED = "intercept_enabled"
    private const val KEY_TASK_WATCH_ENABLED = "task_watch_enabled"
    private const val KEY_TARGET_PACKAGES = "target_packages"
    private const val KEY_NOTIF_KEYWORDS = "notif_keywords"
    private const val KEY_DELAY_MS = "delay_after_kill_ms"
    private const val KEY_PRESS_BACK_FIRST = "press_back_first"
    private const val KEY_VIBRATE = "vibrate_on_trigger"

    // 啟動目標模式：預設使用 VISTrigger 喚起 Gemini 浮層視窗；或改為啟動自訂 AI App
    const val LAUNCH_MODE_VIS_TRIGGER = "vis_trigger"
    const val LAUNCH_MODE_CUSTOM_APP = "custom_app"
    private const val KEY_LAUNCH_MODE = "launch_mode"
    private const val KEY_CUSTOM_AI_PACKAGE = "custom_ai_package"
    private const val KEY_CUSTOM_AI_LABEL = "custom_ai_label"
    private const val KEY_APP_LANG = "app_language" // "system", "zh-TW", "zh-CN", "en"

    // 預設涵蓋 vivo (藍心小V)、OPPO (小布助手)、小米 (小愛同學) 的語音助理套件
    const val DEFAULT_TARGET_PACKAGES =
        "com.vivo.ai.copilot,com.vivo.agent,com.vivo.aiservice," +
        "com.heytap.speechassist,com.coloros.speechassist,com.oplus.aiunit," +
        "com.miui.voiceassist,com.xiaomi.voiceassistant"

    // 預設語音呼喊喚醒時的收音通知關鍵字（僅在語音喚醒收音時觸發，不影響手動點開 App）
    const val DEFAULT_NOTIF_KEYWORDS = "收音,藍心小V,正在傾聽,正在聆聽,正在听,語音喚醒,小布助手,小愛同學,小爱同学"
    const val DEFAULT_DELAY_MS = 150L
    const val DEFAULT_CUSTOM_AI_PACKAGE = "com.google.android.apps.bard"
    const val DEFAULT_CUSTOM_AI_LABEL = "Google Gemini"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /**
     * 預設關閉前景 Task 無差別監控，確保「只攔截語音呼叫收音，不干涉手動點開陸版助理直接使用」。
     */
    fun isTaskWatchEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TASK_WATCH_ENABLED, false)

    fun setTaskWatchEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TASK_WATCH_ENABLED, value).apply()
    }

    fun getTargetPackagesRaw(context: Context): String =
        prefs(context).getString(KEY_TARGET_PACKAGES, DEFAULT_TARGET_PACKAGES) ?: DEFAULT_TARGET_PACKAGES

    fun setTargetPackagesRaw(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_TARGET_PACKAGES, raw).apply()
    }

    fun getTargetPackagesList(context: Context): List<String> =
        getTargetPackagesRaw(context)
            .split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun getNotifKeywordsRaw(context: Context): String =
        prefs(context).getString(KEY_NOTIF_KEYWORDS, DEFAULT_NOTIF_KEYWORDS) ?: DEFAULT_NOTIF_KEYWORDS

    fun setNotifKeywordsRaw(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_NOTIF_KEYWORDS, raw).apply()
    }

    fun getNotifKeywordsList(context: Context): List<String> =
        getNotifKeywordsRaw(context)
            .split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun getDelayMs(context: Context): Long =
        prefs(context).getLong(KEY_DELAY_MS, DEFAULT_DELAY_MS)

    fun setDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong(KEY_DELAY_MS, delayMs.coerceIn(0L, 1500L)).apply()
    }

    fun isPressBackFirst(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRESS_BACK_FIRST, true)

    fun setPressBackFirst(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRESS_BACK_FIRST, value).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE, true)

    fun setVibrateEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE, value).apply()
    }

    fun getLaunchMode(context: Context): String =
        prefs(context).getString(KEY_LAUNCH_MODE, LAUNCH_MODE_VIS_TRIGGER) ?: LAUNCH_MODE_VIS_TRIGGER

    fun setLaunchMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_LAUNCH_MODE, mode).apply()
    }

    fun getCustomAiPackage(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_AI_PACKAGE, DEFAULT_CUSTOM_AI_PACKAGE) ?: DEFAULT_CUSTOM_AI_PACKAGE

    fun setCustomAiPackage(context: Context, pkg: String, label: String) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_AI_PACKAGE, pkg.trim())
            .putString(KEY_CUSTOM_AI_LABEL, label.trim())
            .apply()
    }

    fun getCustomAiLabel(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_AI_LABEL, DEFAULT_CUSTOM_AI_LABEL) ?: DEFAULT_CUSTOM_AI_LABEL

    fun getAppLanguage(context: Context): String =
        prefs(context).getString(KEY_APP_LANG, "system") ?: "system"

    fun setAppLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_APP_LANG, lang).apply()
    }
}

object InterceptLogStore {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun add(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        _logs.value = (listOf(entry) + _logs.value).take(60)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
