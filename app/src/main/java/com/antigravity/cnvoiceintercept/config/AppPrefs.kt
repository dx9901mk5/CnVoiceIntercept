package com.antigravity.cnvoiceintercept.config

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CnAssistantProfile(
    val id: String,
    val candidatePackages: List<String>,
    val defaultKeywords: String
)

data class DetectedAssistantInfo(
    val profileId: String,
    val pinnedPackage: String,
    val isInstalledOnDevice: Boolean,
    val deviceBrandModel: String
)

object AppPrefs {
    private const val PREFS_NAME = "cn_voice_intercept_prefs"

    private const val KEY_ENABLED = "intercept_enabled"
    private const val KEY_DIRECT_WAKEUP_ENABLED = "direct_wakeup_detect_enabled"
    private const val KEY_PINNED_PROFILE = "pinned_assistant_profile"
    private const val KEY_PINNED_PACKAGE = "pinned_assistant_package"
    private const val KEY_ASSISTANT_CONFIRMED = "assistant_confirmed_v130"
    private const val KEY_TARGET_PACKAGES = "target_packages"
    private const val KEY_NOTIF_KEYWORDS = "notif_keywords"
    private const val KEY_DELAY_MS = "delay_after_kill_ms_v130"
    private const val KEY_VIBRATE = "vibrate_on_trigger"

    // 啟動目標模式：預設喚起 Gemini 浮層視窗；或改為啟動自訂 AI App
    const val LAUNCH_MODE_VIS_TRIGGER = "vis_trigger"
    const val LAUNCH_MODE_CUSTOM_APP = "custom_app"
    private const val KEY_LAUNCH_MODE = "launch_mode"
    private const val KEY_CUSTOM_AI_PACKAGE = "custom_ai_package"
    private const val KEY_CUSTOM_AI_LABEL = "custom_ai_label"
    private const val KEY_APP_LANG = "app_language" // "system", "zh-TW", "zh-CN", "en"

    const val PROFILE_VIVO = "vivo"
    const val PROFILE_OPPO = "oppo"
    const val PROFILE_XIAOMI = "xiaomi"
    const val PROFILE_CUSTOM = "custom"

    val PROFILES = listOf(
        CnAssistantProfile(
            id = PROFILE_VIVO,
            candidatePackages = listOf("com.vivo.ai.copilot", "com.vivo.agent"),
            defaultKeywords = "收音,藍心小V,蓝心小V,正在傾聽,正在聆聽,正在听"
        ),
        CnAssistantProfile(
            id = PROFILE_OPPO,
            candidatePackages = listOf("com.heytap.speechassist", "com.coloros.speechassist", "com.oplus.aiunit"),
            defaultKeywords = "收音,小布,Breeno,正在傾聽,正在聆聽,正在听"
        ),
        CnAssistantProfile(
            id = PROFILE_XIAOMI,
            candidatePackages = listOf("com.miui.voiceassist", "com.xiaomi.voiceassistant"),
            defaultKeywords = "收音,小愛,小爱,正在傾聽,正在聆聽,正在听"
        )
    )

    const val DEFAULT_TARGET_PACKAGES = "com.vivo.ai.copilot"
    const val DEFAULT_NOTIF_KEYWORDS = "收音,藍心小V,蓝心小V,正在傾聽,正在聆聽,正在听,小布,小愛,小爱"
    const val DEFAULT_DELAY_MS = 40L
    const val DEFAULT_CUSTOM_AI_PACKAGE = "com.google.android.apps.bard"
    const val DEFAULT_CUSTOM_AI_LABEL = "Google Gemini"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 自動偵測本機安裝的陸版語音助理與廠牌型號，回傳單一鎖定套件指標。
     */
    fun detectDeviceCnAssistant(context: Context): DetectedAssistantInfo {
        val pm = context.packageManager
        val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.ROOT)
        val deviceBrandModel = "${Build.BRAND.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        fun isPkgInstalled(pkg: String): Boolean {
            return runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

        // 1. 先依已安裝的語音助理主套件精準判定
        for (profile in PROFILES) {
            for (pkg in profile.candidatePackages) {
                if (isPkgInstalled(pkg)) {
                    return DetectedAssistantInfo(
                        profileId = profile.id,
                        pinnedPackage = pkg,
                        isInstalledOnDevice = true,
                        deviceBrandModel = deviceBrandModel
                    )
                }
            }
        }

