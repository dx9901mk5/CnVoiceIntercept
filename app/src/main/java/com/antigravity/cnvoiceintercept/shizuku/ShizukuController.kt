package com.antigravity.cnvoiceintercept.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.antigravity.cnvoiceintercept.BuildConfig
import com.antigravity.cnvoiceintercept.config.AppExpiry
import com.antigravity.cnvoiceintercept.config.AppPrefs
import com.antigravity.cnvoiceintercept.config.InterceptLogStore
import com.antigravity.cnvoiceintercept.intercept.AssistantNotificationListener
import com.antigravity.cnvoiceintercept.intercept.InterceptCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShizukuStatus(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val serviceBound: Boolean = false
)

object ShizukuController {
    private const val REQUEST_CODE_SHIZUKU = 1001

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    var service: IPrivilegedService? = null
        private set

    private var appContext: Context? = null
    private var listenersRegistered = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, PrivilegedService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val taskCallback = object : ITaskCallback.Stub() {
        override fun onTargetTaskAppeared(packageName: String, className: String) {
            val ctx = appContext ?: return
            val alreadyKilled = className.startsWith("KILL_DONE:")
            val reason = className.removePrefix("KILL_DONE:").ifEmpty { "底層語音喚醒偵測" }
            InterceptCoordinator.onInterceptTriggered(
                context = ctx,
                source = reason,
                detectedPackage = packageName,
                alreadyKilled = alreadyKilled
            )
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IPrivilegedService.Stub.asInterface(binder)
            _status.value = _status.value.copy(serviceBound = service != null)
            InterceptLogStore.add("Shizuku UserService 已連線（底層語音喚醒直連監聽就緒）")
            val ctx = appContext
            if (ctx != null && !AssistantNotificationListener.isEnabled(ctx)) {
                grantAllRequiredPermissions(ctx) {}
            }
            refreshTaskWatch()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _status.value = _status.value.copy(serviceBound = false)
            InterceptLogStore.add("Shizuku UserService 已斷線")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStateAndBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        _status.value = ShizukuStatus(binderAlive = false, permissionGranted = false, serviceBound = false)
        InterceptLogStore.add("Shizuku Binder 已離線")
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            _status.value = _status.value.copy(permissionGranted = granted)
            if (granted) {
                InterceptLogStore.add("已取得 Shizuku 授權")
                bindUserService()
            } else {
                InterceptLogStore.add("Shizuku 授權被拒絕")
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        AppPrefs.ensurePinnedAssistantInitialized(context)
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            listenersRegistered = true
        }
        refreshStateAndBind()
    }

    fun refreshStateAndBind() {
        if (AppExpiry.isExpired()) return
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = if (alive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else {
            false
        }
        _status.value = _status.value.copy(
            binderAlive = alive,
            permissionGranted = granted,
            serviceBound = service != null
        )
        if (alive && granted && service == null) {
            bindUserService()
        }
    }

    fun requestPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            InterceptLogStore.add("找不到 Shizuku 服務，請先啟動 Shizuku App")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshStateAndBind()
            return
        }
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    fun bindUserService() {
        runCatching {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        }.onFailure {
            InterceptLogStore.add("綁定 Shizuku UserService 失敗: ${it.message}")
        }
    }

    fun grantAllRequiredPermissions(context: Context, onComplete: (Boolean) -> Unit) {
        val svc = service
        if (svc == null) {
            InterceptLogStore.add("Shizuku UserService 尚未連線，無法一鍵授權")
            onComplete(false)
            return
        }
        val appPkg = context.packageName
        val notifComponent = ComponentName(context, AssistantNotificationListener::class.java).flattenToString()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching {
                svc.grantAllRequiredPermissions(appPkg, notifComponent)
            }.getOrDefault(false)
            InterceptLogStore.add(
                if (ok) "已透過 Shizuku 一鍵授予所有所需 ADB 權限（通知監聽／背景彈窗／省電白名單）"
                else "Shizuku 權限授予部分完成，請確認通知監聽狀態"
            )
            withContext(Dispatchers.Main) {
                onComplete(ok)
            }
        }
    }

    fun refreshTaskWatch() {
        val ctx = appContext ?: return
        val svc = service ?: return
        val enabled = !AppExpiry.isExpired() && AppPrefs.isEnabled(ctx) && AppPrefs.isDirectWakeupDetectEnabled(ctx)
        runCatching {
            if (enabled) {
                val pkgs = AppPrefs.getTargetPackagesList(ctx)
                svc.startTaskWatch(pkgs, taskCallback)
            } else {
                svc.stopTaskWatch()
            }
        }
    }
}
