package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ==============================================================================
// BASE DROID V2 - NETWORK MODEL FOR GEMINI LIGHTWEIGHT API
// ==============================================================================
object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        // Handle missing key gracefully
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API_KEY_UNAVAILABLE"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are the Base Droid OS integrated intelligence engine. Provide a direct, helpful, system optimization recommendation or scientific answer for custom OS. User query: $prompt")
                        })
                    })
                })
            })
        }

        val body = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            jsonPayload.toString()
        )

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Signal transceiver error: HTTP ${response.code}"
                }
                val rawJson = response.body?.string() ?: return@withContext "Empty system transmission"
                val jsonObject = JSONObject(rawJson)
                val candidates = jsonObject.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "Telemetry parsing mismatch.")
                        }
                    }
                }
                "Malformed data transmission packet."
            }
        } catch (e: Exception) {
            "Diagnostic link timeout: ${e.localizedMessage}. Check link state."
        }
    }
}

// ==============================================================================
// BASE DROID OS APPLICATIONS REPRESENTATION
// ==============================================================================
enum class BaseDroidTheme(val displayName: String, val bgGradients: List<Color>, val baseUiColor: Color) {
    NEON_GRID("Neon Cyber Grid", listOf(Color(0xFF050505), Color(0xFF0B1424), Color(0xFF111E30)), Color(0xFF22D3EE)),
    ABYSSAL_SLATE("Abyssal Obsidian", listOf(Color(0xFF050505), Color(0xFF0E0E14), Color(0xFF241635)), Color(0xFF9333EA)),
    SOLAR_FLARE("Plasma Surge", listOf(Color(0xFF050505), Color(0xFF1A0A0C), Color(0xFF330E14)), Color(0xFFF97316))
}

data class SystemAppItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val securityLevel: String = "SECURE_SANDBOX",
    val packageSize: String = "12 MB"
)

data class CodeFile(
    val name: String,
    val language: String,
    var content: String
)

data class ChatMessage(
    val sender: String,
    val message: String,
    val timestamp: String,
    val isUser: Boolean
)

data class FloatingWindow(
    val id: String,
    val title: String,
    val icon: ImageVector,
    var xOffset: Float,
    var yOffset: Float,
    val appType: String
)

// ==============================================================================
// STATE MANAGEMENT VIEWMODEL
// ==============================================================================
class BaseDroidViewModel : ViewModel() {
    // OS Initialization & Boot
    private val _bootState = MutableStateFlow<BootProgressState>(BootProgressState.Booting(0f))
    val bootState: StateFlow<BootProgressState> = _bootState.asStateFlow()

    private val _bootLogs = MutableStateFlow<List<String>>(emptyList())
    val bootLogs: StateFlow<List<String>> = _bootLogs.asStateFlow()

    // Active Configurations
    val activeTheme = MutableStateFlow(BaseDroidTheme.NEON_GRID)
    val accentColor = MutableStateFlow(Color(0xFF00FFCC))
    val secureVpnActive = MutableStateFlow(false)
    val systemLockActive = MutableStateFlow(false)
    val appVerifierActive = MutableStateFlow(true)
    val refreshRate = MutableStateFlow(120) // 60, 120, 144 Hz

    // Simulated Metrics
    val ramAvailable = MutableStateFlow(11.4f) // GB available in 16GB
    val batteryHealth = MutableStateFlow("EXCELLENT")
    val batteryTemperature = MutableStateFlow(31.5f) // °C
    val cpuProfile = MutableStateFlow("BALANCED_POWER") // ECO, BALANCED, GAMING_EXTREME
    val systemOptimizing = MutableStateFlow(false)

    // Store State
    val storeQuery = MutableStateFlow("")
    val installedApps = MutableStateFlow(setOf("terminal", "security", "customization"))
    val downloadingAppsStatus = MutableStateFlow<Map<String, Float>>(emptyMap()) // map app id -> progress

    // Terminal Commands State
    val commandInput = MutableStateFlow("")
    private val _terminalLogs = MutableStateFlow<List<String>>(listOf(
        "Base Droid OS Terminal v2.0.1 (KNL-6.12.15)",
        "Type 'help' to verify connected core subsystems.",
        "firmware@basedroid:~$ "
    ))
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // Security Scan State
    val isScanningMalware = MutableStateFlow(false)
    val scanProgress = MutableStateFlow(0f)
    val scannedFilesCount = MutableStateFlow(0)
    val systemMalwareStatus = MutableStateFlow("VERIFIED_CLEAN")

