package com.antigravity.cnvoiceintercept.intercept

import android.content.Context
import android.content.Intent
import com.antigravity.cnvoiceintercept.BuildConfig
import com.antigravity.cnvoiceintercept.config.AppPrefs
import com.antigravity.cnvoiceintercept.config.InterceptLogStore
import com.antigravity.cnvoiceintercept.shizuku.ShizukuController
import com.antigravity.cnvoiceintercept.trigger.TriggerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 攔截協調器 (極速單一指針版)：
 * 流程：
 * 1. Shizuku 底層語音喚醒直連監聽（或收音通知備援）偵測到語音喚醒
 * 2. 若由底層直連監聽觸發，已在 Shizuku UID 2000 行程內瞬間完成單一鎖定助理的 forceStopPackage；
 *    若由通知或手動測試觸發，則只針對已鎖定的單一陸版助理套件執行強制終止（不再逐一掃描其他未安裝廠牌套件）
 * 3. 極短緩衝後直接啟動 Gemini（含震動回饋）或自選 AI App。
 */
object InterceptCoordinator {
    private const val COOLDOWN_MS = 1200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastTriggerTimeMs = 0L

    fun onInterceptTriggered(
        context: Context,
        source: String,
        detectedPackage: String? = null,
        alreadyKilled: Boolean = false
    ) {
        if (!AppPrefs.isEnabled(context)) return

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastTriggerTimeMs < COOLDOWN_MS) {
                return
            }
            lastTriggerTimeMs = now
        }

        executeInterceptPipeline(context.applicationContext, source, detectedPackage, alreadyKilled)
    }

    fun runManualTestPipeline(context: Context) {
        executeInterceptPipeline(
            context = context.applicationContext,
            source = "手動完整測試",
            detectedPackage = AppPrefs.getPinnedPackage(context),
            alreadyKilled = false
        )
    }

    private fun executeInterceptPipeline(
        context: Context,
        source: String,
        detectedPackage: String?,
        alreadyKilled: Boolean
    ) {
        scope.launch {
            val shizuku = ShizukuController.service
            val pinnedPkg = detectedPackage?.takeIf { it.isNotBlank() } ?: AppPrefs.getPinnedPackage(context)

            if (alreadyKilled) {
                InterceptLogStore.add("⚡ 語音喚醒秒殺 [$source] 已強制終止 $pinnedPkg")
            } else {
                InterceptLogStore.add("語音喚醒攔截 [$source] 目標：$pinnedPkg")
                if (shizuku != null) {
                    val targets = AppPrefs.getTargetPackagesList(context).ifEmpty { listOf(pinnedPkg) }
                    for (pkg in targets) {
                        val killed = runCatching { shizuku.forceStopPackage(pkg) }.getOrDefault(false)
                        if (killed) {
                            InterceptLogStore.add("強制終止 $pkg -> 成功")
                        }
                    }
                } else {
                    InterceptLogStore.add("警告：Shizuku UserService 未連線，無法強制終止 $pinnedPkg")
                }
            }

            // 極短緩衝等待系統釋放焦點
            val delayMs = AppPrefs.getDelayMs(context)
            if (delayMs > 0) {
                delay(delayMs)
            }

            // 啟動 Gemini（含震動回饋）或使用者指定的 AI App
            val launchMode = AppPrefs.getLaunchMode(context)
            if (launchMode == AppPrefs.LAUNCH_MODE_CUSTOM_APP) {
                val customPkg = AppPrefs.getCustomAiPackage(context)
                val customLabel = AppPrefs.getCustomAiLabel(context)
                val launchedDirectly = runCatching {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(customPkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        InterceptLogStore.add("已啟動自訂 AI 助理：$customLabel ($customPkg)")
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)

                if (!launchedDirectly && shizuku != null) {
                    val launchedByShizuku = runCatching { shizuku.launchPackageAsShell(customPkg) }.getOrDefault(false)
                    if (launchedByShizuku) {
                        InterceptLogStore.add("已啟動自訂 AI 助理：$customLabel ($customPkg)")
                    } else {
                        InterceptLogStore.add("找不到或無法啟動自訂 AI 助理：$customPkg")
                    }
                }
            } else {
                // 優先直接以本行程 Binder 啟動 TriggerActivity（已授予 SYSTEM_ALERT_WINDOW，僅需 ~5ms，省去 am start 240ms 子行程開銷）
                val launchedDirectly = runCatching {
                    val intent = Intent(context, TriggerActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)

                if (!launchedDirectly && shizuku != null) {
                    runCatching {
                        shizuku.startActivityAsShell(
                            BuildConfig.APPLICATION_ID,
                            TriggerActivity::class.java.name
                        )
                    }.onFailure {
                        InterceptLogStore.add("啟動 Gemini 失敗: ${it.message}")
                    }
                }
            }
        }
    }
}