        // 2. 若因系統權限隱藏套件清單，則依手機/平板廠牌名稱判定
        return when {
            brand.contains("vivo") || brand.contains("iqoo") -> DetectedAssistantInfo(
                profileId = PROFILE_VIVO,
                pinnedPackage = "com.vivo.ai.copilot",
                isInstalledOnDevice = false,
                deviceBrandModel = deviceBrandModel
            )
            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") || brand.contains("oplus") -> DetectedAssistantInfo(
                profileId = PROFILE_OPPO,
                pinnedPackage = "com.heytap.speechassist",
                isInstalledOnDevice = false,
                deviceBrandModel = deviceBrandModel
            )
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> DetectedAssistantInfo(
                profileId = PROFILE_XIAOMI,
                pinnedPackage = "com.miui.voiceassist",
                isInstalledOnDevice = false,
                deviceBrandModel = deviceBrandModel
            )
            else -> DetectedAssistantInfo(
                profileId = PROFILE_VIVO,
                pinnedPackage = "com.vivo.ai.copilot",
                isInstalledOnDevice = false,
                deviceBrandModel = deviceBrandModel
            )
        }
    }

    /**
     * 確保初始化單一助理指標（若尚未設定，自動填入本機偵測結果）
     */
    fun ensurePinnedAssistantInitialized(context: Context): DetectedAssistantInfo {
        val detected = detectDeviceCnAssistant(context)
        val p = prefs(context)
        if (!p.contains(KEY_PINNED_PACKAGE) || !p.contains(KEY_PINNED_PROFILE)) {
            val profile = PROFILES.find { it.id == detected.profileId }
            p.edit()
                .putString(KEY_PINNED_PROFILE, detected.profileId)
                .putString(KEY_PINNED_PACKAGE, detected.pinnedPackage)
                .putString(KEY_TARGET_PACKAGES, detected.pinnedPackage)
                .apply {
                    if (profile != null) {
                        putString(KEY_NOTIF_KEYWORDS, profile.defaultKeywords)
                    }
                }
                .apply()
        }
        return detected
    }

    fun isAssistantConfirmed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASSISTANT_CONFIRMED, false)

    fun setAssistantConfirmed(context: Context, confirmed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ASSISTANT_CONFIRMED, confirmed).apply()
    }

    fun getPinnedProfileId(context: Context): String {
        ensurePinnedAssistantInitialized(context)
        return prefs(context).getString(KEY_PINNED_PROFILE, PROFILE_VIVO) ?: PROFILE_VIVO
    }

    fun getPinnedPackage(context: Context): String {
        ensurePinnedAssistantInitialized(context)
        return prefs(context).getString(KEY_PINNED_PACKAGE, DEFAULT_TARGET_PACKAGES) ?: DEFAULT_TARGET_PACKAGES
    }

    fun applyPinnedAssistantProfile(
        context: Context,
        profileId: String,
        customPackage: String? = null,
        markConfirmed: Boolean = true
    ) {
        val detected = detectDeviceCnAssistant(context)
        val profile = PROFILES.find { it.id == profileId }
        val resolvedPkg = when {
            !customPackage.isNullOrBlank() -> customPackage.trim()
            profile != null && detected.profileId == profileId -> detected.pinnedPackage
            profile != null -> profile.candidatePackages.first()
            else -> getPinnedPackage(context)
        }
        val keywords = profile?.defaultKeywords ?: getNotifKeywordsRaw(context)

        prefs(context).edit()
            .putString(KEY_PINNED_PROFILE, profileId)
            .putString(KEY_PINNED_PACKAGE, resolvedPkg)
            .putString(KEY_TARGET_PACKAGES, resolvedPkg)
            .putString(KEY_NOTIF_KEYWORDS, keywords)
            .putBoolean(KEY_ASSISTANT_CONFIRMED, markConfirmed)
            .apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /**
     * 預設開啟「Shizuku 底層語音喚醒事件直連偵測」（不需等待 1.6 秒的系統通知，喚醒瞬間直接攔截）。
     */
    fun isDirectWakeupDetectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DIRECT_WAKEUP_ENABLED, true)

    fun setDirectWakeupDetectEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIRECT_WAKEUP_ENABLED, value).apply()
    }

    fun getTargetPackagesRaw(context: Context): String {
        ensurePinnedAssistantInitialized(context)
        return prefs(context).getString(KEY_TARGET_PACKAGES, getPinnedPackage(context)) ?: getPinnedPackage(context)
    }

    fun setTargetPackagesRaw(context: Context, raw: String) {
        val firstPkg = raw.split(",", "\n", ";").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: DEFAULT_TARGET_PACKAGES
        prefs(context).edit()
            .putString(KEY_TARGET_PACKAGES, raw)
            .putString(KEY_PINNED_PACKAGE, firstPkg)
            .apply()
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
