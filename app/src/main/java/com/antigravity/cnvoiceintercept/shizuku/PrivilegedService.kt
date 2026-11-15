package com.antigravity.cnvoiceintercept.shizuku

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Shizuku UserService (執行於 shell UID 2000 高權限行程)。
 * 負責：
 * 1. 快取 IActivityManager Binder，毫秒級強制關閉單一鎖定的陸版語音助理 (forceStopPackage)
 * 2. 即時監聽系統底層 events (logcat -b events -T 1) 的語音喚醒事件 (WakeupService / 語音浮層視窗 / 麥克風前台服務)，
 *    不需等待 1.6 秒的系統通知，於語音喚醒瞬間直接在 UID 2000 內秒殺陸版助理並回呼啟動 Gemini！
 * 3. 一鍵授予所有所需 ADB 權限 (通知監聽、受限設定、背景彈窗、省電白名單)
 */
@SuppressLint("PrivateApi")
class PrivilegedService : IPrivilegedService.Stub() {

    @Volatile
    private var watchThread: Thread? = null

    @Volatile
    private var watchProcess: Process? = null

    @Volatile
    private var watchRunning = false

    @Volatile
    private var lastWakeupKillTimeMs = 0L

    private var cachedIAmClass: Class<*>? = null
    private var cachedIAmInstance: Any? = null
    private var cachedIAtmClass: Class<*>? = null
    private var cachedIAtmInstance: Any? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
        initCachedBinders()
    }

    private fun initCachedBinders() {
        runCatching {
            val iAmClass = Class.forName("android.app.IActivityManager")
            val amBinder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "activity") as IBinder
            val iam = Class.forName("android.app.IActivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, amBinder)
            cachedIAmClass = iAmClass
            cachedIAmInstance = iam
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val iAtmClass = Class.forName("android.app.IActivityTaskManager")
                val atmBinder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "activity_task") as IBinder
                val iatm = Class.forName("android.app.IActivityTaskManager\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, atmBinder)
                cachedIAtmClass = iAtmClass
                cachedIAtmInstance = iatm
            }
        }
    }

    override fun destroy() {
        stopTaskWatch()
        exitProcess(0)
    }

    override fun forceStopPackage(packageName: String): Boolean {
        val binderSuccess = runCatching {
            val iAmClass = cachedIAmClass ?: Class.forName("android.app.IActivityManager")
            val iam = cachedIAmInstance ?: run {
                val amBinder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "activity") as IBinder
                Class.forName("android.app.IActivityManager\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, amBinder)
            }
            HiddenApiBypass.invoke(iAmClass, iam, "forceStopPackage", packageName, 0)
            true
        }.onFailure {
            Log.w(TAG, "Binder forceStopPackage failed for $packageName, falling back to am", it)
        }.getOrDefault(false)

        if (binderSuccess) return true

        return runExec(arrayOf("am", "force-stop", packageName))
    }

    override fun pressBack(): Boolean {
        return runExec(arrayOf("input", "keyevent", "4"))
    }

    override fun startActivityAsShell(packageName: String, className: String): Boolean {
        return runExec(
            arrayOf(
                "am", "start",
                "--user", "0",
                "-n", "$packageName/$className",
                "-f", "0x14010000"
            )
        )
    }

    override fun launchPackageAsShell(packageName: String): Boolean {
        val resolved = runCatching {
            val proc = Runtime.getRuntime().exec(
                arrayOf("cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", packageName)
            )
            val lines = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readLines() }
            proc.waitFor()
            lines.lastOrNull { it.contains("/") }?.trim()
        }.getOrNull()

        if (!resolved.isNullOrBlank()) {
            if (runExec(arrayOf("am", "start", "--user", "0", "-n", resolved, "-f", "0x10000000"))) {
                return true
            }
        }
        return runExec(arrayOf("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"))
    }

    override fun grantNotificationListener(componentName: String): Boolean {
        return runExec(arrayOf("cmd", "notification", "allow_listener", componentName))
    }

    override fun grantAllRequiredPermissions(appPackage: String, notifListenerComponent: String): Boolean {
        var allOk = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runExec(arrayOf("pm", "grant", appPackage, "android.permission.POST_NOTIFICATIONS"))
        }
        runExec(arrayOf("appops", "set", appPackage, "ACCESS_RESTRICTED_SETTINGS", "allow"))
        if (!runExec(arrayOf("cmd", "notification", "allow_listener", notifListenerComponent))) {
            allOk = false
        }
        runExec(arrayOf("appops", "set", appPackage, "SYSTEM_ALERT_WINDOW", "allow"))
        runExec(arrayOf("appops", "set", appPackage, "START_FOREGROUND", "allow"))
        runExec(arrayOf("appops", "set", appPackage, "RUN_IN_BACKGROUND", "allow"))
        runExec(arrayOf("appops", "set", appPackage, "RUN_ANY_IN_BACKGROUND", "allow"))
        runExec(arrayOf("dumpsys", "deviceidle", "whitelist", "+$appPackage"))
        return allOk
    }

    /**
     * 啟動「底層語音喚醒直連監聽」：
     * 直接訂閱系統 `logcat -b events -T 1` 串流，零輪詢延遲偵測單一鎖定助理的語音喚醒事件：
     * - 語音喚醒服務啟動 (WakeupService / voicewakeup / voicetrigger)
     * - 語音收音浮層視窗彈出 (CopilotPanelWindow / SpeechAssist / VoiceAssistWindow)
     * - 非手動前景下的麥克風收音前台服務啟動 (am_foreground_service_start ...,128])
     * 偵測到的瞬間立即在 UID 2000 內執行 forceStopPackage 秒殺陸版助理，並回呼 App 直接喚起 Gemini！
     */
    override fun startTaskWatch(targetPackages: MutableList<String>, callback: ITaskCallback): Boolean {
        stopTaskWatch()
        val targets = targetPackages.map { it.trim() }.filter { it.isNotEmpty() }
        if (targets.isEmpty()) return false
        val primaryPkg = targets.first()

        watchRunning = true
        val thread = Thread({
            while (watchRunning) {
                var proc: Process? = null
                try {
                    val startMs = System.currentTimeMillis()
                    proc = Runtime.getRuntime().exec(arrayOf("logcat", "-b", "events", "-T", "1"))
                    watchProcess = proc
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String? = reader.readLine()
                    while (watchRunning && line != null) {
                        val now = System.currentTimeMillis()
                        // 略過剛啟動時的歷史首行
                        if (now - startMs > 180L && now - lastWakeupKillTimeMs > 1100L) {
                            val match = matchVoiceWakeupEvent(line, targets, primaryPkg)
                            if (match != null) {
                                lastWakeupKillTimeMs = now
                                val (pkgToKill, reason) = match
                                // 立即在 Shizuku UID 2000 行程內直接透過快取 Binder 強制終止陸版助理（約 5~10ms）
                                forceStopPackage(pkgToKill)
                                runCatching {
                                    callback.onTargetTaskAppeared(pkgToKill, "KILL_DONE:$reason")
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                } catch (e: Exception) {
                    if (watchRunning) {
                        Log.w(TAG, "Event watch stream interrupted, restarting", e)
                        runCatching { Thread.sleep(500) }
                    }
                } finally {
                    runCatching { proc?.destroyForcibly() }
                }
            }
        }, "VoiceWakeupEventWatcher")
        thread.isDaemon = true
        watchThread = thread
        thread.start()
        return true
    }

    private fun matchVoiceWakeupEvent(
        line: String,
        targets: List<String>,
        primaryPkg: String
    ): Pair<String, String>? {
        // 1. 語音喚醒專屬浮層視窗 (vivo CopilotPanelWindow 等，不會在手動點開主程式 Activity 時出現)
        if ((line.contains("input_focus") || line.contains("viewroot_draw_event")) &&
            !line.contains("Focus leaving") && !line.contains("focus=false")
        ) {
            if (line.contains("CopilotPanelWindow")) {
                val pkg = targets.find { it.contains("vivo") } ?: primaryPkg
                return pkg to "語音浮層視窗(CopilotPanelWindow)"
            }
        }

        // 2. 語音喚醒專屬背景服務啟動 (am_proc_start started-service WakeupService / voicewakeup / voicetrigger)
        if (line.contains("am_proc_start") && line.contains("started-service")) {
            val matchedPkg = targets.find { line.contains(it) }
            if (matchedPkg != null && (
                    line.contains("Wakeup", ignoreCase = true) ||
                        line.contains("voicewakeup", ignoreCase = true) ||
                        line.contains("voicetrigger", ignoreCase = true)
                    )
            ) {
                return matchedPkg to "底層語音喚醒服務(WakeupService)"
            }
        }

        // 3. 陸版語音助理啟動麥克風收音 ForegroundService (type=128 MICROPHONE)
        // 若使用者並非手動打開該陸版助理的全螢幕主畫面 Activity，則必為語音呼喊喚醒！
        if (line.contains("am_foreground_service_start")) {
            val matchedPkg = targets.find { line.contains(it) }
            if (matchedPkg != null && (line.endsWith(",128]") || line.contains("ForegroundService"))) {
                val topActivityPkg = getTopTaskPackage()
                if (topActivityPkg != matchedPkg) {
                    return matchedPkg to "語音收音前台服務($matchedPkg)"
                }
            }
        }

        return null
    }

    override fun stopTaskWatch() {
        watchRunning = false
        runCatching { watchProcess?.destroyForcibly() }
        watchProcess = null
        runCatching { watchThread?.interrupt() }
        watchThread = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun getTopTaskPackage(): String? {
        return runCatching {
            val tasks: List<ActivityManager.RunningTaskInfo>? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val iAtmClass = cachedIAtmClass ?: Class.forName("android.app.IActivityTaskManager")
                    val iatm = cachedIAtmInstance ?: return null
                    HiddenApiBypass.invoke(iAtmClass, iatm, "getTasks", 1) as? List<ActivityManager.RunningTaskInfo>
                } else {
                    val iAmClass = cachedIAmClass ?: Class.forName("android.app.IActivityManager")
                    val iam = cachedIAmInstance ?: return null
                    HiddenApiBypass.invoke(iAmClass, iam, "getTasks", 1) as? List<ActivityManager.RunningTaskInfo>
                }
            tasks?.firstOrNull()?.topActivity?.packageName
        }.getOrNull()
    }

    private fun runExec(cmd: Array<String>): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor() == 0
        }.onFailure {
            Log.e(TAG, "Command failed: ${cmd.joinToString(" ")}", it)
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "PrivilegedService"
    }
}