    // AI Tracing Assistant
    val aiQueryInput = MutableStateFlow("")
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("Neural Core", "Base Droid neural transceiver active. How can I optimize your custom build?", getFormattedTime(), false)
    ))
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()
    val isAiLoading = MutableStateFlow(false)

    // Code Editor Files State
    val currentSelectedFileIndex = MutableStateFlow(0)
    val codeFiles = MutableStateFlow(listOf(
        CodeFile("bootloader.py", "Python", """# Base Droid High-Speed Bootloader Init Overlay
import os
import sys

def check_hardware_signature():
    print("[HARDWARE] Verifying Qualcomm SM8650 cryptographic keys...")
    sig = os.getenv("OEM_SIGNATURE")
    if sig == "CYBER_BASE_DROID_V2_PROD":
        return True
    return False

if __name__ == '__main__':
    if check_hardware_signature():
        sys.exit(0)
    sys.exit(1)
"""),
        CodeFile("index.html", "HTML", """<!DOCTYPE html>
<html>
<head>
    <title>Base Droid OS HUD</title>
    <style>
        body { background: #080B10; color: #00FFCC; font-family: monospace; }
        .hud { border: 1px solid #7000FF; padding: 20px; text-shadow: 0 0 10px #00FFCC; }
    </style>
</head>
<body>
    <div class="hud">
        <h2>BASE_DROID_HUD ACTIVE</h2>
        <p>AOSP System Overlays Compiled.</p>
    </div>
</body>
</html>
"""),
        CodeFile("app_lock.ts", "TypeScript", """// Base Droid Secure Virtual Space App Locker
export class AppLockManager {
    private isEnforced: boolean = true;
    private keystate: string = "SHA-512_ENCRYPTED_BLOB";

    public authenticate(hash: string): boolean {
        console.log("Verifying cryptographic payload...");
        return hash === this.keystate;
    }
}
""")
    ))

    // Real-time AOSP Compilation Simulator
    val aospTargetChipset = MutableStateFlow("Qualcomm SM8650 (Snapdragon 8 Gen 3)")
    val aospPartitionTarget = MutableStateFlow("systemimage (Full system overlay)")
    val isCompilingAosp = MutableStateFlow(false)
    val compilationProgress = MutableStateFlow(0f)
    private val _compilationLogs = MutableStateFlow<List<String>>(emptyList())
    val compilationLogs: StateFlow<List<String>> = _compilationLogs.asStateFlow()

    // Floating windows Multitasking state
    private val _openFloatingWindows = MutableStateFlow<List<FloatingWindow>>(emptyList())
    val openFloatingWindows: StateFlow<List<FloatingWindow>> = _openFloatingWindows.asStateFlow()

    init {
        triggerBootSequence()
    }

    private fun triggerBootSequence() {
        val testLogs = listOf(
            "[    0.000000] Booting Linux kernel v6.12.15-basedroid-v2...",
            "[    0.052140] Initializing CPU cores configuration: 8 physical hyper-threads.",
            "[    0.180292] [HARDWARE] Detecting Qualcomm SM8650 SoC architecture...",
            "[    0.342110] Memory: 11984284K/16777216K available (AES-256 hardware acceleration enabled).",
            "[    0.512349] [SECURITY] Base Droid sandboxed storage vault mounted.",
            "[    0.803452] Base Droid security engine loaded safely (malware scanner init).",
            "[    1.092104] Connecting AOSP hardware abstraction layer (HAL) drivers...",
            "[    1.410432] Mounting dual partitions system_a / system_b for seamless updates...",
            "[    1.802911] Initializing Base Droid Launcher core UI...",
            "[    2.124501] Loading integrated Google Gemini Transceiver..."
        )
        
        // Feed boot logs progressively and increment progress
        var index = 0
        val handler = Handler(Looper.getMainLooper())
        
        val progressRunnable = object : Runnable {
            var currentProgress = 0f
            override fun run() {
                if (currentProgress < 1.0f) {
                    currentProgress += 0.05f
                    _bootState.value = BootProgressState.Booting(currentProgress)
                    
                    if (index < testLogs.size) {
                        _bootLogs.value = _bootLogs.value + testLogs[index]
                        index++
                    }
                    handler.postDelayed(this, 180)
                } else {
                    _bootState.value = BootProgressState.Finished
                }
            }
        }
        handler.post(progressRunnable)
    }

    fun skipBoot() {
        _bootState.value = BootProgressState.Finished
    }

    // Interactive Optimizer Task
    fun performOptimize(scope: kotlinx.coroutines.CoroutineScope) {
        if (systemOptimizing.value) return
        systemOptimizing.value = true
        scope.launch {
            delay(1500)
            ramAvailable.value = 14.1f // increase available RAM
            batteryTemperature.value = 29.8f // lower temperature
            systemOptimizing.value = false
        }
    }

    // Toggle VPN
    fun toggleVpn() {
        secureVpnActive.value = !secureVpnActive.value
        _terminalLogs.value = _terminalLogs.value + listOf(
            "firmware@basedroid:~$ ip vpn status toggle",
            "[VPN] Interface toggled. Secure Link state: ${if (secureVpnActive.value) "ACTIVE" else "DISCONNECTED"}",
            "firmware@basedroid:~$ "
        )
    }

    // Malware Scanner Simulation
    fun triggerMalwareScan(scope: kotlinx.coroutines.CoroutineScope) {
        if (isScanningMalware.value) return
        isScanningMalware.value = true
        scanProgress.value = 0f
        scannedFilesCount.value = 0
        systemMalwareStatus.value = "SCANNING..."
        
        scope.launch {
            val systemsToScan = listOf("/system/priv-app", "/vendor/firmware", "/bin/bootloader", "/data/user/0/vault", "/etc/hosts")
            for (i in 1..20) {
                delay(120)
                scanProgress.value = (i / 20f)
                scannedFilesCount.value += (12 + (Math.random() * 25).toInt())
                if (i % 4 == 0) {
                    val currentPath = systemsToScan[(i/4 - 1) % systemsToScan.size]
                    _terminalLogs.value = _terminalLogs.value + "[SECURITY] Scanning directory: $currentPath"
                }
            }
            systemMalwareStatus.value = "VERIFIED_CLEAN"
            isScanningMalware.value = false
        }
    }

    // Interactive Shell Executor
    fun executeShellCommand(cmd: String) {
        if (cmd.trim().isEmpty()) return
        
        val rawCmd = cmd.trim()
        val currentLogs = _terminalLogs.value.toMutableList()
        currentLogs.add("firmware@basedroid:~$ $rawCmd")
        
        val parts = rawCmd.split(" ")
        val baseCommand = parts[0].lowercase()
        
        when (baseCommand) {
            "help" -> {
                currentLogs.addAll(listOf(
                    "Available Core Executables:",
                    "  neofetch       - Display Base Droid system build metrics.",
                    "  uname -a       - Show custom Linux Kernel status.",
                    "  aosp-stats     - View physical partitions allocation details.",
                    "  secure-scan    - Execute security auditing scan packages.",
                    "  roll-update    - Trigger firmware rollback capability checks.",
                    "  clear          - Flush active console buffers.",
                    "  git status     - Check local compiler integration branch state."
                ))
            }
            "neofetch" -> {
                currentLogs.addAll(listOf(
                    "       _---~~---_        BASE DROID v2.0 - CUSTOM Android OS",
                    "     /~          ~\\      ------------------------------------",
                    "    |   ●     ●    |     baseline     : AOSP 14.0.0 (r50)",
                    "    |   __---__    |     kernel       : Linux v6.12.15-basedroid-v2",
                    "     \\_         _/      architecture : ARM64-v8a",
                    "       \\_ ___ _/         refresh_rate : ${refreshRate.value} Hz",
                    "                         ram_state    : ${(16.0f - ramAvailable.value).toString().take(4)} GB / 16.0 GB",
                    "                         active_vpn   : ${if (secureVpnActive.value) "CONNECTED (Calyx_Encrypted)" else "DISCONNECTED"}"
                ))
            }
            "uname", "uname -a" -> {
                currentLogs.add("Linux basedroid-v2 6.12.15-basedroid-v2 #1 SMP PREEMPT Sat Jun 20 UTC 2026 aarch64 Android")
            }
            "aosp-stats" -> {
                currentLogs.addAll(listOf(
                    "Partition Allocations (SM8650 storage size: 512GB):",
                    "  /system_a      [EXT4] : 4.8 GB / 8.0 GB (USED: 60%)",
                    "  /system_b      [EXT4] : 4.8 GB / 8.0 GB (USED: 60%)",
                    "  /vendor        [EROFS]: 1.2 GB / 2.0 GB (USED: 60%)",
                    "  /data          [F2FS] : 112 GB / 494 GB (USED: 22%)",
                    "  Recovery State : VERIFIED SECURE BOOT (AVB 2.0)"
                ))
            }
            "secure-scan" -> {
                currentLogs.add("[AOSP SECURITY] Running threat index scans: Verified 0 malware signatures.")
            }
            "roll-update" -> {
                currentLogs.addAll(listOf(
                    "[OTA ENG] Target partition validation: OK.",
                    "[OTA ENG] Checking rollback compatibility... Supported. Ready for AOSP OTA compilation system."
                ))
            }
            "clear" -> {
                currentLogs.clear()
                currentLogs.add("Base Droid OS Terminal v2.0.1 (KNL-6.12.15)")
            }
            "git", "git status" -> {
                currentLogs.addAll(listOf(
                    "On branch master (Base Droid custom patch baseline)",
                    "Your branch is up to date with 'origin/master'.",
                    "Changes not staged for commit:",
                    "  (use \"git add <file>...\" to update what will be committed)",
                    "\tmodified:   frameworks/base/core/res/res/values/themes.xml (Accent overlays changed)",
                    "no changes added to commit (use \"git add\" and \"git commit -a\")"
                ))
            }
            else -> {
                currentLogs.add("shell: command not found: $baseCommand. Type 'help' for available software bundles.")
            }
        }
        
        currentLogs.add("firmware@basedroid:~$ ")
        _terminalLogs.value = currentLogs
        commandInput.value = ""
    }

    // AOSP Simulator Compile Engine
    fun runAospCompile(scope: kotlinx.coroutines.CoroutineScope) {
        if (isCompilingAosp.value) return
        isCompilingAosp.value = true
        compilationProgress.value = 0f
        _compilationLogs.value = listOf(
            "[AOSP CORE] Launching compilation: TARGET=$aospTargetChipset, PARTITION=$aospPartitionTarget",
            "[AOSP CORE] Initializing compiler environments: make -j\$(nproc) $aospPartitionTarget",
            "[AOSP CORE] Sourcing out/config.mk parameters..."
        )

        scope.launch {
            val progressSteps = listOf(
                "[CC] device/basedroid/custom/init.cpp",
                "[CC] vendor/basedroid/security/sandboxing.cpp",
                "[LD] libbasedroid_security.so",
                "[CC] frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java",
                "[CC] frameworks/base/core/java/android/view/View.java",
                "[DEX] out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.dex",
                "[APK] Outbound/priv-app/BaseDroidLauncher.apk",
                "[IMG] Building system.img - filesystem: EROFS",
                "[IMG] Computing cryptographic hash SHA-256 for systempartition",
                "[OTA] Generation of full payload.bin OTA bundle... Finished!"
            )

            for (i in 1..20) {
                delay(250)
                compilationProgress.value = (i / 20f)
                if (i % 2 == 0) {
                    val logIndex = (i / 2) - 1
                    if (logIndex < progressSteps.size) {
                        _compilationLogs.value = _compilationLogs.value + progressSteps[logIndex]
                    }
                }
            }
            _compilationLogs.value = _compilationLogs.value + listOf(
                "==================================================",
                "[BUILD SUCCESS] Android firmware bundle created successfully!",
                "[COMPILER] Target binary size: 2.14 GB",
                "[HELP] Connect your test device in fastboot and execute: fastboot flashall"
            )
            isCompilingAosp.value = false
        }
    }

    // AI Chat Engine calling Gemini
    fun sendAiPrompt(scope: kotlinx.coroutines.CoroutineScope) {
        val prompt = aiQueryInput.value.trim()
        if (prompt.isEmpty() || isAiLoading.value) return

        val newMsg = ChatMessage("User", prompt, getFormattedTime(), true)
        _chatHistory.value = _chatHistory.value + newMsg
        aiQueryInput.value = ""
        isAiLoading.value = true

        scope.launch {
            val response = GeminiClient.generateContent(prompt)
            val aiMsg = if (response == "API_KEY_UNAVAILABLE") {
                // Return descriptive, highly thematic recommendations in case key is missing
                ChatMessage(
                    "Neural Core",
                    "Base Droid v2 intelligence system is operational. (API key not detected. Showing local Base Droid device optimizations recommendation):\n\n" +
                            "💡 **Memory Tuning**: Change the performance profiles inside core settings to 'GAMING_EXTREME' to speed up CPU clocks.\n" +
                            "💡 **VPN Protection**: Activate the encrypted Calyx VPN protocol within your security panel to safeguard diagnostic packets.\n" +
                            "💡 **Firmware Builds**: Go to the 'AOSP Build Engine' tab and run compilation metrics for MediaTek Dimensity or Snapdragon platforms seamlessly.",
                    getFormattedTime(),
                    false
                )
            } else {
                ChatMessage("Neural Core", response, getFormattedTime(), false)
            }
            _chatHistory.value = _chatHistory.value + aiMsg
            isAiLoading.value = false
        }
    }

    // Floating windows controls
    fun openAppInFloatingWindow(appId: String, title: String, icon: ImageVector) {
        val alreadyOpenIndex = _openFloatingWindows.value.indexOfFirst { it.id == appId }
        if (alreadyOpenIndex != -1) {
            // Bring to search/focus front
            val currentList = _openFloatingWindows.value.toMutableList()
            val win = currentList.removeAt(alreadyOpenIndex)
            currentList.add(win)
            _openFloatingWindows.value = currentList
            return
        }
        val randomX = 50f + (Math.random() * 150f).toFloat()
        val randomY = 150f + (Math.random() * 150f).toFloat()
        val nextWin = FloatingWindow(appId, title, icon, randomX, randomY, appId)
        _openFloatingWindows.value = _openFloatingWindows.value + nextWin
    }

    fun closeFloatingWindow(appId: String) {
        _openFloatingWindows.value = _openFloatingWindows.value.filterNot { it.id == appId }
    }

    fun updateFloatingWindowPosition(appId: String, newX: Float, newY: Float) {
        _openFloatingWindows.value = _openFloatingWindows.value.map {
            if (it.id == appId) {
                it.copy(xOffset = newX, yOffset = newY)
            } else {
                it
            }
        }
    }

    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}

