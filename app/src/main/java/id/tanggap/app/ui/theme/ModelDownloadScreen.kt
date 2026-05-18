package id.tanggap.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Layar download model — ditampilkan saat isModelMissing = true.
 * Download dimulai OTOMATIS tanpa perlu klik tombol.
 * User hanya perlu menunggu, atau membatalkan jika diperlukan.
 */
@Composable
fun ModelDownloadScreen(
    language: String,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    downloadState: DownloadState
) {
    val isId = language != "en"

    // Auto-start download saat layar pertama kali muncul
    LaunchedEffect(Unit) {
        if (downloadState is DownloadState.Idle) {
            onStartDownload()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060F10)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── Animasi ikon ─────────────────────────────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "dl_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue  = 1f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Color(0xFF0B3037).copy(
                            alpha = if (downloadState is DownloadState.Downloading) pulseAlpha else 1f
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (downloadState) {
                        is DownloadState.Idle        -> "⬇"
                        is DownloadState.Downloading -> "📡"
                        is DownloadState.Done        -> "✅"
                        is DownloadState.Error       -> "⚠️"
                    },
                    fontSize = 36.sp
                )
            }

            // ── Judul ────────────────────────────────────────────────────────
            Text(
                text = when (downloadState) {
                    is DownloadState.Idle        ->
                        if (isId) "Mempersiapkan Download..." else "Preparing Download..."
                    is DownloadState.Downloading ->
                        if (isId) "Mengunduh Model AI..." else "Downloading AI Model..."
                    is DownloadState.Done        ->
                        if (isId) "Download Selesai!" else "Download Complete!"
                    is DownloadState.Error       ->
                        if (isId) "Download Gagal" else "Download Failed"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // ── Sumber model ─────────────────────────────────────────────────
            if (downloadState is DownloadState.Idle || downloadState is DownloadState.Downloading) {
                Text(
                    text = if (isId)
                        "Mengunduh Gemma 4 E2B (±2.6 GB) dari Hugging Face"
                    else
                        "Downloading Gemma 4 E2B (~2.6 GB) from Hugging Face",
                    fontSize = 13.sp,
                    color = Color(0xFF2A8A90),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            // ── Progress bar ─────────────────────────────────────────────────
            when (downloadState) {
                is DownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${downloadState.downloadedMB} MB / ${downloadState.totalMB} MB",
                                fontSize = 12.sp,
                                color = Color(0xFF2A8A90)
                            )
                            Text(
                                text = "${downloadState.percent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2A8A90)
                            )
                        }

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0B3037))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(downloadState.percent / 100f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF2A8A90))
                            )
                        }

                        Text(
                            text = if (isId)
                                "Download otomatis dimulai · bisa dilanjutkan jika terputus"
                            else
                                "Download started automatically · resumes if interrupted",
                            fontSize = 11.sp,
                            color = Color(0xFF2A8A90).copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is DownloadState.Idle -> {
                    // Indeterminate sementara auto-start berjalan
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2A8A90),
                        trackColor = Color(0xFF0B3037)
                    )
                }

                is DownloadState.Error -> {
                    Text(
                        text = downloadState.message,
                        fontSize = 13.sp,
                        color = Color(0xFFE57373),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is DownloadState.Done -> {
                    Text(
                        text = if (isId) "✅ Memuat model, harap tunggu..." else "✅ Loading model, please wait...",
                        fontSize = 14.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Info koneksi (hanya saat awal / downloading) ─────────────────
            if (downloadState is DownloadState.Idle || downloadState is DownloadState.Downloading) {
                Text(
                    text = if (isId)
                        "Diperlukan koneksi internet sekali saja.\nSetelah selesai, app berjalan 100% offline."
                    else
                        "Internet required once for this download.\nAfter that, the app runs 100% offline.",
                    fontSize = 11.sp,
                    color = Color(0xFF2A8A90).copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Tombol batalkan (hanya saat downloading) ─────────────────────
            if (downloadState is DownloadState.Downloading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick  = onCancelDownload,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isId) "Batalkan Download" else "Cancel Download",
                            color = Color(0xFFE53935),
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ── Tombol coba lagi (hanya saat error) ──────────────────────────
            if (downloadState is DownloadState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2A8A90)),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick  = onStartDownload,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isId) "🔄  Coba Lagi" else "🔄  Retry",
                            color = Color(0xFF0B1A1C),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─── State download ────────────────────────────────────────────────────────────

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val downloadedMB: Int = 0,
        val totalMB: Int = 0,
        val percent: Int = 0
    ) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}
