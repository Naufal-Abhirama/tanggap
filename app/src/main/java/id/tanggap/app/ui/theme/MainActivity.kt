package id.tanggap.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import id.tanggap.app.cache.EmergencyCacheManager
import id.tanggap.app.data.DisasterType
import id.tanggap.app.data.DisasterTypeDetector
import id.tanggap.app.inference.GemmaInferenceEngine
import id.tanggap.app.tts.TTSManager
import id.tanggap.app.ui.theme.Gemma4Theme
import id.tanggap.app.ui.theme.LanguagePickerScreen
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.ContentValues
import android.widget.Toast
import id.tanggap.app.inference.ConversationContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import id.tanggap.app.download.ModelDownloadManager
import id.tanggap.app.ui.theme.DownloadState
import id.tanggap.app.ui.theme.ModelDownloadScreen
import kotlinx.coroutines.Job
import id.tanggap.app.location.LocationHelper
import androidx.compose.foundation.BorderStroke
import id.tanggap.app.data.detectTriageType
import id.tanggap.app.data.buildTriageData
import androidx.compose.material3.DropdownMenu
import id.tanggap.app.debug.TanggapLogger

// ─── Data model ───────────────────────────────────────────────────────────────

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
    val imageBitmap: Bitmap? = null,
    val textId: String? = null,
    val textEn: String? = null,
    val isDivider: Boolean = false,
    val triageData: id.tanggap.app.data.TriageData? = null,
    val isHidden: Boolean = false
)

fun estimateTtsDuration(text: String, lang: String = "id"): Int {
    val wordCount = text.trim().split("\\s+".toRegex()).size
    val wps = if (lang == "en") 2.5 else 1.8
    return maxOf(5, (wordCount / wps).toInt())
}

