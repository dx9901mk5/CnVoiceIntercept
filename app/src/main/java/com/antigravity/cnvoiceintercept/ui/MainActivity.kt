package com.antigravity.cnvoiceintercept.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.cnvoiceintercept.BuildConfig
import com.antigravity.cnvoiceintercept.R
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
import java.util.Locale
import kotlin.math.roundToLong

data class InstalledAppItem(
    val label: String,
    val packageName: String
)

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun attachBaseContext(newBase: Context) {
        val lang = AppPrefs.getAppLanguage(newBase)
        val locale = when (lang) {
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> null
        }
        if (locale != null) {
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppPrefs.ensurePinnedAssistantInitialized(this)
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

@Composable
private fun profileDisplayName(profileId: String): String {
    return when (profileId) {
        AppPrefs.PROFILE_VIVO -> stringResource(R.string.assistant_name_vivo)
        AppPrefs.PROFILE_OPPO -> stringResource(R.string.assistant_name_oppo)
        AppPrefs.PROFILE_XIAOMI -> stringResource(R.string.assistant_name_xiaomi)
        else -> stringResource(R.string.assistant_name_custom)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shizukuStatus by ShizukuController.status.collectAsState()
    val logs by InterceptLogStore.logs.collectAsState()

    val detectedInfo = remember { AppPrefs.detectDeviceCnAssistant(context) }
    var pinnedProfileId by remember { mutableStateOf(AppPrefs.getPinnedProfileId(context)) }
    var pinnedPackage by remember { mutableStateOf(AppPrefs.getPinnedPackage(context)) }
    var showDetectDialog by remember { mutableStateOf(!AppPrefs.isAssistantConfirmed(context)) }

    var targetPackages by remember { mutableStateOf(AppPrefs.getTargetPackagesRaw(context)) }
    var notifKeywords by remember { mutableStateOf(AppPrefs.getNotifKeywordsRaw(context)) }
    var delayMs by remember { mutableFloatStateOf(AppPrefs.getDelayMs(context).toFloat()) }
    var notifListenerEnabled by remember { mutableStateOf(AssistantNotificationListener.isEnabled(context)) }

    var launchMode by remember { mutableStateOf(AppPrefs.getLaunchMode(context)) }
    var customAiPkg by remember { mutableStateOf(AppPrefs.getCustomAiPackage(context)) }
    var customAiLabel by remember { mutableStateOf(AppPrefs.getCustomAiLabel(context)) }
    var currentLang by remember { mutableStateOf(AppPrefs.getAppLanguage(context)) }

    var showAppPickerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var updateInfoDialog by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

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
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showLangDialog = true }) {
                        Icon(Icons.Default.Language, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_lang_short))
                    }
                    TextButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_about_short))
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
                            stringResource(R.string.step1_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        stringResource(R.string.step1_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = { openDefaultAssistantSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.step1_btn))
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
                            stringResource(R.string.step2_title),
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
                                stringResource(
                                    R.string.status_shizuku_service,
                                    if (shizukuStatus.binderAlive) stringResource(R.string.status_running) else stringResource(R.string.status_stopped),
                                    if (shizukuStatus.permissionGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_not_granted)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(
                                    R.string.status_user_service,
                                    if (shizukuStatus.serviceBound) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(
                                    R.string.status_notif_listener,
                                    if (notifListenerEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    val toastGrantedMsg = stringResource(R.string.toast_granted_all_adb)
                    Button(
                        onClick = {
                            if (!shizukuStatus.permissionGranted) {
                                ShizukuController.requestPermission()
                            } else {
                                ShizukuController.refreshStateAndBind()
                                ShizukuController.grantAllRequiredPermissions(context) {
                                    notifListenerEnabled = AssistantNotificationListener.isEnabled(context)
                                    Toast.makeText(context, toastGrantedMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (!shizukuStatus.permissionGranted) stringResource(R.string.btn_request_shizuku)
                            else stringResource(R.string.btn_grant_all_adb)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_manual_notif_settings), fontSize = 12.sp)
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
                            Text(stringResource(R.string.btn_refresh_status), fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. 步驟 3：鎖定本機陸版語音助理（單一指針極速模式）
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.pinned_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(
                                    R.string.pinned_detected_badge,
                                    detectedInfo.deviceBrandModel,
                                    profileDisplayName(detectedInfo.profileId)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(
                                    R.string.pinned_current_label,
                                    profileDisplayName(pinnedProfileId),
                                    pinnedPackage
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.pinned_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Button(
                        onClick = { showDetectDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_change_pinned_assistant))
                    }
                }
            }

            // 4. 攔截後要啟動的目標 AI 助理設定 (預設啟動 Gemini / 可自選 AI App)
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
                            stringResource(R.string.ai_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 選項 A：預設喚起 Gemini
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
                                Text(stringResource(R.string.ai_vis_title), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.ai_vis_desc),
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
                                    Text(stringResource(R.string.ai_custom_title), fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(R.string.ai_custom_current, customAiLabel, customAiPkg),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (launchMode == AppPrefs.LAUNCH_MODE_CUSTOM_APP) {
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.ai_quick_pick_label), style = MaterialTheme.typography.labelMedium)
                                val aiPresets = listOf(
                                    "Google Gemini" to "com.google.android.apps.bard",
                                    "ChatGPT" to "com.openai.chatgpt",
                                    "Claude" to "com.anthropic.claude",
                                    "Perplexity" to "ai.perplexity.app.android",
                                    "Copilot" to "com.microsoft.copilot",
                                    "Doubao 豆包" to "com.larus.nova",
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
                                    Text(stringResource(R.string.btn_pick_installed_app))
                                }
                            }
                        }
                    }
                }
            }

            // 5. 語音攔截主控台與「不干涉手動點開」說明
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
                            stringResource(R.string.control_section_title),
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
                            stringResource(R.string.control_voice_only_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                context.startActivity(Intent(context, TriggerActivity::class.java))
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_test_vis))
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                InterceptCoordinator.runManualTestPipeline(context)
                            }
                        ) {
                            Text(stringResource(R.string.btn_test_full))
                        }
                    }
                }
            }

            // 6. 鎖定助理套件與進階參數設定 (vivo / OPPO / 小米)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.preset_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(stringResource(R.string.preset_quick_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val det = AppPrefs.detectDeviceCnAssistant(context)
                                AppPrefs.applyPinnedAssistantProfile(context, det.profileId, det.pinnedPackage, true)
                                pinnedProfileId = AppPrefs.getPinnedProfileId(context)
                                pinnedPackage = AppPrefs.getPinnedPackage(context)
                                targetPackages = AppPrefs.getTargetPackagesRaw(context)
                                notifKeywords = AppPrefs.getNotifKeywordsRaw(context)
                                ShizukuController.refreshTaskWatch()
                            },
                            label = { Text(stringResource(R.string.preset_all)) }
                        )
                        AssistChip(
                            onClick = {
                                AppPrefs.applyPinnedAssistantProfile(context, AppPrefs.PROFILE_VIVO, "com.vivo.ai.copilot", true)
                                pinnedProfileId = AppPrefs.getPinnedProfileId(context)
                                pinnedPackage = AppPrefs.getPinnedPackage(context)
                                targetPackages = AppPrefs.getTargetPackagesRaw(context)
                                notifKeywords = AppPrefs.getNotifKeywordsRaw(context)
                                ShizukuController.refreshTaskWatch()
                            },
                            label = { Text(stringResource(R.string.preset_vivo)) }
                        )
                        AssistChip(
                            onClick = {
                                AppPrefs.applyPinnedAssistantProfile(context, AppPrefs.PROFILE_OPPO, null, true)
                                pinnedProfileId = AppPrefs.getPinnedProfileId(context)
                                pinnedPackage = AppPrefs.getPinnedPackage(context)
                                targetPackages = AppPrefs.getTargetPackagesRaw(context)
                                notifKeywords = AppPrefs.getNotifKeywordsRaw(context)
                                ShizukuController.refreshTaskWatch()
                            },
                            label = { Text(stringResource(R.string.preset_oppo)) }
                        )
                        AssistChip(
                            onClick = {
                                AppPrefs.applyPinnedAssistantProfile(context, AppPrefs.PROFILE_XIAOMI, null, true)
                                pinnedProfileId = AppPrefs.getPinnedProfileId(context)
                                pinnedPackage = AppPrefs.getPinnedPackage(context)
                                targetPackages = AppPrefs.getTargetPackagesRaw(context)
                                notifKeywords = AppPrefs.getNotifKeywordsRaw(context)
                                ShizukuController.refreshTaskWatch()
                            },
                            label = { Text(stringResource(R.string.preset_xiaomi)) }
                        )
                    }

                    OutlinedTextField(
                        value = targetPackages,
                        onValueChange = {
                            targetPackages = it
                            AppPrefs.setTargetPackagesRaw(context, it)
                            pinnedPackage = AppPrefs.getPinnedPackage(context)
                            ShizukuController.refreshTaskWatch()
                        },
                        label = { Text(stringResource(R.string.field_target_packages)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notifKeywords,
                        onValueChange = {
                            notifKeywords = it
                            AppPrefs.setNotifKeywordsRaw(context, it)
                        },
                        label = { Text(stringResource(R.string.field_notif_keywords)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        stringResource(R.string.slider_delay_label, delayMs.roundToLong()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = delayMs,
                        onValueChange = { delayMs = it },
                        onValueChangeFinished = { AppPrefs.setDelayMs(context, delayMs.roundToLong()) },
                        valueRange = 0f..500f
                    )
                }
            }

            // 7. 即時攔截紀錄
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
                        Text(
                            stringResource(R.string.log_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { InterceptLogStore.clear() }) {
                            Text(stringResource(R.string.btn_clear))
                        }
                    }
                    if (logs.isEmpty()) {
                        Text(
                            stringResource(R.string.log_empty),
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

            // 8. 關於我與檢查更新卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.about_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.about_version_fixed, BuildConfig.VERSION_NAME),
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
                            Text(stringResource(R.string.btn_about_github))
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
                                                context.getString(R.string.toast_latest_version, info.currentVersion),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_update_failed, err.message ?: ""),
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
                            Text(
                                if (isCheckingUpdate) stringResource(R.string.btn_checking_update)
                                else stringResource(R.string.btn_check_update)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // 彈窗 A：自動判定本機陸版語音助理 & 讓使用者確認或改選
    if (showDetectDialog) {
        var selectedProfile by remember(pinnedProfileId) { mutableStateOf(pinnedProfileId) }
        var customPkgInput by remember(pinnedPackage) { mutableStateOf(pinnedPackage) }

        val options = listOf(
            Triple(AppPrefs.PROFILE_VIVO, stringResource(R.string.assistant_name_vivo), "com.vivo.ai.copilot"),
            Triple(AppPrefs.PROFILE_OPPO, stringResource(R.string.assistant_name_oppo), "com.heytap.speechassist"),
            Triple(AppPrefs.PROFILE_XIAOMI, stringResource(R.string.assistant_name_xiaomi), "com.miui.voiceassist"),
            Triple(AppPrefs.PROFILE_CUSTOM, stringResource(R.string.assistant_name_custom), customPkgInput)
        )

        AlertDialog(
            onDismissRequest = {
                AppPrefs.setAssistantConfirmed(context, true)
                showDetectDialog = false
            },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_detect_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.dialog_detect_subtitle,
                            detectedInfo.deviceBrandModel,
                            profileDisplayName(detectedInfo.profileId),
                            detectedInfo.pinnedPackage
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                    options.forEach { (id, label, defaultPkg) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProfile = id
                                    if (id != AppPrefs.PROFILE_CUSTOM) {
                                        customPkgInput = if (detectedInfo.profileId == id) detectedInfo.pinnedPackage else defaultPkg
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfile == id,
                                onClick = {
                                    selectedProfile = id
                                    if (id != AppPrefs.PROFILE_CUSTOM) {
                                        customPkgInput = if (detectedInfo.profileId == id) detectedInfo.pinnedPackage else defaultPkg
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(label, fontWeight = FontWeight.SemiBold)
                                if (id != AppPrefs.PROFILE_CUSTOM) {
                                    val pkgText = if (detectedInfo.profileId == id) detectedInfo.pinnedPackage else defaultPkg
                                    Text(
                                        pkgText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (selectedProfile == AppPrefs.PROFILE_CUSTOM) {
                        OutlinedTextField(
                            value = customPkgInput,
                            onValueChange = { customPkgInput = it },
                            label = { Text(stringResource(R.string.field_target_packages)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkgOverride = if (selectedProfile == AppPrefs.PROFILE_CUSTOM) customPkgInput else null
                        AppPrefs.applyPinnedAssistantProfile(
                            context = context,
                            profileId = selectedProfile,
                            customPackage = pkgOverride,
                            markConfirmed = true
                        )
                        pinnedProfileId = AppPrefs.getPinnedProfileId(context)
                        pinnedPackage = AppPrefs.getPinnedPackage(context)
                        targetPackages = AppPrefs.getTargetPackagesRaw(context)
                        notifKeywords = AppPrefs.getNotifKeywordsRaw(context)
                        ShizukuController.refreshTaskWatch()
                        showDetectDialog = false

                        val confirmedLabel = when (pinnedProfileId) {
                            AppPrefs.PROFILE_VIVO -> context.getString(R.string.assistant_name_vivo)
                            AppPrefs.PROFILE_OPPO -> context.getString(R.string.assistant_name_oppo)
                            AppPrefs.PROFILE_XIAOMI -> context.getString(R.string.assistant_name_xiaomi)
                            else -> context.getString(R.string.assistant_name_custom)
                        }
                        InterceptLogStore.add("已鎖定單一陸版助理指標：$confirmedLabel ($pinnedPackage)")
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_pinned_confirmed, confirmedLabel, pinnedPackage),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.btn_confirm_pin))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        AppPrefs.setAssistantConfirmed(context, true)
                        showDetectDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // 彈窗 0：語言切換對話框 (繁體中文 / 简体中文 / English / 跟隨系統)
    if (showLangDialog) {
        val options = listOf(
            "system" to stringResource(R.string.lang_system),
            "zh-TW" to stringResource(R.string.lang_zh_tw),
            "zh-CN" to stringResource(R.string.lang_zh_cn),
            "en" to stringResource(R.string.lang_en)
        )
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            icon = { Icon(Icons.Default.Language, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_lang_title)) },
            text = {
                Column {
                    options.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentLang = code
                                    AppPrefs.setAppLanguage(context, code)
                                    showLangDialog = false
                                    (context as? Activity)?.recreate()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLang == code,
                                onClick = {
                                    currentLang = code
                                    AppPrefs.setAppLanguage(context, code)
                                    showLangDialog = false
                                    (context as? Activity)?.recreate()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
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

    // 彈窗 2：關於我（中英文雙語說明 + GitHub 連結）
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_about_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_about_body, BuildConfig.VERSION_NAME))
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAboutDialog = false
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.GITHUB_REPO_URL))
                    )
                }) {
                    Text(stringResource(R.string.btn_open_github))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // 彈窗 3：發現新版本更新對話框
    updateInfoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfoDialog = null },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_update_title, info.latestVersion)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_update_version, info.currentVersion, info.latestVersion))
                    Text(stringResource(R.string.dialog_update_desc))
                    if (info.releaseNotes.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.dialog_update_notes, info.releaseNotes),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val url = info.apkDownloadUrl ?: info.releasePageUrl
                    updateInfoDialog = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) {
                    Text(stringResource(R.string.btn_download_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfoDialog = null }) {
                    Text(stringResource(R.string.btn_later))
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
        title = { Text(stringResource(R.string.dialog_pick_app_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.dialog_pick_app_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    Text(stringResource(R.string.dialog_pick_app_loading), style = MaterialTheme.typography.bodySmall)
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
                Text(stringResource(R.string.btn_cancel))
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
