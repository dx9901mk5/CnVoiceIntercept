package com.antigravity.cnvoiceintercept.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.cnvoiceintercept.BuildConfig
import com.antigravity.cnvoiceintercept.config.AppPrefs
import com.antigravity.cnvoiceintercept.config.InterceptLogStore
import com.antigravity.cnvoiceintercept.intercept.AssistantNotificationListener
import com.antigravity.cnvoiceintercept.intercept.InterceptCoordinator
import com.antigravity.cnvoiceintercept.intercept.InterceptKeepAliveService
import com.antigravity.cnvoiceintercept.shizuku.ShizukuController
import com.antigravity.cnvoiceintercept.trigger.TriggerActivity
import com.antigravity.cnvoiceintercept.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

data class InstalledAppItem(
    val label: String,
    val packageName: String
)

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ShizukuController.init(this)
        if (AppPrefs.isEnabled(this)) {
            InterceptKeepAliveService.start(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CnVoiceInterceptM3Theme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuController.refreshStateAndBind()
    }
}

@Composable
fun CnVoiceInterceptM3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun openDefaultAssistantSettings(context: Context) {
    val actions = listOf(
        Settings.ACTION_VOICE_INPUT_SETTINGS,
        "com.android.settings.VOICE_INPUT_SETTINGS",
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in actions) {
        val ok = runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (ok) return
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shizukuStatus by ShizukuController.status.collectAsState()
    val logs by InterceptLogStore.logs.collectAsState()

    var enabled by remember { mutableStateOf(AppPrefs.isEnabled(context)) }
    var pressBackFirst by remember { mutableStateOf(AppPrefs.isPressBackFirst(context)) }
    var vibrate by remember { mutableStateOf(AppPrefs.isVibrateEnabled(context)) }
    var targetPackages by remember { mutableStateOf(AppPrefs.getTargetPackagesRaw(context)) }
    var notifKeywords by remember { mutableStateOf(AppPrefs.getNotifKeywordsRaw(context)) }
    var delayMs by remember { mutableFloatStateOf(AppPrefs.getDelayMs(context).toFloat()) }
    var notifListenerEnabled by remember { mutableStateOf(AssistantNotificationListener.isEnabled(context)) }

    var launchMode by remember { mutableStateOf(AppPrefs.getLaunchMode(context)) }
    var customAiPkg by remember { mutableStateOf(AppPrefs.getCustomAiPackage(context)) }
    var customAiLabel by remember { mutableStateOf(AppPrefs.getCustomAiLabel(context)) }

    var showAppPickerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var updateInfoDialog by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    // 啟動時自動檢查 GitHub 是否有新版本，若有新版則推播通知並彈窗提醒
    LaunchedEffect(Unit) {
        UpdateChecker.checkForUpdate(context, sendNotificationIfNew = true) { result ->
            result.onSuccess { info ->
                if (info.hasUpdate) {
                    updateInfoDialog = info
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("陸版語音呼喚攔截", fontWeight = FontWeight.Bold)
                        Text(
                            "vivo / OPPO / 小米 CN ROM 語音喚醒秒切 Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "關於我")
                        Spacer(Modifier.width(4.dp))
                        Text("關於")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 置頂捷徑：Android 系統預設數位助理選擇頁面
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "步驟 1：確認系統預設數位助理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        "使用 VISTrigger 喚起 Gemini 圈選／浮層視窗前，請先在系統設定中將「預設數位助理應用程式」設為 Google（並於 Google 內選用 Gemini）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = { openDefaultAssistantSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("開啟 Android 系統預設數位助理設定頁")
                    }
                }
            }

            // 2. Shizuku 與一鍵 ADB 授權狀態卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (shizukuStatus.serviceBound && notifListenerEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (shizukuStatus.serviceBound && notifListenerEnabled)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "步驟 2：Shizuku 連線與一鍵 ADB 權限",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "• Shizuku 服務：${if (shizukuStatus.binderAlive) "運行中" else "未啟動"}  |  " +
                                    "授權：${if (shizukuStatus.permissionGranted) "已授權" else "未授權"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "• UserService (UID 2000)：${if (shizukuStatus.serviceBound) "已連線" else "未連線"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "• 語音收音通知監聽權限：${if (notifListenerEnabled) "已開啟" else "未開啟"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!shizukuStatus.permissionGranted) {
                                ShizukuController.requestPermission()
                            } else {
                                ShizukuController.refreshStateAndBind()
                                ShizukuController.grantAllRequiredPermissions(context) {
                                    notifListenerEnabled = AssistantNotificationListener.isEnabled(context)
                                    Toast.makeText(
                                        context,
                                        "已透過 Shizuku 自動授予所有所需 ADB 權限！",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (!shizukuStatus.permissionGranted) "1. 先點此請求 Shizuku 授權"
                            else "⚡ 一鍵透過 Shizuku 授予所有 ADB 權限"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("手動檢查通知權限", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                ShizukuController.refreshStateAndBind()
                                notifListenerEnabled = AssistantNotificationListener.isEnabled(context)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重新整理狀態", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. 攔截後要啟動的目標 AI 助理設定 (預設 Gemini VISTrigger / 可自選 AI App)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "攔截後啟動的目標 AI 助理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 選項 A：預設 VISTrigger 喚起 Gemini
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (launchMode == AppPrefs.LAUNCH_MODE_VIS_TRIGGER)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                launchMode = AppPrefs.LAUNCH_MODE_VIS_TRIGGER
                                AppPrefs.setLaunchMode(context, AppPrefs.LAUNCH_MODE_VIS_TRIGGER)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = launchMode == AppPrefs.LAUNCH_MODE_VIS_TRIGGER,
                                onClick = {
                                    launchMode = AppPrefs.LAUNCH_MODE_VIS_TRIGGER
                                    AppPrefs.setLaunchMode(context, AppPrefs.LAUNCH_MODE_VIS_TRIGGER)
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("Gemini 圈選／浮層視窗 (VISTrigger 模式 - 預設推薦)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "直接呼叫系統 IVoiceInteractionManagerService.showSessionFromSession，不跳離當前畫面直接彈出 Gemini 浮層與圈選搜尋。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 選項 B：自訂啟動其他 AI App
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (launchMode == AppPrefs.LAUNCH_MODE_CUSTOM_APP)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                launchMode = AppPrefs.LAUNCH_MODE_CUSTOM_APP
                                AppPrefs.setLaunchMode(context, AppPrefs.LAUNCH_MODE_CUSTOM_APP)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = launchMode == AppPrefs.LAUNCH_MODE_CUSTOM_APP,
                                    onClick = {
                                        launchMode = AppPrefs.LAUNCH_MODE_CUSTOM_APP
                                        AppPrefs.setLaunchMode(context, AppPrefs.LAUNCH_MODE_CUSTOM_APP)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("改為開啟指定的 AI App", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "目前選擇：$customAiLabel ($customAiPkg)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (launchMode == AppPrefs.LAUNCH_MODE_CUSTOM_APP) {
                                Spacer(Modifier.height(8.dp))
                                Text("快速選擇常見 AI App：", style = MaterialTheme.typography.labelMedium)
                                val aiPresets = listOf(
                                    "Google Gemini" to "com.google.android.apps.bard",
                                    "ChatGPT" to "com.openai.chatgpt",
                                    "Claude" to "com.anthropic.claude",
                                    "Perplexity" to "ai.perplexity.app.android",
                                    "Copilot" to "com.microsoft.copilot",
                                    "豆包 Doubao" to "com.larus.nova",
                                    "Kimi" to "com.moonshot.kimichat"
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    aiPresets.forEach { (label, pkg) ->
                                        FilterChip(
                                            selected = customAiPkg == pkg,
                                            onClick = {
                                                customAiPkg = pkg
                                                customAiLabel = label
                                                AppPrefs.setCustomAiPackage(context, pkg, label)
                                            },
                                            label = { Text(label, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                FilledTonalButton(
                                    onClick = { showAppPickerDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📱 從手機已安裝的所有 App 中挑選...")
                                }
                            }
                        }
                    }
                }
            }

            // 4. 語音攔截主控台與「不干涉手動點開」說明
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "語音呼喚攔截控制",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "💡 智慧區分語音與手動開啟：本工具僅在「語音呼喊喚醒」產生收音狀態通知（如『收音 (藍心小V)』）時才攔截並強制關閉；您平時在桌面手動點開陸版助理 App 打字或使用內建功能時，完全不會被干涉！",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    SwitchRow(
                        title = "啟用語音呼喚攔截",
                        subtitle = "語音呼叫陸版助理時，強制關閉陸版助理並啟動目標 AI",
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            AppPrefs.setEnabled(context, it)
                            if (it) InterceptKeepAliveService.start(context) else InterceptKeepAliveService.stop(context)
                        }
                    )

                    SwitchRow(
                        title = "強制關閉前先送出返回鍵",
                        subtitle = "比照原 MacroDroid 腳本先收起浮層再強制停止",
                        checked = pressBackFirst,
                        onCheckedChange = {
                            pressBackFirst = it
                            AppPrefs.setPressBackFirst(context, it)
                        }
                    )

                    SwitchRow(
                        title = "喚起 Gemini 時震動回饋",
                        subtitle = "觸發成功時提供清脆觸覺點擊震動",
                        checked = vibrate,
                        onCheckedChange = {
                            vibrate = it
                            AppPrefs.setVibrateEnabled(context, it)
                        }
                    )

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                context.startActivity(Intent(context, TriggerActivity::class.java))
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("測試 VISTrigger")
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                InterceptCoordinator.runManualTestPipeline(context)
                            }
                        ) {
                            Text("測試完整攔截流程")
                        }
                    }
                }
            }

            // 5. 廠牌快速預設與目標設定 (vivo / OPPO / 小米)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "陸版 ROM 廠牌預設與關鍵字設定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text("一鍵套用廠牌預設值：", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                targetPackages = AppPrefs.DEFAULT_TARGET_PACKAGES
                                notifKeywords = AppPrefs.DEFAULT_NOTIF_KEYWORDS
                                AppPrefs.setTargetPackagesRaw(context, targetPackages)
                                AppPrefs.setNotifKeywordsRaw(context, notifKeywords)
                            },
                            label = { Text("三廠牌通用預設") }
                        )
                        AssistChip(
                            onClick = {
                                targetPackages = "com.vivo.ai.copilot,com.vivo.agent,com.vivo.aiservice"
                                notifKeywords = "收音,藍心小V,正在傾聽,正在聆聽"
                                AppPrefs.setTargetPackagesRaw(context, targetPackages)
                                AppPrefs.setNotifKeywordsRaw(context, notifKeywords)
                            },
                            label = { Text("vivo (藍心小V)") }
                        )
                        AssistChip(
                            onClick = {
                                targetPackages = "com.heytap.speechassist,com.coloros.speechassist,com.oplus.aiunit"
                                notifKeywords = "收音,小布,正在傾聽,正在聆聽,Breeno"
                                AppPrefs.setTargetPackagesRaw(context, targetPackages)
                                AppPrefs.setNotifKeywordsRaw(context, notifKeywords)
                            },
                            label = { Text("OPPO (小布助手)") }
                        )
                        AssistChip(
                            onClick = {
                                targetPackages = "com.miui.voiceassist,com.xiaomi.voiceassistant,com.miui.voicetrigger"
                                notifKeywords = "收音,小愛,小爱,正在傾聽,正在聆聽"
                                AppPrefs.setTargetPackagesRaw(context, targetPackages)
                                AppPrefs.setNotifKeywordsRaw(context, notifKeywords)
                            },
                            label = { Text("小米 (小愛同學)") }
                        )
                    }

                    OutlinedTextField(
                        value = notifKeywords,
                        onValueChange = {
                            notifKeywords = it
                            AppPrefs.setNotifKeywordsRaw(context, it)
                        },
                        label = { Text("語音收音通知關鍵字 (逗號分隔)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetPackages,
                        onValueChange = {
                            targetPackages = it
                            AppPrefs.setTargetPackagesRaw(context, it)
                        },
                        label = { Text("強制關閉陸版助理套件名 (逗號分隔)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("強制關閉後延遲啟動 AI：${delayMs.roundToLong()} ms", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = delayMs,
                        onValueChange = { delayMs = it },
                        onValueChangeFinished = { AppPrefs.setDelayMs(context, delayMs.roundToLong()) },
                        valueRange = 0f..800f
                    )
                }
            }

            // 6. 即時攔截紀錄
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("攔截執行紀錄", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { InterceptLogStore.clear() }) {
                            Text("清除")
                        }
                    }
                    if (logs.isEmpty()) {
                        Text(
                            "尚無攔截紀錄",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        logs.take(15).forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 7. 關於我與檢查更新卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("關於與版本更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "目前版本：v${BuildConfig.VERSION_NAME}（固定簽章，可直接覆蓋更新）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("關於我 / GitHub")
                        }
                        Button(
                            onClick = {
                                isCheckingUpdate = true
                                UpdateChecker.checkForUpdate(context, sendNotificationIfNew = true) { res ->
                                    isCheckingUpdate = false
                                    res.onSuccess { info ->
                                        if (info.hasUpdate) {
                                            updateInfoDialog = info
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "目前已是最新版本 (v${info.currentVersion})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            "檢查更新失敗：${err.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isCheckingUpdate) "檢查中..." else "檢查 APK 更新")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // 彈窗 1：從手機已安裝 App 挑選 AI App
    if (showAppPickerDialog) {
        InstalledAppPickerDialog(
            onDismiss = { showAppPickerDialog = false },
            onSelect = { item ->
                customAiPkg = item.packageName
                customAiLabel = item.label
                AppPrefs.setCustomAiPackage(context, item.packageName, item.label)
                showAppPickerDialog = false
            }
        )
    }

    // 彈窗 2：關於我
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("關於 陸版語音呼喚攔截") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("作者：dx9901mk5", fontWeight = FontWeight.SemiBold)
                    Text("版本：v${BuildConfig.VERSION_NAME}")
                    Text(
                        "專為 vivo (OriginOS)、OPPO (ColorOS)、小米 (HyperOS) 陸版 CN ROM 打造的語音喚醒攔截工具。\n\n" +
                            "當透過語音呼喊陸版助理時，利用 Shizuku 系統級權限瞬間強制關閉陸版助理並透過 VISTrigger 喚起 Gemini 圈選／浮層視窗（或自訂 AI App），且不干涉平時手動點開陸版助理使用。"
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAboutDialog = false
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.GITHUB_REPO_URL))
                    )
                }) {
                    Text("前往 GitHub 專案頁")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }

    // 彈窗 3：發現新版本更新對話框
    updateInfoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfoDialog = null },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
            title = { Text("發現新版本 v${info.latestVersion}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("目前版本：v${info.currentVersion} → 最新版本：v${info.latestVersion}")
                    Text("本 App 採用固定簽名，下載新版 APK 後可直接覆蓋安裝（推薦搭配支援 Shizuku 的 InstallerX Revived 或 ZArchiver 安裝）。")
                    if (info.releaseNotes.isNotBlank()) {
                        HorizontalDivider()
                        Text("更新內容：\n${info.releaseNotes}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val url = info.apkDownloadUrl ?: info.releasePageUrl
                    updateInfoDialog = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) {
                    Text("立即下載更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfoDialog = null }) {
                    Text("稍後再說")
                }
            }
        )
    }
}

@Composable
private fun InstalledAppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (InstalledAppItem) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var appList by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                    InstalledAppItem(label = label, packageName = pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
        appList = loaded
        loading = false
    }

    val filtered = remember(appList, searchQuery) {
        if (searchQuery.isBlank()) appList
        else appList.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇要啟動的 AI App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜尋 App 名稱或套件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    Text("正在讀取已安裝 App 清單...", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(item) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(item.label, fontWeight = FontWeight.Medium)
                                Text(
                                    item.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