sealed interface BootProgressState {
    data class Booting(val progress: Float) : BootProgressState
    object Finished : BootProgressState
}

// ==============================================================================
// MAIN ACTIVITY
// ==============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BaseDroidSystemView()
            }
        }
    }
}

// ==============================================================================
// COMPOSABLE VISUALS
// ==============================================================================
@Composable
fun BaseDroidSystemView(viewModel: BaseDroidViewModel = viewModel()) {
    val bootState by viewModel.bootState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF050505)
    ) {
        Crossfade(targetState = bootState, label = "boot_screen_crossfade") { state ->
            when (state) {
                is BootProgressState.Booting -> {
                    BootAnimationScreen(
                        progress = state.progress,
                        viewModel = viewModel
                    )
                }
                is BootProgressState.Finished -> {
                    MainSystemDashboard(viewModel = viewModel)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 1. BOOT SEQUENCE SCREEN (Displays cyber logo and raw Linux kernel logs)
// ------------------------------------------------------------------------------
@Composable
fun BootAnimationScreen(progress: Float, viewModel: BaseDroidViewModel) {
    val logs by viewModel.bootLogs.collectAsState()
    val scrollState = rememberLazyListState()

    // Autoscroll logs when new lines arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        // Glowing cyber background details
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF00FFCC).copy(alpha = 0.04f),
                radius = size.minDimension * 0.4f,
                center = Offset(size.width / 2, size.height * 0.4f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Floating boot skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { viewModel.skipBoot() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("skip_boot_button")
                ) {
                    Text("SKIP BOOT", color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Kernel Raw Logs output Box at the top half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .background(Color(0xFF010206))
                    .padding(12.dp)
            ) {
                LazyColumn(state = scrollState) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("[SECURITY]")) Color(0xFF9A52FF) else if (log.contains("[HARDWARE]")) Color(0xFFFF9E00) else Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Main Base Droid v2 glowing Logo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large Cyber Emblem representation
                Image(
                    painter = painterResource(id = R.drawable.img_basedroid_logo_1781986116934),
                    contentDescription = "Base Droid logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF00FFCC), CircleShape)
                        .shadow(20.dp, CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "BASE DROID OS v2.0",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.SansSerif,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFF00FFCC),
                            offset = Offset(0f, 0f),
                            blurRadius = 10f
                        )
                    )
                )

                Text(
                    text = "Secure AOSP Custom Platform Interface",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Real-time Loading metrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Loading Modules: ${(progress * 100).toInt()}%",
                        color = Color(0xFF00FFCC),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Qualcomm Platform SM8650",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF00FFCC),
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------
// 2. MAIN ACTIVE CONTROL OS PORTAL
// ------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSystemDashboard(viewModel: BaseDroidViewModel) {
    val scope = rememberCoroutineScope()
    val activeThemeState by viewModel.activeTheme.collectAsState()
    val accentColorState by viewModel.accentColor.collectAsState()
    val isVpnActive by viewModel.secureVpnActive.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var activeFullscreenApp by remember { mutableStateOf<String?>(null) }

    // Dynamic wallpapers background gradient mapping
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = activeThemeState.bgGradients,
                        center = Offset(size.width / 2f, size.height * 0.4f),
                        radius = size.maxDimension * 0.9f
                    )
                )
            }
    ) {
        // Multi-layered Grid background overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val columns = 16
            val rows = 32
            val cellWidth = size.width / columns
            val cellHeight = size.height / rows
            for (i in 0..columns) {
                drawLine(
                    color = accentColorState.copy(alpha = 0.015f),
                    start = Offset(i * cellWidth, 0f),
                    end = Offset(i * cellWidth, size.height),
                    strokeWidth = 1f
                )
            }
            for (i in 0..rows) {
                drawLine(
                    color = accentColorState.copy(alpha = 0.015f),
                    start = Offset(0f, i * cellHeight),
                    end = Offset(size.width, i * cellHeight),
                    strokeWidth = 1f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Futuristic Custom Status HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "BASE_DROID v2.0_ENG",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColorState)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURE_SANDBOX: ONLINE",
                            color = accentColorState,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isVpnActive) {
                        Surface(
                            color = Color(0xFF00FFCC).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF00FFCC))
                        ) {
                            Text(
                                text = "VPN_ACTIVE",
                                color = Color(0xFF00FFCC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Power Source",
                            tint = accentColorState,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "100%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "13:07 UTC",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top Banner Hero (Polished UI Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cyber_banner_1781986128849),
                        contentDescription = "Cyber grid banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Semi-transparent color shader overlay
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF050505).copy(alpha = 0.90f))
                        )
                    ))
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "DEVELOPER WORKBENCH",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Build, deploy, and analyze standard AOSP modules in deep sandbox spaces.",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // High aesthetic Category Tabs Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
            ) {
                val menuTabs = listOf("LAUNCHER", "AOSP BUILDER", "HANDBOOK")
                menuTabs.forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = index }
                            .background(if (selectedTab == index) accentColorState.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (selectedTab == index) accentColorState else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Primary Tab Navigation Render
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> LaunchpadScreen(
                        viewModel = viewModel,
                        onAppSelected = { activeFullscreenApp = it }
                    )
                    1 -> AospBuildWorkbenchScreen(viewModel = viewModel)
                    2 -> BaseDroidHandbookScreen()
                }

                // Render App Overlay Dialog when fullscreen app selected
                if (activeFullscreenApp != null) {
                    FullscreenAppOverlay(
                        appId = activeFullscreenApp!!,
                        viewModel = viewModel,
                        onClose = { activeFullscreenApp = null }
                    )
                }
            }

            // Bottom Navigation Gesture pill spacing
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        // Render Floating Multitasking Canvas inside the Box (Overlay Window elements)
        val floatingWindows by viewModel.openFloatingWindows.collectAsState()
        floatingWindows.forEach { win ->
            FloatingWindowWrapper(
                window = win,
                viewModel = viewModel,
                accentColor = accentColorState
            )
        }
    }
}

