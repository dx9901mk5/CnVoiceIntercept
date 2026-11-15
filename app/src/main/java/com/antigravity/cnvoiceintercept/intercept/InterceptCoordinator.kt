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
 * 攔截協調器：
 * 流程：偵測到語音呼喊收音通知 → 透過 Shizuku 強制終止陸版語音助理 → 延遲緩衝 → 啟動 Gemini（含震動回饋）或自選 AI App。
 * （已移除返回鍵動作，直接以 Shizuku force-stop 強制終止陸版助理）
 */
object InterceptCoordinator {
    private const val COOLDOWN_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastTriggerTimeMs = 0L

    fun onInterceptTriggered(
        context: Context,
        source: String,
        detectedPackage: String? = null
    ) {
        if (!AppPrefs.isEnabled(context)) return

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastTriggerTimeMs < COOLDOWN_MS) {
                return
            }
            lastTriggerTimeMs = now
        }

        executeInterceptPipeline(context.applicationContext, source, detectedPackage)
    }

    fun runManualTestPipeline(context: Context) {
        executeInterceptPipeline(context.applicationContext, "手動完整測試", null)
    }

    private fun executeInterceptPipeline(
        context: Context,
        source: String,
        detectedPackage: String?
    ) {
        scope.launch {
            InterceptLogStore.add("語音喚醒攔截 [$source] ${detectedPackage ?: ""}".trim())

            // 1. 透過 Shizuku 直接強制終止陸版助理（不送返回鍵）
            val shizuku = ShizukuController.service
            if (shizuku != null) {
                val targets = buildSet {
                    addAll(AppPrefs.getTargetPackagesList(context))
                    if (!detectedPackage.isNullOrBlank()) {
                        add(detectedPackage)
                    }
                }

                for (pkg in targets) {
                    val killed = runCatching { shizuku.forceStopPackage(pkg) }.getOrDefault(false)
                    if (killed) {
                        InterceptLogStore.add("強制終止 $pkg -> 成功")
                    }
                }
            } else {
                InterceptLogStore.add("警告：Shizuku UserService 未連線，無法強制終止陸版助理")
            }

            // 2. 延遲緩衝，等待系統釋放麥克風與焦點
            val delayMs = AppPrefs.getDelayMs(context)
            if (delayMs > 0) {
                delay(delayMs)
            }

            // 3. 啟動 Gemini（含震動回饋）或使用者指定的 AI App
            val launchMode = AppPrefs.getLaunchMode(context)
            if (launchMode == AppPrefs.LAUNCH_MODE_CUSTOM_APP) {
                val customPkg = AppPrefs.getCustomAiPackage(context)
                val customLabel = AppPrefs.getCustomAiLabel(context)
                val launchedByShizuku = if (shizuku != null) {
                    runCatching { shizuku.launchPackageAsShell(customPkg) }.getOrDefault(false)
                } else {
                    false
                }

                if (launchedByShizuku) {
                    InterceptLogStore.add("已啟動自訂 AI 助理：$customLabel ($customPkg)")
                } else {
                    runCatching {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(customPkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            InterceptLogStore.add("已啟動自訂 AI 助理：$customLabel ($customPkg)")
                        } else {
                            InterceptLogStore.add("找不到自訂 AI 助理套件：$customPkg")
                        }
                    }.onFailure {
                        InterceptLogStore.add("啟動 $customPkg 失敗: ${it.message}")
                    }
                }
            } else {
                // 預設：啟動 Gemini 浮層／圈選對話視窗（TriggerActivity 內含震動回饋）
                val launchedViaShizuku = if (shizuku != null) {
                    runCatching {
                        shizuku.startActivityAsShell(
                            BuildConfig.APPLICATION_ID,
                            TriggerActivity::class.java.name
                        )
                    }.getOrDefault(false)
                } else {
                    false
                }

                if (!launchedViaShizuku) {
                    runCatching {
                        val intent = Intent(context, TriggerActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        context.startActivity(intent)
                    }.onFailure {
                        InterceptLogStore.add("啟動 Gemini 失敗: ${it.message}")
                    }
                }
            }
        }
    }
}