fun ChatMessage.textFor(lang: String): String =
    if (!isUser && !isDivider) {
        when (lang) {
            "en" -> textEn ?: text
            else -> textId ?: text
        }
    } else text

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    private lateinit var gemmaEngine: GemmaInferenceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gemmaEngine = GemmaInferenceEngine(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1002
            )
        }

        setContent {
            Gemma4Theme {
                AppRoot(gemmaEngine = gemmaEngine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gemmaEngine.close()
    }
}

// ─── Root navigasi ────────────────────────────────────────────────────────────

private const val PREFS_NAME   = "tanggap_prefs"
private const val KEY_LANGUAGE = "selected_language"

@Composable
fun AppRoot(gemmaEngine: GemmaInferenceEngine) {
    val context    = LocalContext.current
    val ttsManager = remember { TTSManager(context) }

    val prefs     = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val savedLang = remember { prefs.getString(KEY_LANGUAGE, null) }

    var selectedLanguage by remember { mutableStateOf<String?>(savedLang) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    when {
        selectedLanguage == null -> {
            LanguagePickerScreen(onLanguageSelected = { lang ->
                prefs.edit().putString(KEY_LANGUAGE, lang).apply()
                selectedLanguage = lang
            })
        }
        else -> {
            TanggapAIScreen(
                gemmaEngine      = gemmaEngine,
                ttsManager       = ttsManager,
                initialLanguage  = selectedLanguage!!,
                onLanguageChange = { newLang ->
                    prefs.edit().putString(KEY_LANGUAGE, newLang).apply()
                    selectedLanguage = newLang
                }
            )
        }
    }
}

// ─── Chat Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanggapAIScreen(
    gemmaEngine: GemmaInferenceEngine,
    ttsManager: TTSManager,
    initialLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    var statusText        by remember { mutableStateOf("Memuat model...") }
    var isEngineReady     by remember { mutableStateOf(false) }
    var isModelMissing    by remember { mutableStateOf(false) }
    var downloadState     by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var downloadJob       by remember { mutableStateOf<Job?>(null) }
    var isLoading         by remember { mutableStateOf(false) }
    var userInput         by remember { mutableStateOf("") }
    var speakingIndex     by remember { mutableStateOf(-1) }
    var ttsSeconds        by remember { mutableStateOf(0) }
    var timerJob          by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val finishedSeconds   = remember { mutableStateMapOf<Int, Int>() }
    val messages  = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var isFirstLaunch    by remember { mutableStateOf(true) }
    var pendingImage         by remember { mutableStateOf<Bitmap?>(null) }
    var showMediaSheet       by remember { mutableStateOf(false) }
    var fullscreenImage      by remember { mutableStateOf<Bitmap?>(null) }
    var showDeleteConfirm    by remember { mutableStateOf(false) }
    var showImageActionSheet by remember { mutableStateOf(false) }
    var actionSheetBitmap    by remember { mutableStateOf<Bitmap?>(null) }
    var convContext          by remember { mutableStateOf(ConversationContext()) }
    var detectedProvince by remember { mutableStateOf<String?>(null) }
    var showLanguageConfirmDialog by remember { mutableStateOf(false) }
    var pendingLanguage           by remember { mutableStateOf<String?>(null) }
    val tempImageUri = remember {
        val file = File(context.cacheDir, "tanggap_capture.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingImage = uriToBitmap(context, tempImageUri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingImage = uriToBitmap(context, it) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(tempImageUri)
    }
    val cacheManager      = remember { EmergencyCacheManager(context) }
    var isBatteryCritical by remember { mutableStateOf(cacheManager.isBatteryCritical()) }
    var lastTriageSummary by remember { mutableStateOf("") }
    var lastTriageType    by remember { mutableStateOf<id.tanggap.app.data.TriageType?>(null) }
    val isSilentNeeded = isBatteryCritical
    LaunchedEffect(isSilentNeeded) { ttsManager.setSilentMode(isSilentNeeded) }

    DisposableEffect(Unit) {
        cacheManager.startMonitoring { _, critical -> isBatteryCritical = critical }
        onDispose { cacheManager.stopMonitoring() }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val province = LocationHelper.detectProvince(context)
            withContext(Dispatchers.Main) {
                if (province != null) {
                    detectedProvince = province
                    id.tanggap.app.inference.SafetyLayer.currentProvinsi = province
                }
            }
        }
    }

    val greetingId = "Halo! Saya TANGGAP, asisten darurat bencana offline. Ada yang bisa saya bantu?"
    val greetingEn = "Hello! I'm TANGGAP, an offline disaster emergency assistant. How can I help?"

    LaunchedEffect(selectedLanguage) {
        gemmaEngine.currentLanguage = selectedLanguage
        ttsManager.setLanguage(selectedLanguage)
        ttsManager.stop()
        timerJob?.cancel()
        speakingIndex = -1
        finishedSeconds.clear()

        if (isFirstLaunch) {
            isFirstLaunch = false
        } else {
            val lastNonDivider = messages.lastOrNull { !it.isDivider }
            if (lastNonDivider != null) {
                val dividerText = if (selectedLanguage == "en")
                    "— Switched to English —"
                else
                    "— Berganti ke Bahasa Indonesia —"
                messages.add(ChatMessage(text = dividerText, isUser = false, isDivider = true))
            }
        }

        onLanguageChange(selectedLanguage)
    }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    LaunchedEffect(ttsManager) {
        ttsManager.onSpeakingDone = {
            if (speakingIndex != -1) finishedSeconds[speakingIndex] = ttsSeconds
            speakingIndex = -1
            timerJob?.cancel()
        }
    }

    // ── Inisialisasi engine ────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (!gemmaEngine.isModelReady()) {
            isModelMissing = true
        } else {
            withContext(Dispatchers.IO) {
                try {
                    gemmaEngine.initialize()
                    withContext(Dispatchers.Main) {
                        isEngineReady = true
                        statusText = "✅ Engine siap"
                        messages.add(ChatMessage(
                            text   = if (selectedLanguage == "en") greetingEn else greetingId,
                            isUser = false,
                            textId = greetingId,
                            textEn = greetingEn
                        ))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText = "❌ Gagal: ${e.message}" }
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun startTtsFromSecond(messageText: String, index: Int, fromSecond: Int, totalDuration: Int) {
        ttsManager.stop()
        timerJob?.cancel()
        ttsSeconds = fromSecond

        val words = messageText.trim().split("\\s+".toRegex())
        val ratio = if (totalDuration > 0) fromSecond.toFloat() / totalDuration else 0f
        val startWordIndex = (ratio * words.size).toInt().coerceIn(0, words.size - 1)
        val partialText = words.drop(startWordIndex).joinToString(" ")

        ttsManager.speak(partialText)
        speakingIndex = index

        timerJob = scope.launch {
            while (true) { delay(1000); ttsSeconds++ }
        }
    }

    // ── Helper: streaming response ke bubble ──────────────────────────────────
    suspend fun streamResponseToBubble(response: String, langSnapshot: String) {
        val bubbleIndex = withContext(Dispatchers.Main) {
            messages.indexOfLast { it.isLoading }
        }
        if (bubbleIndex >= 0) {
            val words = response.split(" ")
            val accumulated = StringBuilder()
            for (word in words) {
                accumulated.append(if (accumulated.isEmpty()) word else " $word")
                val snapshot = accumulated.toString()
                withContext(Dispatchers.Main) {
                    if (bubbleIndex < messages.size) {
                        messages[bubbleIndex] = messages[bubbleIndex].copy(
                            text = snapshot, isLoading = false
                        )
                    }
                }
                delay(25L)
            }
        }
        withContext(Dispatchers.Main) {
            val finalIdx = if (bubbleIndex >= 0 && bubbleIndex < messages.size)
                bubbleIndex
            else messages.indexOfLast { !it.isUser && !it.isDivider }
            if (finalIdx >= 0) {
                messages[finalIdx] = messages[finalIdx].copy(
                    text      = response,
                    isLoading = false,
                    textId    = if (langSnapshot == "id") response else null,
                    textEn    = if (langSnapshot == "en") response else null
                )
            }
        }
    }

    // ── Bottom Sheet: Media ────────────────────────────────────────────────────
    if (showMediaSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMediaSheet = false },
            containerColor = Color(0xFF0B3037),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp).height(4.dp)
                        .background(Color(0xFF2A8A90).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        showMediaSheet = false
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) cameraLauncher.launch(tempImageUri)
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, tint = Color(0xFF2A8A90), modifier = Modifier.size(24.dp))
                        Text(
                            if (selectedLanguage == "en") "Camera" else "Kamera",
                            color = Color(0xFFC8EEEB), fontSize = 16.sp
                        )
                    }
                }
                TextButton(
                    onClick = { showMediaSheet = false; galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = Color(0xFF2A8A90), modifier = Modifier.size(24.dp))
                        Text(
                            if (selectedLanguage == "en") "Gallery" else "Galeri",
                            color = Color(0xFFC8EEEB), fontSize = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Dialog konfirmasi hapus foto ───────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF0B3037),
            title = {
                Text(
                    if (selectedLanguage == "en") "Remove photo?" else "Hapus foto?",
                    color = Color(0xFFC8EEEB), fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (selectedLanguage == "en") "This photo will be removed from your message."
                    else "Foto ini akan dihapus dari pesanmu.",
                    color = Color(0xFFC8EEEB).copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingImage = null; showDeleteConfirm = false }) {
                    Text(
                        if (selectedLanguage == "en") "Remove" else "Hapus",
                        color = Color(0xFFE53935), fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(if (selectedLanguage == "en") "Cancel" else "Batal", color = Color(0xFF2A8A90))
                }
            }
        )
    }

    // ── Bottom Sheet opsi foto ─────────────────────────────────────────────────
    if (showImageActionSheet && actionSheetBitmap != null) {
        val bitmapForAction = actionSheetBitmap!!
        ModalBottomSheet(
            onDismissRequest = { showImageActionSheet = false; actionSheetBitmap = null },
            containerColor = Color(0xFF0B3037),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp).height(4.dp)
                        .background(Color(0xFF2A8A90).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        showImageActionSheet = false
                        val filename = "TANGGAP_${System.currentTimeMillis()}.jpg"
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                }
                                val uri = context.contentResolver.insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                                )
                                uri?.let { u ->
                                    context.contentResolver.openOutputStream(u)?.use { out ->
                                        bitmapForAction.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                    }
                                }
                            } else {
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                dir.mkdirs()
                                java.io.File(dir, filename).outputStream().use { out ->
                                    bitmapForAction.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                            }
                            Toast.makeText(
                                context,
                                if (selectedLanguage == "en") "Saved to Gallery" else "Tersimpan ke Galeri",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                if (selectedLanguage == "en") "Failed to save" else "Gagal menyimpan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("⬇", fontSize = 20.sp)
                        Text(
                            if (selectedLanguage == "en") "Save to Gallery" else "Simpan ke Galeri",
                            color = Color(0xFFC8EEEB), fontSize = 16.sp
                        )
                    }
                }
                TextButton(
                    onClick = {
                        showImageActionSheet = false
                        try {
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE
                            ) as android.content.ClipboardManager
                            val cacheFile = java.io.File(
                                context.cacheDir, "tanggap_copy_${System.currentTimeMillis()}.jpg"
                            )
                            cacheFile.outputStream().use { out ->
                                bitmapForAction.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", cacheFile
                            )
                            val clip = android.content.ClipData.newUri(
                                context.contentResolver,
                                if (selectedLanguage == "en") "Image" else "Gambar",
                                uri
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                context,
                                if (selectedLanguage == "en") "Image copied" else "Gambar disalin",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                if (selectedLanguage == "en") "Failed to copy" else "Gagal menyalin",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("⎘", fontSize = 20.sp)
                        Text(
                            if (selectedLanguage == "en") "Copy Image" else "Salin Gambar",
                            color = Color(0xFFC8EEEB), fontSize = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Dialog konfirmasi ganti bahasa ────────────────────────────────────────
    if (showLanguageConfirmDialog && pendingLanguage != null) {
        val switchingToId = pendingLanguage == "id"
        AlertDialog(
            onDismissRequest = { showLanguageConfirmDialog = false; pendingLanguage = null },
            containerColor = Color(0xFF0B1A1C),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF2A8A90),
            title = {
                Text(
                    text = if (selectedLanguage == "en") "Switch Language?" else "Ganti Bahasa?",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (selectedLanguage == "en")
                        "Switching to ${if (switchingToId) "Indonesian" else "English"} will start a new empty chat. Your current conversation will be cleared."
                    else
                        "Mengganti ke ${if (switchingToId) "Bahasa Indonesia" else "English"} akan memulai chat baru yang kosong. Percakapan saat ini akan dihapus.",
                    fontSize = 14.sp, lineHeight = 21.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLanguageConfirmDialog = false
                    val newLang = pendingLanguage!!
                    pendingLanguage = null

                    messages.clear()
                    convContext = ConversationContext()
                    ttsManager.stop()
                    timerJob?.cancel()
                    speakingIndex = -1
                    finishedSeconds.clear()

                    isFirstLaunch = true
                    selectedLanguage = newLang

                    messages.add(ChatMessage(
                        text   = if (newLang == "en") greetingEn else greetingId,
                        isUser = false,
                        textId = greetingId,
                        textEn = greetingEn
                    ))
                }) {
                    Text(
                        text = if (selectedLanguage == "en") "Yes, switch" else "Ya, ganti",
                        color = Color(0xFF2A8A90), fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLanguageConfirmDialog = false; pendingLanguage = null }) {
                    Text(
                        text = if (selectedLanguage == "en") "Cancel" else "Batal",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1A1C))
                        .statusBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // ── Kiri: toggle ID | EN ──────────────────────────────
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                    .background(
                                        if (selectedLanguage == "id") Color(0xFF2A8A90) else Color(0xFF0B3037)
                                    )
                                    .clickable(enabled = selectedLanguage != "id") {
                                        pendingLanguage = "id"
                                        showLanguageConfirmDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ID",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedLanguage == "id") Color(0xFF0B1A1C)
                                    else Color(0xFF2A8A90).copy(alpha = 0.5f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                    .background(
                                        if (selectedLanguage == "en") Color(0xFF2A8A90) else Color(0xFF0B3037)
                                    )
                                    .clickable(enabled = selectedLanguage != "en") {
                                        pendingLanguage = "en"
                                        showLanguageConfirmDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "EN",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedLanguage == "en") Color(0xFF0B1A1C)
                                    else Color(0xFF2A8A90).copy(alpha = 0.5f)
                                )
                            }
                        }

                        // ── Tengah: TANGGAP + status dot ─────────────────────
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TANGGAP",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            val dotColor = when {
                                isEngineReady              -> Color(0xFF4CAF50)
                                statusText.startsWith("❌") -> Color(0xFFE53935)
                                else                       -> Color(0xFFFFC107)
                            }
                            val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
                            val dotAlpha by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue  = if (isEngineReady) 1f else 0.2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dot_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = dotColor.copy(alpha = if (isEngineReady) 1f else dotAlpha),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                        }

                        // ── Kanan: tombol 📞 BPBD ────────────────────────────
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            var showBpbdPopup by remember { mutableStateOf(false) }
                            val bpbdPhone = if (detectedProvince != null)
                                id.tanggap.app.inference.SafetyLayer
                                    .getBpbdContact(detectedProvince)
                                    .substringAfterLast(": ").trim()
                            else "117"
                            val bpbdLabel = if (detectedProvince != null)
                                "BPBD ${detectedProvince}"
                            else "BPBD Nasional"

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFD32F2F))
                                    .clickable { showBpbdPopup = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📞", fontSize = 16.sp)
                            }

                            DropdownMenu(
                                expanded = showBpbdPopup,
                                onDismissRequest = { showBpbdPopup = false },
                                modifier = Modifier
                                    .background(Color(0xFF0B3037))
                                    .width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "📍 $bpbdLabel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = bpbdPhone,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2A8A90)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFD32F2F))
                                            .clickable {
                                                showBpbdPopup = false
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$bpbdPhone"))
                                                context.startActivity(intent)
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (selectedLanguage == "en") "📞 Call Now" else "📞 Hubungi Sekarang",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isBatteryCritical) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFD32F2F))
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (selectedLanguage == "en")
                                    "🔴 CRITICAL BATTERY — Please charge your device soon"
                                else
                                    "🔴 BATERAI KRITIS — Segera cas perangkatmu",
                                color = Color.White, fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF0B3037), thickness = 1.dp)
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1A1C))
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = Color(0xFF0B3037), thickness = 1.dp)

                    if (pendingImage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(64.dp)) {
                                Image(
                                    bitmap = pendingImage!!.asImageBitmap(),
                                    contentDescription = if (selectedLanguage == "en") "Selected photo" else "Foto dipilih",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { fullscreenImage = pendingImage })
                                        },
                                    contentScale = ContentScale.Fit
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(Color(0xFF0B1A1C), RoundedCornerShape(50))
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { showDeleteConfirm = true })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close, contentDescription = "Hapus foto",
                                        tint = Color.White, modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF0B3037), shape = RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { showMediaSheet = true }) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = if (selectedLanguage == "en") "Add photo" else "Tambah foto",
                                    tint = Color(0xFF2A8A90), modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            placeholder = {
                                Text(
                                    if (pendingImage != null) {
                                        if (selectedLanguage == "en") "Add caption (optional)..."
                                        else "Tambah keterangan (opsional)..."
                                    } else {
                                        if (selectedLanguage == "en") "Describe your situation..."
                                        else "Ceritakan situasimu..."
                                    },
                                    fontSize = 14.sp,
                                    color = Color(0xFF2A8A90).copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isEngineReady && !isLoading,
                            minLines = 1,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor   = Color(0xFF0B3037),
                                unfocusedContainerColor = Color(0xFF0B3037),
                                focusedBorderColor      = Color(0xFF134E5E),
                                unfocusedBorderColor    = Color(0xFF0B3037),
                                focusedTextColor        = Color(0xFFC8EEEB),
                                unfocusedTextColor      = Color(0xFFC8EEEB),
                                cursorColor             = Color(0xFF2A8A90)
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isEngineReady && (userInput.isNotBlank() || pendingImage != null) && !isLoading)
                                        Color(0xFF2A8A90)
                                    else Color(0xFF0B3037),
                                    shape = RoundedCornerShape(50)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = {
                                if (!isEngineReady || isLoading) return@IconButton
                                if (userInput.isBlank() && pendingImage == null) return@IconButton

                                val prompt      = userInput.trim()
                                val imageToSend = pendingImage
                                userInput    = ""
                                pendingImage = null
                                ttsManager.stop()
                                speakingIndex = -1
                                timerJob?.cancel()
                                ttsSeconds = 0

                                // FIX: pakai convContext.triageDone — lebih reliable dari cek isHidden
                                val triageAlreadyDone = convContext.triageDone
                                val triageType        = detectTriageType(prompt, selectedLanguage)

                                // Triage hanya aktif jika:
                                // 1. Tidak ada gambar
                                // 2. Ada keyword triage terdeteksi
                                // 3. Triage belum pernah dilakukan (flag di context)
                                // 4. Sesi darurat belum aktif (turn pertama)
                                val shouldTriage = imageToSend == null
                                        && triageType != null
                                        && !triageAlreadyDone
                                        && !convContext.isActive()

                                TanggapLogger.logTriageDetection(
                                    query             = prompt,
                                    lang              = selectedLanguage,
                                    triageType        = triageType?.name,
                                    triageAlreadyDone = triageAlreadyDone,
                                    hasImage          = imageToSend != null,
                                    shouldTriage      = shouldTriage
                                )

                                if (imageToSend != null) {
                                    messages.add(ChatMessage(
                                        text = if (prompt.isNotBlank()) prompt
                                        else if (selectedLanguage == "en") "📷 Photo sent"
                                        else "📷 Foto dikirim",
                                        isUser = true,
                                        imageBitmap = imageToSend
                                    ))
                                } else {
                                    messages.add(ChatMessage(prompt, isUser = true))
                                }

                                if (shouldTriage && triageType != null) {
                                    val triage = buildTriageData(triageType, selectedLanguage)
                                    TanggapLogger.logTriageCardShown(
                                        triageType = triageType.name,
                                        q1 = triage.q1,
                                        q2 = triage.q2,
                                        q3 = triage.q3
                                    )
                                    lastTriageType = triageType
                                    messages.add(ChatMessage(text = "", isUser = false, triageData = triage))
                                    return@IconButton
                                }

                                messages.add(ChatMessage("", isUser = false, isLoading = true))

                                scope.launch {
                                    isLoading = true
                                    val langSnapshot = selectedLanguage
                                    withContext(Dispatchers.IO) {
                                        val (response, updatedContext) = if (imageToSend != null) {
                                            val dt = if (prompt.isNotBlank())
                                                DisasterTypeDetector.detect(prompt)
                                            else DisasterType.UMUM
                                            gemmaEngine.analyzeImage(
                                                bitmap       = imageToSend,
                                                disasterType = dt,
                                                userHint     = prompt,
                                                context      = convContext
                                            )
                                        } else {
                                            gemmaEngine.generateResponse(
                                                userPrompt = prompt,
                                                context    = convContext
                                            )
                                        }

                                        streamResponseToBubble(response, langSnapshot)

                                        withContext(Dispatchers.Main) {
                                            convContext = updatedContext
                                            isLoading   = false
                                        }
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (selectedLanguage == "en") "Send" else "Kirim",
                                    tint = if (isEngineReady && (userInput.isNotBlank() || pendingImage != null) && !isLoading)
                                        Color(0xFF0B1A1C)
                                    else Color(0xFF2A8A90).copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (isModelMissing) {
                ModelDownloadScreen(
                    language        = selectedLanguage,
                    downloadState   = downloadState,
                    onStartDownload = {
                        downloadState = DownloadState.Downloading()
                        downloadJob = scope.launch {
                            ModelDownloadManager.download(
                                context    = context,
                                onProgress = { dlMB, totalMB, pct ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        downloadState = DownloadState.Downloading(dlMB, totalMB, pct)
                                    }
                                },
                                onDone = {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        downloadState = DownloadState.Done
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    gemmaEngine.initialize()
                                                    withContext(Dispatchers.Main) {
                                                        isModelMissing = false
                                                        isEngineReady  = true
                                                        statusText     = "✅ Engine siap"
                                                        messages.add(ChatMessage(
                                                            text   = if (selectedLanguage == "en") greetingEn else greetingId,
                                                            isUser = false,
                                                            textId = greetingId,
                                                            textEn = greetingEn
                                                        ))
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        downloadState = DownloadState.Error(
                                                            if (selectedLanguage == "en")
                                                                "Failed to load model: ${e.message}"
                                                            else
                                                                "Gagal memuat model: ${e.message}"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onError = { error ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        downloadState = DownloadState.Error(
                                            if (selectedLanguage == "en")
                                                "Download failed: $error\nMake sure you have a stable internet connection."
                                            else
                                                "Download gagal: $error\nPastikan koneksi internet stabil."
                                        )
                                    }
                                }
                            )
                        }
                    },
                    onCancelDownload = {
                        downloadJob?.cancel()
                        downloadState = DownloadState.Idle
                        ModelDownloadManager.deletePartial(context)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages.size, key = { it }) { index ->
                        val message = messages[index]

                        if (message.isDivider) {
                            LanguageDivider(text = message.text)
                            return@items
                        }

                        if (message.isHidden) return@items

                        val displayText = message.textFor(selectedLanguage)
                        val wordCount   = displayText.trim().split(Regex("\\s+")).size
                        val showVoiceNote = !message.isUser && !message.isLoading && index > 0 && wordCount > 80

                        val hasTriageResponseAfter = message.triageData != null &&
                                messages.drop(index + 1).any { !it.isUser && !it.isDivider }

                        ChatBubble(
                            message                = message.copy(text = displayText),
                            index                  = index,
                            hasTriageResponseAfter = hasTriageResponseAfter,
                            speakingIndex          = speakingIndex,
                            ttsSeconds             = if (speakingIndex == index) ttsSeconds
                            else (finishedSeconds[index] ?: -1),
                            totalDuration          = if (showVoiceNote)
                                estimateTtsDuration(displayText, selectedLanguage)
                            else 0,
                            showVoiceNote          = showVoiceNote,
                            isSilentMode           = isSilentNeeded,
                            onTtsClick = {
                                if (speakingIndex == index) {
                                    finishedSeconds[index] = ttsSeconds
                                    ttsManager.stop()
                                    speakingIndex = -1
                                    timerJob?.cancel()
                                } else {
                                    val resumeFrom = finishedSeconds[index] ?: 0
                                    startTtsFromSecond(
                                        displayText, index, resumeFrom,
                                        estimateTtsDuration(displayText, selectedLanguage)
                                    )
                                }
                            },
                            onSeek = { targetSecond ->
                                startTtsFromSecond(
                                    displayText, index, targetSecond,
                                    estimateTtsDuration(displayText, selectedLanguage)
                                )
                            },
                            onImageClick     = { bitmap -> fullscreenImage = bitmap },
                            onImageLongPress = { bitmap ->
                                actionSheetBitmap = bitmap
                                showImageActionSheet = true
                            },
                            selectedLanguage = selectedLanguage,
                            onTriageAnswer = { summary ->
                                val langSnapshot = selectedLanguage
                                lastTriageSummary = summary
                                TanggapLogger.logTriageAnswer(summary)

                                // FIX: markTriageDone() sebelum generateResponse agar
                                // pesan lanjutan tidak memicu triage card lagi
                                convContext = convContext.markTriageDone()

                                messages.add(ChatMessage(summary, isUser = true, isHidden = true))
                                messages.add(ChatMessage("", isUser = false, isLoading = true))

                                scope.launch {
                                    isLoading = true
                                    withContext(Dispatchers.IO) {
                                        // Update disaster type dari triage summary sebelum generate
                                        val triageDisasterType = when (lastTriageType) {
                                            id.tanggap.app.data.TriageType.BENCANA_AKTIF ->
                                                id.tanggap.app.data.DisasterTypeDetector.detect(summary)
                                            else -> convContext.disasterType ?: id.tanggap.app.data.DisasterType.UMUM
                                        }
                                        val contextWithDisasterType = if (
                                            triageDisasterType != id.tanggap.app.data.DisasterType.UMUM
                                            && convContext.disasterType == null
                                        ) {
                                            convContext.copy(disasterType = triageDisasterType)
                                        } else {
                                            convContext
                                        }

                                        val (response, updatedCtx) = gemmaEngine.generateResponse(
                                            userPrompt = summary,
                                            context    = contextWithDisasterType
                                        )

                                        streamResponseToBubble(response, langSnapshot)

                                        withContext(Dispatchers.Main) {
                                            convContext = updatedCtx
                                            isLoading   = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Fullscreen image viewer ────────────────────────────────────────────
        if (fullscreenImage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { fullscreenImage = null }) }
            ) {
                Image(
                    bitmap = fullscreenImage!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.Center)
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImage = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ─── Divider ganti bahasa ─────────────────────────────────────────────────────

@Composable
fun LanguageDivider(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF2A8A90).copy(alpha = 0.25f),
            thickness = 0.5.dp
        )
        Text(
            text = text, fontSize = 11.sp,
            color = Color(0xFF2A8A90).copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF2A8A90).copy(alpha = 0.25f),
            thickness = 0.5.dp
        )
    }
}

// ─── Waveform + ChatBubble ────────────────────────────────────────────────────

private val waveformHeights = listOf(
    3, 5, 9, 14, 8, 18, 6, 20, 11, 16, 4, 13, 19, 7, 15,
    10, 17, 5, 12, 20, 8, 14, 6, 18, 9, 13, 4, 16, 11, 7,
    19, 5, 12, 17, 8, 20, 6, 14, 10, 16
)

@Composable
fun WaveformSeekBar(progress: Float, isPlaying: Boolean, onSeek: (Float) -> Unit) {
    val activeColor   = Color(0xFF2A8A90)
    val inactiveColor = Color(0xFF2A8A90).copy(alpha = 0.28f)

    var seekProgress by remember { mutableFloatStateOf(progress) }
    var isSeeking    by remember { mutableStateOf(false) }

    LaunchedEffect(progress) { if (!isSeeking) seekProgress = progress }

    val displayProgress by animateFloatAsState(
        targetValue   = seekProgress,
        animationSpec = if (isSeeking) snap() else tween(900, easing = LinearEasing),
        label         = "waveform_progress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { offset ->
                    isSeeking = true
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    seekProgress = ratio
                    onSeek(ratio)
                    tryAwaitRelease()
                    isSeeking = false
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isSeeking = true
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        seekProgress = ratio
                        onSeek(ratio)
                    },
                    onDrag = { change, _ ->
                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                        seekProgress = ratio
                        onSeek(ratio)
                        change.consume()
                    },
                    onDragEnd    = { isSeeking = false },
                    onDragCancel = { isSeeking = false }
                )
            }
    ) {
        val totalWidth = maxWidth

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            waveformHeights.forEachIndexed { i, h ->
                val barProgress = i.toFloat() / waveformHeights.size
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (barProgress <= displayProgress) activeColor else inactiveColor)
                )
            }
        }

        if (isPlaying || displayProgress > 0f) {
            Box(
                modifier = Modifier.fillMaxHeight().width(totalWidth),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = (totalWidth * displayProgress).coerceIn(0.dp, totalWidth - 12.dp))
                        .size(12.dp)
                        .background(Color.White, RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    index: Int,
    hasTriageResponseAfter: Boolean = false,
    speakingIndex: Int,
    ttsSeconds: Int,
    totalDuration: Int,
    showVoiceNote: Boolean,
    isSilentMode: Boolean = false,
    onTtsClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onImageClick: (Bitmap) -> Unit = {},
    onImageLongPress: (Bitmap) -> Unit = {},
    selectedLanguage: String,
    onTriageAnswer: (String) -> Unit,
) {
    if (message.isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.imageBitmap != null) {
                    Image(
                        bitmap = message.imageBitmap.asImageBitmap(),
                        contentDescription = "Foto",
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 14.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onImageClick(message.imageBitmap) },
                                    onLongPress = { onImageLongPress(message.imageBitmap) }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }
                if (message.text.isNotBlank() &&
                    message.text != "📷 Foto dikirim" &&
                    message.text != "📷 Photo sent"
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                Color(0xFF134E5E),
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                            )
                            .padding(14.dp, 10.dp)
                    ) {
                        Text(text = message.text, color = Color(0xFFC8EEEB), fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    } else {
        if (!message.isLoading && message.text.isBlank() && message.triageData == null) return
        if (!message.isLoading && message.text.isBlank() && message.triageData != null && hasTriageResponseAfter) return
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 56.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        Color(0xFF0B1A1C),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                    )
                    .padding(14.dp, 10.dp)
            ) {
                if (message.isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF2A8A90), shape = RoundedCornerShape(50)))
                        }
                    }
                } else {
                    Column {
                        if (showVoiceNote) {
                            val isPlaying = speakingIndex == index
                            val progress  = if (totalDuration > 0 && ttsSeconds >= 0)
                                (ttsSeconds.toFloat() / totalDuration).coerceIn(0f, 1f)
                            else 0f

                            val displayMin: Int
                            val displaySec: Int
                            if (ttsSeconds >= 0) {
                                displayMin = ttsSeconds / 60
                                displaySec = ttsSeconds % 60
                            } else {
                                displayMin = totalDuration / 60
                                displaySec = totalDuration % 60
                            }
                            val timeStr = "%d:%02d".format(displayMin, displaySec)

                            val buttonColor by animateColorAsState(
                                targetValue = if (isPlaying) Color(0xFF2A8A90) else Color(0xFF0B3037),
                                animationSpec = tween(200), label = "btn_color"
                            )
                            val iconTint by animateColorAsState(
                                targetValue = if (isPlaying) Color(0xFF0B1A1C) else Color(0xFF2A8A90),
                                animationSpec = tween(200), label = "icon_tint"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0B3037), shape = RoundedCornerShape(14.dp))
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).background(buttonColor, shape = RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(onClick = onTtsClick, modifier = Modifier.size(40.dp)) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    WaveformSeekBar(
                                        progress  = progress,
                                        isPlaying = isPlaying,
                                        onSeek    = { ratio -> onSeek((ratio * totalDuration).toInt()) }
                                    )
                                    Text(
                                        text = timeStr, fontSize = 10.sp,
                                        color = Color(0xFF2A8A90).copy(alpha = 0.75f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (message.triageData != null && !hasTriageResponseAfter) {
                            TriageCard(
                                triageData = message.triageData,
                                lang       = selectedLanguage,
                                onAnswer   = { summary -> onTriageAnswer(summary) }
                            )
                        } else {
                            RichText(modifier = Modifier.fillMaxWidth()) {
                                Markdown(message.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TriageCard(
    triageData: id.tanggap.app.data.TriageData,
    lang: String,
    onAnswer: (String) -> Unit
) {
    val answers   = remember { mutableStateMapOf<Int, Boolean?>() }
    val submitted = remember { mutableStateOf(false) }

    if (submitted.value) return

    fun optionLabels(qIndex: Int): Pair<String, String> {
        if (triageData.type == id.tanggap.app.data.TriageType.BENCANA_AKTIF && qIndex == 0) {
            return if (lang == "en") "Indoors" to "Outdoors" else "Di dalam" to "Di luar"
        }
        return if (lang == "en") "Yes" to "No" else "Ya" to "Tidak"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(triageData.q1, triageData.q2, triageData.q3).forEachIndexed { i, question ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = question, color = Color(0xFFC8EEEB), fontSize = 13.sp, lineHeight = 18.sp)
                val (yesLabel, noLabel) = optionLabels(i)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to yesLabel, false to noLabel).forEach { (value, label) ->
                        val selected = answers[i] == value
                        OutlinedButton(
                            onClick = { answers[i] = value },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) Color(0xFF2A8A90) else Color.Transparent
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selected) Color(0xFF2A8A90) else Color(0xFF2A8A90).copy(alpha = 0.35f)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) Color(0xFF0B1A1C) else Color(0xFF2A8A90),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        val allAnswered = answers.size == 3
        Button(
            onClick = {
                submitted.value = true
                val summary = id.tanggap.app.data.buildTriageSummary(
                    type    = triageData.type,
                    answers = answers,
                    lang    = lang
                )
                onAnswer(summary)
            },
            enabled = allAnswered,
            colors = ButtonDefaults.buttonColors(
                containerColor         = Color(0xFF2A8A90),
                disabledContainerColor = Color(0xFF2A8A90).copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (lang == "en") "Send situation report" else "Kirim kondisiku",
                color = Color(0xFF0B1A1C), fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) { null }
}