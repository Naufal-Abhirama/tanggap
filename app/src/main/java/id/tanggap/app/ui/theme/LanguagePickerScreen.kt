package id.tanggap.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState

// ── Color palette (sama dengan SplashScreen) ──────────────────────────────────
private val LpBgDeep      = Color(0xFF0B1A1C)
private val LpBgSurface   = Color(0xFF0B3037)
private val LpBgCard      = Color(0xFF1C4A40)
private val LpAccentGreen = Color(0xFF2A8A90)
private val LpTextPrimary = Color(0xFFFFFFFF)
private val LpTextSoft    = Color(0xFFC8EEEB)
private val LpTextMuted   = Color(0xFF2A8A90)

/**
 * LanguagePickerScreen — muncul sekali setelah SplashScreen,
 * sebelum chat screen. User memilih bahasa yang akan digunakan.
 *
 * @param onLanguageSelected dipanggil dengan "id" atau "en" ketika user menekan Mulai/Start.
 */
@Composable
fun LanguagePickerScreen(onLanguageSelected: (String) -> Unit) {

    var selectedLang by remember { mutableStateOf("id") }

    // Animasi masuk
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "fade_in"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "slide_up"
    )

    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LpBgDeep)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .alpha(alpha)
                .offset(y = offsetY.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Ikon globe / bahasa ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LpBgSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🌐", fontSize = 36.sp)
            }

            Spacer(Modifier.height(24.dp))

            // ── Judul bilingual ───────────────────────────────────────────────
            Text(
                text = if (selectedLang == "id")
                    "Pilih Bahasa"
                else
                    "Choose Language",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = LpTextPrimary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(36.dp))

            // ── Pilihan bahasa Indonesia ──────────────────────────────────────
            LanguageOption(
                flag = "🇮🇩",
                name = "Bahasa Indonesia",
                native = "Indonesia",
                isSelected = selectedLang == "id",
                onClick = { selectedLang = "id" }
            )

            Spacer(Modifier.height(12.dp))

            // ── Pilihan English ───────────────────────────────────────────────
            LanguageOption(
                flag = "🇬🇧",
                name = "English",
                native = "English",
                isSelected = selectedLang == "en",
                onClick = { selectedLang = "en" }
            )

            Spacer(Modifier.height(40.dp))

            // ── Tombol konfirmasi ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LpAccentGreen)
                    .clickable { onLanguageSelected(selectedLang) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedLang == "id") "Mulai  →" else "Start  →",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LpBgDeep
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Catatan kecil bilingual ───────────────────────────────────────
            Text(
                text = if (selectedLang == "id")
                    "Bahasa bisa diubah kapan saja lewat menu ≡"
                else
                    "Language can be changed anytime from the ≡ menu",
                fontSize = 12.sp,
                color = LpTextMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Komponen kartu pilihan bahasa ─────────────────────────────────────────────

@Composable
private fun LanguageOption(
    flag: String,
    name: String,
    native: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) LpAccentGreen else Color.Transparent,
        animationSpec = tween(200),
        label = "border_color"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) LpBgCard else LpBgSurface,
        animationSpec = tween(200),
        label = "bg_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bendera
        Text(text = flag, fontSize = 28.sp)

        // Teks
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LpTextPrimary
            )
            Text(
                text = native,
                fontSize = 12.sp,
                color = LpTextMuted
            )
        }

        // Centang
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) LpAccentGreen else LpBgSurface),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(
                    text = "✓",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LpBgDeep
                )
            }
        }
    }
}