// ------------------------------------------------------------------------------
// TAB 0: LAUNCHER & THEME ADJUSTMENT VIEWS
// ------------------------------------------------------------------------------
@Composable
fun LaunchpadScreen(
    viewModel: BaseDroidViewModel,
    onAppSelected: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val accentColor by viewModel.accentColor.collectAsState()
    val isVpnActive by viewModel.secureVpnActive.collectAsState()
    val memoryAvail by viewModel.ramAvailable.collectAsState()
    val systemLock by viewModel.systemLockActive.collectAsState()
    val optimizerActive by viewModel.systemOptimizing.collectAsState()

    // Base applications registered
    val sysAppsList = remember {
        listOf(
            SystemAppItem("terminal", "Shell Terminal", "Direct AOSP kernel configuration utility & boot logs monitor.", Icons.Default.PlayArrow, "ROOT_SHELL"),
            SystemAppItem("ai_assistant", "Hermes AI Assistant", "Neural deep-search optimization engine utilizing Google Gemini.", Icons.Default.Star, "INTELLIGENCE_LAYER"),
            SystemAppItem("code_editor", "Hydra Editor", "Compile-enabled mobile repository code editor with sample packages.", Icons.Default.Edit, "FILE_SPACE_SANDBOX"),
            SystemAppItem("security", "Base Security Safeguard", "Real-time threat auditor, permission logs management, & virus scans.", Icons.Default.Lock, "SECURITY_ENFORCED"),
            SystemAppItem("store", "Verified Store", "Download, update, and deploy cryptographic checked open source bundles.", Icons.Default.ShoppingCart, "CRYPT_VERIFIED"),
            SystemAppItem("customization", "Matrix Themes", "Tweak wallpapers, layout accentuation and color matrices.", Icons.Default.Settings, "USER_SPACE")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Quick Optimization Performance Widget
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF164E63).copy(alpha = 0.40f),
                                    Color(0xFF172554).copy(alpha = 0.40f)
                                )
                            )
                        )
                ) {
                    // Concentric Circle Ring Decorations (Top Right)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val centerOffset = Offset(size.width - 24.dp.toPx(), 24.dp.toPx())
                        drawCircle(
                            color = Color(0xFF22D3EE),
                            radius = 48.dp.toPx(),
                            center = centerOffset,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                            alpha = 0.20f
                        )
                        drawCircle(
                            color = Color(0xFF22D3EE),
                            radius = 32.dp.toPx(),
                            center = centerOffset,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                            alpha = 0.20f
                        )
                    }

                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Performance Engine".uppercase(Locale.getDefault()),
                            color = Color(0xFF22D3EE).copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (optimizerActive) "94" else "98",
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = "%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Live RAM gauge
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MEMORY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                Text("${(16.0f - memoryAvail).toString().take(4)} / 16.0 GB", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (16.0f - memoryAvail) / 16.0f },
                                    color = Color(0xFF22D3EE),
                                    trackColor = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                                )
                            }

                            // Dynamic Visual Divider (W-px H-8 bg-white/10)
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .align(Alignment.CenterVertically)
                            )

                            // CPU Profiles
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TEMP", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                Text("32°C", color = Color(0xFF22D3EE), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { 0.32f },
                                    color = Color(0xFF22D3EE),
                                    trackColor = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { viewModel.performOptimize(scope) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("run_optimize_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE).copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (optimizerActive) {
                                CircularProgressIndicator(color = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("SWEEPING CORE FLUSHES...", color = Color(0xFF22D3EE), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.Star, contentDescription = "Flash Optimizer", tint = Color(0xFF22D3EE))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("RUN OPTIMIZE & REALLOCATE", color = Color(0xFF22D3EE), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "INSTALLED SYSTEM UTILITIES",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
        }

        // System App Cards Grid representation
        items(sysAppsList.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { app ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_${app.id}")
                            .clickable { onAppSelected(app.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = accentColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(38.dp),
                                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(app.icon, contentDescription = app.title, tint = accentColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                                
                                // Direct launch in Floating option! (Highly adaptive)
                                IconButton(
                                    onClick = { viewModel.openAppInFloatingWindow(app.id, app.title, app.icon) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Float Window",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = app.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = app.description,
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ------------------------------------------------------------------------------
// TAB 1: INTEGRATIVE AOSP COMPILATION & FIRMWARE TOOL
// ------------------------------------------------------------------------------
@Composable
fun AospBuildWorkbenchScreen(viewModel: BaseDroidViewModel) {
    val scope = rememberCoroutineScope()
    val accentColor by viewModel.accentColor.collectAsState()
    val targetChipset by viewModel.aospTargetChipset.collectAsState()
    val targetPartition by viewModel.aospPartitionTarget.collectAsState()
    val compiling by viewModel.isCompilingAosp.collectAsState()
    val progress by viewModel.compilationProgress.collectAsState()
    val logs by viewModel.compilationLogs.collectAsState()
    val scrollState = rememberLazyListState()

    // Autoscroll logs when lines execute
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AOSP TARGET CONFIGURATION",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Target Selector
                    Text("Target Processor Chipset", color = Color(0xFF5F6E86), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF070B14))
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "CPU Target", tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(targetChipset, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Partition Target Selector
                    Text("Build Partition Images", color = Color(0xFF5F6E86), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF070B14))
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "Layers target", tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(targetPartition, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.runAospCompile(scope) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("aosp_compile_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, accentColor),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !compiling
                    ) {
                        if (compiling) {
                            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("COMPILING... ${(progress * 100).toInt()}%", color = accentColor, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Launch compile", tint = accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COMPILE BASE_DROID ROM TARGET", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Live Compiler Logs Monitor Console
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "ROM INTERACTIVE BUILD_TERMINAL LOGS",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF010206))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    if (logs.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Awaiting", tint = Color(0xFF334155), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Awaiting compilation parameters. Hit compile above to begin...", color = Color(0xFF334155), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        LazyColumn(state = scrollState) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("[BUILD SUCCESS]")) Color(0xFF00FFCC) else if (log.contains("[CC]")) Color(0xFF94A3B8) else Color(0xFFF1F5F9),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ------------------------------------------------------------------------------
// TAB 3: BASE DROID COMPREHENSIVE TEXT DOCUMENTATION HANDBOOK
// ------------------------------------------------------------------------------
@Composable
fun BaseDroidHandbookScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚀 PORTING INTRO & CONCEPTS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Base Droid v2 is a system built directly on top of AOSP (Android Open Source Project) and security-minded hardening repositories.\n\n" +
                                "It overrides default styling packages inside frameworks/base, replaces default system navigation structures with strict gestural capabilities, provides kernel-level defenses against zero-day sandboxing exploits, and deploys verified sandboxed priv-apps.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "🛡️ ENHANCED SECURITY MODULES",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "• Dual-Key app workspace container encryption.\n" +
                                "• Custom SEPolicy rules forbidding privileged apps from reading external sockets unless flagged locally.\n" +
                                "• Hardware verification layer running SHA-256 boot signatures for OEMs on launch phase.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "🔥 HOW TO BUILD (SUMMARY)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "1. Sync source trees as documented inside the main README.md located in the project's root space.\n" +
                                "2. Apply custom device paths. Copied configs inside local AOSP binder.\n" +
                                "3. Initialize compile profile via build/envsetup.sh.\n" +
                                "4. Compile targets with make and deploy firmware into fastboot ports securely.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ------------------------------------------------------------------------------
// OVERLAY MODAL CARDS REPRESENTING APPLICATIONS (SPLIT SIMULATION OR DRAWER STYLE)
// ------------------------------------------------------------------------------
@Composable
fun FullscreenAppOverlay(
    appId: String,
    viewModel: BaseDroidViewModel,
    onClose: () -> Unit
) {
    val accentColor by viewModel.accentColor.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03050C).copy(alpha = 0.94f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close overlay", tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = appId.uppercase(Locale.getDefault()) + "_CONTAINER",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SANDBOX_SECURE",
                        color = accentColor,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Screen Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E3856), RoundedCornerShape(12.dp))
                    .background(Color(0xFF090E1A))
            ) {
                when (appId) {
                    "terminal" -> TerminalAppView(viewModel = viewModel)
                    "ai_assistant" -> AiAssistantAppView(viewModel = viewModel)
                    "code_editor" -> CodeEditorAppView(viewModel = viewModel)
                    "security" -> SecuritySafeguardAppView(viewModel = viewModel)
                    "store" -> VerifiedStoreAppView(viewModel = viewModel)
                    "customization" -> ThemeCustomizerAppView(viewModel = viewModel)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// APPLICATION SUB-WINDOWS
// ------------------------------------------------------------------------------

// 1. TERMINAL APP
@Composable
fun TerminalAppView(viewModel: BaseDroidViewModel) {
    val logs by viewModel.terminalLogs.collectAsState()
    val command by viewModel.commandInput.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = if (log.contains("[SECURITY]")) Color(0xFFEF4444) else if (log.contains("firmware@basedroid")) accentColor else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF03050C))
                .border(0.5.dp, Color(0xFF2E3856), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$", color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = command,
                onValueChange = { viewModel.commandInput.value = it },
                modifier = Modifier.weight(1f).testTag("terminal_input"),
                placeholder = { Text("enter shell command... (try 'help' or 'neofetch')", color = Color(0xFF475569), fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                singleLine = true
            )
            IconButton(
                onClick = { viewModel.executeShellCommand(command) },
                modifier = Modifier.testTag("terminal_send")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = accentColor)
            }
        }
    }
}

// 2. GEMINI AI ASSISTANT APP
@Composable
fun AiAssistantAppView(viewModel: BaseDroidViewModel) {
    val scope = rememberCoroutineScope()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val aiQuery by viewModel.aiQueryInput.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            scrollState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Conversation history
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
                items(chatHistory) { chat ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalAlignment = if (chat.isUser) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = chat.sender,
                            color = if (chat.isUser) accentColor else Color(0xFFFF9E00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (chat.isUser) Color(0xFF1E293B) else Color(0xFF0F172A))
                                .border(0.5.dp, if (chat.isUser) accentColor.copy(alpha = 0.3f) else Color(0xFF2E3856), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = chat.message,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Command recommendations bar
        val suggestions = listOf("/recommend-memory", "/security-check", "/optimize-device")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggest ->
                Surface(
                    modifier = Modifier.clickable {
                        viewModel.aiQueryInput.value = suggest
                    },
                    color = Color(0xFF131B2E),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        suggest,
                        color = accentColor,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF03050C))
                .border(0.5.dp, Color(0xFF2E3856), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = aiQuery,
                onValueChange = { viewModel.aiQueryInput.value = it },
                modifier = Modifier.weight(1f).testTag("ai_input"),
                placeholder = { Text("Ask Hermes assistant about optimizations...", color = Color(0xFF475569), fontSize = 11.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                singleLine = true
            )
            IconButton(
                onClick = { viewModel.sendAiPrompt(scope) },
                enabled = !isAiLoading,
                modifier = Modifier.testTag("ai_send_button")
            ) {
                if (isAiLoading) {
                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send UI check", tint = accentColor)
                }
            }
        }
    }
}

// 3. SYNTAX HIGHLIGHT CODE EDITOR APP
@Composable
fun CodeEditorAppView(viewModel: BaseDroidViewModel) {
    val selectIndex by viewModel.currentSelectedFileIndex.collectAsState()
    val files by viewModel.codeFiles.collectAsState()
    val accentColorByTheme by viewModel.accentColor.collectAsState()
    val scope = rememberCoroutineScope()
    var consoleOutput by remember { mutableStateOf("Ready to run custom compilers.") }
    var isCompilingFile by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab file bar selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF070B14))
                .border(0.5.dp, Color(0xFF1E293B))
        ) {
            files.forEachIndexed { idx, file ->
                Box(
                    modifier = Modifier
                        .clickable { viewModel.currentSelectedFileIndex.value = idx }
                        .background(if (selectIndex == idx) Color(0xFF0F172A) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        file.name,
                        color = if (selectIndex == idx) Color.White else Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Code Area
        val activeFile = files[selectIndex]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF04060C))
                .border(0.5.dp, Color(0xFF1E293B))
        ) {
            TextField(
                value = activeFile.content,
                onValueChange = {
                    val nextList = files.toMutableList()
                    nextList[selectIndex] = activeFile.copy(content = it)
                    viewModel.codeFiles.value = nextList
                },
                modifier = Modifier.fillMaxSize().testTag("editor_textarea"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(color = Color(0xFFE2E8F0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            )
        }

        // Action Toolbar & Compiler state
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF070B14))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Language: " + activeFile.language,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = {
                    isCompilingFile = true
                    scope.launch {
                        delay(1200)
                        consoleOutput = "Compiling ${activeFile.name}... SUCCESS!\n[LOADER] Executed in Virtual Space Container: Exit code 0."
                        isCompilingFile = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColorByTheme.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, accentColorByTheme),
                shape = RoundedCornerShape(6.dp)
            ) {
                if (isCompilingFile) {
                    CircularProgressIndicator(color = accentColorByTheme, modifier = Modifier.size(12.dp))
                } else {
                    Icon(Icons.Default.Build, contentDescription = "Compile", tint = accentColorByTheme, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COMPILE & TEST", color = accentColorByTheme, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Minimalist console panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color(0xFF010204))
                .border(0.5.dp, Color(0xFF1E293B))
                .padding(8.dp)
        ) {
            Text(
                consoleOutput,
                color = Color(0xFF10B981),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

// 4. SECURITY AUDIT APP WITH MALWARE SCANNER
@Composable
fun SecuritySafeguardAppView(viewModel: BaseDroidViewModel) {
    val scope = rememberCoroutineScope()
    val isScanning by viewModel.isScanningMalware.collectAsState()
    val scanProg by viewModel.scanProgress.collectAsState()
    val scanCount by viewModel.scannedFilesCount.collectAsState()
    val textStatus by viewModel.systemMalwareStatus.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()

    // Preferences states
    val lockState by viewModel.systemLockActive.collectAsState()
    val verifierState by viewModel.appVerifierActive.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Text("SYSTEM THREAT AUDITOR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Base Droid deploys internal sandboxed scanning parameters periodically.", color = Color(0xFF64748B), fontSize = 10.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Large Scanner Widget
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = textStatus,
                    color = if (textStatus == "VERIFIED_CLEAN") Color(0xFF10B981) else Color(0xFFFFCC00),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Files Scanned: $scanCount",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { scanProg },
                    color = accentColor,
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.triggerMalwareScan(scope) },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, accentColor),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("EXECUTE FULL MALWARE AUDIT", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Toggle configurations
        Text("ACTIVE PREVENTATIVE SYSTEMS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Enforce Cryptographic Lock", color = Color.White, fontSize = 12.sp)
                Text("Encrypts RAM structures under AES-256", color = Color(0xFF64748B), fontSize = 10.sp)
            }
            Switch(
                checked = lockState,
                onCheckedChange = { viewModel.systemLockActive.value = it },
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.4f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Automatic Live Verifier", color = Color.White, fontSize = 12.sp)
                Text("Precheck binaries signature key matching", color = Color(0xFF64748B), fontSize = 10.sp)
            }
            Switch(
                checked = verifierState,
                onCheckedChange = { viewModel.appVerifierActive.value = it },
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.4f))
            )
        }
    }
}

// 5. OFFICIAL STORE APPLICATION APP
@Composable
fun VerifiedStoreAppView(viewModel: BaseDroidViewModel) {
    val query by viewModel.storeQuery.collectAsState()
    val installed by viewModel.installedApps.collectAsState()
    val downloadProgress by viewModel.downloadingAppsStatus.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val scope = rememberCoroutineScope()

    val appsAvailable = remember {
        listOf(
            SystemAppItem("terminal", "Base shell terminal overlay", "Run root partition inspects seamlessly", Icons.Default.PlayArrow, packageSize = "4 MB"),
            SystemAppItem("vpn_service", "Calyx Encrypted VPN Pro", "Deploys secure peer-to-peer sandboxed link layer", Icons.Default.Lock, packageSize = "18 MB"),
            SystemAppItem("debugger", "AOSP Bridge Auditor", "Logs viewer, thread monitors, & tracing utilities", Icons.Default.Build, packageSize = "15 MB"),
            SystemAppItem("editor", "React Scripting Sandbox", "Interactive typescript compiler tool configurations", Icons.Default.Edit, packageSize = "32 MB")
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("BASE DROID OFFICALLY VERIFIED STORE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Each application undergoes automated cryptographic validation before listing.", color = Color(0xFF64748B), fontSize = 9.5.sp)

        Spacer(modifier = Modifier.height(12.dp))

        // Simple search card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF03050C))
                .border(0.5.dp, Color(0xFF2E3856), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search market", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            TextField(
                value = query,
                onValueChange = { viewModel.storeQuery.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search system apps store...", color = Color(0xFF475569), fontSize = 11.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Store App Listing
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(appsAvailable) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.3f)) {
                            Surface(
                                color = accentColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(app.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(app.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(app.description, color = Color(0xFF94A3B8), fontSize = 9.5.sp, maxLines = 1)
                            }
                        }

                        // App status button
                        val isInstalled = installed.contains(app.id)
                        val inProgress = downloadProgress[app.id]
                        
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(0.7f)) {
                            if (inProgress != null) {
                                LinearProgressIndicator(
                                    progress = { inProgress },
                                    color = accentColor,
                                    trackColor = Color(0xFF1E293B),
                                    modifier = Modifier.width(60.dp).height(4.dp).clip(CircleShape)
                                )
                            } else {
                                Button(
                                    onClick = {
                                        if (!isInstalled) {
                                            scope.launch {
                                                // Simulated download
                                                val nextProgress = downloadProgress.toMutableMap()
                                                for (prog in 1..5) {
                                                    delay(250)
                                                    nextProgress[app.id] = (prog / 5f)
                                                    viewModel.downloadingAppsStatus.value = nextProgress
                                                }
                                                val cleanProgress = downloadProgress.toMutableMap()
                                                cleanProgress.remove(app.id)
                                                viewModel.downloadingAppsStatus.value = cleanProgress
                                                viewModel.installedApps.value = installed + app.id
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInstalled) Color(0xFF1E293B) else accentColor.copy(alpha = 0.15f)
                                    ),
                                    border = BorderStroke(1.dp, if (isInstalled) Color(0xFF334155) else accentColor),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (isInstalled) "INSTALLED" else "SYNC APP",
                                        color = if (isInstalled) Color(0xFF94A3B8) else accentColor,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. CUSTOMIZATION THEME APP
@Composable
fun ThemeCustomizerAppView(viewModel: BaseDroidViewModel) {
    val activeTheme by viewModel.activeTheme.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val currentHz by viewModel.refreshRate.collectAsState()

    val colorsAvailable = listOf(
        Color(0xFF00FFCC), // Cyber Cyan
        Color(0xFF9A52FF), // Purple Electric
        Color(0xFFFF9E00), // Amber Flare
        Color(0xFFE11D48), // Crimson Neon
        Color(0xFF10B981)  // Emerald
    )

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Text("DYNAMIC THEME ENGINE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Apply dynamic styles and wallpaper gradients globally.", color = Color(0xFF64748B), fontSize = 10.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Wallpapers grid
        Text("Background Matrix Gradients (Wallpapers)", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))

        BaseDroidTheme.values().forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.activeTheme.value = theme }
                    .background(if (activeTheme == theme) Color(0xFF0F172A) else Color.Transparent)
                    .border(
                        1.dp,
                        if (activeTheme == theme) accentColor else Color(0xFF1E293B),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color representation box
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(theme.bgGradients))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(theme.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accent palette
        Text("UI Hardware Accent Color", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colorsAvailable.forEach { col ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(
                            2.dp,
                            if (accentColor == col) Color.White else Color.Transparent,
                            CircleShape
                        )
                        .clickable { viewModel.accentColor.value = col }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Screen refresh rate tuning (Accessibility parameter)
        Text("Display Engine Refresh Rate (Hz)", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070B14))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
        ) {
            val hzs = listOf(60, 120, 144)
            hzs.forEach { hz ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.refreshRate.value = hz }
                        .background(if (currentHz == hz) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hz Hz",
                        color = if (currentHz == hz) accentColor else Color(0xFF5F6E86),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// MULTITASKING SIMULATOR LAYOUTS (DRAGGABLE MULTI-WINDOW CONTAINER)
// ------------------------------------------------------------------------------
@Composable
fun FloatingWindowWrapper(
    window: FloatingWindow,
    viewModel: BaseDroidViewModel,
    accentColor: Color
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    var dragPositionX by remember { mutableStateOf(window.xOffset) }
    var dragPositionY by remember { mutableStateOf(window.yOffset) }

    Box(
        modifier = Modifier
            .offset { IntOffset(dragPositionX.roundToInt(), dragPositionY.roundToInt()) }
            .size(width = 240.dp, height = 310.dp)
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .border(1.3.dp, accentColor, RoundedCornerShape(10.dp))
            .background(Color(0xFF070B14))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragPositionX += dragAmount.x
                    dragPositionY += dragAmount.y
                    viewModel.updateFloatingWindowPosition(window.id, dragPositionX, dragPositionY)
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag-header toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(window.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = window.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Close Button
                IconButton(
                    onClick = { viewModel.closeFloatingWindow(window.id) },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close window", tint = Color.Red, modifier = Modifier.size(11.dp))
                }
            }

            // Window Subcontent wrapper (Non Click-through constraints)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF090E1A))
                    .pointerInput(Unit) {} // Stops touch propagation
            ) {
                when (window.appType) {
                    "terminal" -> TerminalAppView(viewModel = viewModel)
                    "ai_assistant" -> AiAssistantAppView(viewModel = viewModel)
                    "code_editor" -> CodeEditorAppView(viewModel = viewModel)
                    "security" -> SecuritySafeguardAppView(viewModel = viewModel)
                    "store" -> VerifiedStoreAppView(viewModel = viewModel)
                    "customization" -> ThemeCustomizerAppView(viewModel = viewModel)
                }
            }
        }
    }
}
