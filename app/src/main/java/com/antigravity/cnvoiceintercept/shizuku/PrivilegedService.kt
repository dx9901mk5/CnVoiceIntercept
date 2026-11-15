package com.antigravity.cnvoiceintercept.shizuku

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku UserService (執行於 shell UID 2000 高權限行程)。
 * 負責：
 * 1. 強制關閉陸版語音助理 (IActivityManager.forceStopPackage / am force-stop)
 * 2. 一鍵授予所有所需 ADB 權限 (通知監聽、受限設定、背景彈窗、省電白名單)
 * 3. 繞過背景啟動限制，啟動 TriggerActivity 或自訂 AI App
 */
@SuppressLint("PrivateApi")
class PrivilegedService : IPrivilegedService.Stub() {

    private var watchExecutor: ScheduledExecutorService? = null
    private var watchFuture: ScheduledFuture<*>? = null
    private var lastReportedPackage: String? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
    }

    override fun destroy() {
        stopTaskWatch()
        exitProcess(0)
    }

    override fun forceStopPackage(packageName: String): Boolean {
        val binderSuccess = runCatching {
            val iAmClass = Class.forName("android.app.IActivityManager")
            val amBinder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "activity") as IBinder
            val iam = Class.forName("android.app.IActivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, amBinder)
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
        // 先透過 cmd package resolve-activity 找出該 App 的主 Launcher Activity 並用 am start 啟動
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

    override fun startTaskWatch(targetPackages: MutableList<String>, callback: ITaskCallback): Boolean {
        stopTaskWatch()
        val targets = targetPackages.toSet()
        if (targets.isEmpty()) return false

        val executor = Executors.newSingleThreadScheduledExecutor()
        watchExecutor = executor
        watchFuture = executor.scheduleWithFixedDelay({
            runCatching {
                val topPkg = getTopTaskPackage()
                if (topPkg != null && topPkg in targets) {
                    if (lastReportedPackage != topPkg) {
                        lastReportedPackage = topPkg
                        callback.onTargetTaskAppeared(topPkg, "")
                    }
                } else {
                    lastReportedPackage = null
                }
            }.onFailure {
                Log.w(TAG, "Task watch tick failed", it)
            }
        }, 0, 350, TimeUnit.MILLISECONDS)

        return true
    }

    override fun stopTaskWatch() {
        watchFuture?.cancel(true)
        watchFuture = null
        watchExecutor?.shutdownNow()
        watchExecutor = null
        lastReportedPackage = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun getTopTaskPackage(): String? {
        return runCatching {
            val tasks: List<ActivityManager.RunningTaskInfo>? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val iAtmClass = Class.forName("android.app.IActivityTaskManager")
                    val atmBinder = Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String::class.java)
                        .invoke(null, "activity_task") as IBinder
                    val iatm = Class.forName("android.app.IActivityTaskManager\$Stub")
                        .getMethod("asInterface", IBinder::class.java)
                        .invoke(null, atmBinder)
                    HiddenApiBypass.invoke(iAtmClass, iatm, "getTasks", 1) as? List<ActivityManager.RunningTaskInfo>
                } else {
                    val iAmClass = Class.forName("android.app.IActivityManager")
                    val amBinder = Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String::class.java)
                        .invoke(null, "activity") as IBinder
                    val iam = Class.forName("android.app.IActivityManager\$Stub")
                        .getMethod("asInterface", IBinder::class.java)
                        .invoke(null, amBinder)
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
