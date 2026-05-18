package id.tanggap.app.tts

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTSManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    var onSpeakingDone: (() -> Unit)? = null

    // ── AudioManager untuk kontrol volume ──────────────────────────────────────
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // Bahasa saat ini: "id" atau "en"
    var currentLanguage: String = "id"
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyLanguage(currentLanguage)
                // Lebih natural: sedikit lebih lambat, pitch sedikit lebih rendah
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onSpeakingDone?.invoke()
                    }
                    override fun onError(utteranceId: String?) {}
                })
                Log.d("TTSManager", "TTS siap, bahasa: $currentLanguage, ready: $isReady")
            }
        }
    }

    // ── Mode Senyap Otomatis ───────────────────────────────────────────────────
    /**
     * Aktifkan/nonaktifkan mode senyap.
     * Dipanggil otomatis saat baterai kritis (<15%) atau mode SOS aktif.
     *
     * @param isSilent true = volume rendah (15%) + speech rate lebih lambat
     *                 false = kembali ke volume & speech rate normal
     */
    fun setSilentMode(isSilent: Boolean) {
        // 1. Sesuaikan speech rate TTS
        tts?.setSpeechRate(if (isSilent) 0.9f else 0.85f)

        // 2. Sesuaikan volume stream musik (yang dipakai TTS)
        val targetVolume = if (isSilent) (maxVol * 0.15f).toInt() else maxVol
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume.coerceIn(0, maxVol),
            0   // Tanpa popup UI volume
        )

        Log.d("TTSManager", "Silent mode: $isSilent | volume: $targetVolume/$maxVol")
    }

    /**
     * Ganti bahasa TTS saat runtime.
     * @param lang "id" untuk Bahasa Indonesia, "en" untuk English
     */
    fun setLanguage(lang: String) {
        currentLanguage = lang
        applyLanguage(lang)
    }

    private fun applyLanguage(lang: String) {
        val locale = if (lang == "en") Locale("en", "US") else Locale("id", "ID")
        val result = tts?.setLanguage(locale)
        isReady = result != TextToSpeech.LANG_MISSING_DATA
                && result != TextToSpeech.LANG_NOT_SUPPORTED
        Log.d("TTSManager", "Bahasa diset ke: $lang, locale: $locale, ready: $isReady")
    }

    fun speak(text: String) {
        if (!isReady) return
        val cleanText = text
            .replace(Regex("#{1,6}\\s"), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace("*", "")
            .replace(Regex("-\\s"), "")
            .replace(Regex("\\n{2,}"), ". ")
            .trim()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
