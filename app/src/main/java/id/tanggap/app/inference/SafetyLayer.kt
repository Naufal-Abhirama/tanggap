package id.tanggap.app.inference

import android.content.Context
import android.util.Log
import org.json.JSONArray
import id.tanggap.app.debug.TanggapLogger

object SafetyLayer {

    private const val TAG = "SafetyLayer"

    var currentLanguage: String = "id"
    var currentProvinsi: String? = null

    private val disclaimers = mapOf(
        "medis" to Pair(
            "⚠️ Ini bukan saran medis profesional. Segera cari bantuan tenaga kesehatan jika kondisi serius.",
            "⚠️ This is not professional medical advice. Seek medical help immediately if the condition is serious."
        ),
        "bangunan" to Pair(
            "⚠️ Penilaian keamanan bangunan harus dilakukan oleh teknisi berpengalaman. Jangan masuki bangunan yang tidak aman.",
            "⚠️ Building safety assessment must be done by experienced technicians. Do not enter unsafe buildings."
        ),
        "birokrasi" to Pair(
            "⚠️ Prosedur administrasi dapat berbeda per daerah. Konfirmasi ke kantor setempat.",
            "⚠️ Administrative procedures may vary by region. Confirm with your local office."
        ),
        "umum" to Pair(
            "⚠️ Informasi ini bersifat umum. Ikuti arahan petugas BPBD di lokasi kamu.",
            "⚠️ This information is general. Follow instructions from BPBD officers at your location."
        )
    )

    private val medisKeywords = listOf(
        "luka", "cedera", "darah", "patah", "p3k", "pertolongan pertama",
        "dokter", "rumah sakit", "pernapasan", "nadi", "pingsan", "rjp",
        "kondisi terdeteksi", "tingkat keparahan", "butuh dokter",
        "wound", "injury", "blood", "fracture", "first aid",
        "doctor", "hospital", "breathing", "pulse", "unconscious", "cpr"
    )
    private val bangunanKeywords = listOf(
        "bangunan", "gedung", "retak", "runtuh", "struktur", "fondasi",
        "dinding", "atap", "konstruksi", "level risiko", "evakuasi",
        "building", "crack", "collapse", "structure", "foundation",
        "wall", "roof", "construction", "risk level", "evacuation"
    )
    private val birokrasiKeywords = listOf(
        "ktp", "dokumen", "bantuan pemerintah", "pengajuan", "formulir",
        "administrasi", "dukcapil", "kelurahan", "klaim", "surat",
        "document", "government aid", "submission", "form",
        "administration", "claim", "letter", "certificate"
    )

    // Kontak BPBD 34 provinsi — fallback jika JSON tidak tersedia
    private val bpbdHardcode = mapOf(
        "Aceh" to "0651-7551",
        "Sumatera Utara" to "061-4158743",
        "Sumatera Barat" to "0751-7051",
        "Riau" to "0761-856000",
        "Kepulauan Riau" to "0771-7001",
        "Jambi" to "0741-7551",
        "Bengkulu" to "0736-21011",
        "Sumatera Selatan" to "0711-356400",
        "Kepulauan Bangka Belitung" to "0717-422062",
        "Lampung" to "0721-486068",
        "Banten" to "0254-267027",
        "DKI Jakarta" to "021-34584263",
        "Jawa Barat" to "022-7272639",
        "Jawa Tengah" to "024-7608833",
        "DI Yogyakarta" to "0274-580100",
        "Jawa Timur" to "031-8292555",
        "Bali" to "0361-255255",
        "Nusa Tenggara Barat" to "0370-621555",
        "Nusa Tenggara Timur" to "0380-831491",
        "Kalimantan Barat" to "0561-768686",
        "Kalimantan Tengah" to "0536-3221655",
        "Kalimantan Selatan" to "0511-3305566",
        "Kalimantan Timur" to "0541-741665",
        "Kalimantan Utara" to "0552-2027100",
        "Sulawesi Utara" to "0431-862233",
        "Gorontalo" to "0435-831119",
        "Sulawesi Tengah" to "0451-457789",
        "Sulawesi Barat" to "0426-2325018",
        "Sulawesi Selatan" to "0411-852233",
        "Sulawesi Tenggara" to "0401-3122233",
        "Maluku" to "0911-352122",
        "Maluku Utara" to "0921-3121100",
        "Papua Barat" to "0986-213507",
        "Papua" to "0967-534455"
    )

    private var bpbdFromJson: Map<String, String> = emptyMap()
    private var isLoaded = false

    fun loadBpbdContacts(context: Context) {
        if (isLoaded) return
        try {
            val json = context.assets.open("bpbd_contacts.json")
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val province =
                    if (obj.has("province")) obj.getString("province") else obj.getString("provinsi")
                val phone = if (obj.has("phone")) obj.getString("phone") else obj.getString("nomor")
                map[province] = phone
            }
            bpbdFromJson = map
            isLoaded = true
            Log.d(TAG, "BPBD contacts loaded: ${map.size} provinsi")
        } catch (e: Exception) {
            Log.w(TAG, "bpbd_contacts.json tidak ditemukan, pakai hardcode: ${e.message}")
            isLoaded = true
        }
    }

    /**
     * Konten darurat (RJP, tourniquet, evakuasi paksa) mengandung keyword yang mungkin
     * terdengar "berbahaya" di konteks normal. Whitelist ini memastikan konten tersebut
     * tidak dimodifikasi — hanya ditambahkan footer disclaimer.
     */
    private fun isEmergencyContent(text: String): Boolean {
        val lower = text.lowercase()
        return GemmaInferenceEngine.EMERGENCY_WHITELIST_KEYWORDS.any { lower.contains(it) }
    }

    fun detectTopic(responseText: String): String {
        val lower = responseText.lowercase()
        return when {
            medisKeywords.any { lower.contains(it) } -> "medis"
            bangunanKeywords.any { lower.contains(it) } -> "bangunan"
            birokrasiKeywords.any { lower.contains(it) } -> "birokrasi"
            else -> "umum"
        }
    }

    fun getDisclaimer(topic: String): String {
        val pair = disclaimers[topic] ?: disclaimers["umum"]!!
        return if (currentLanguage == "en") pair.second else pair.first
    }

    fun getBpbdContact(provinsi: String? = null): String {
        if (provinsi != null) {
            val phone = bpbdFromJson[provinsi] ?: bpbdHardcode[provinsi]
            if (phone != null) return "BPBD $provinsi: $phone"
        }
        return if (currentLanguage == "en") "National BPBD: 117" else "BPBD Nasional: 117"
    }

    fun appendSafetyFooter(response: String, provinsi: String? = null): String {
        if (isEmergencyContent(response)) {
            Log.d(TAG, "Emergency content — appending footer only, no modification")
        }

        val topic = detectTopic(response)
        val disclaimer = getDisclaimer(topic)
        val resolvedProvinsi = provinsi ?: currentProvinsi
        val bpbd = getBpbdContact(resolvedProvinsi)

        // ── LOG: safety layer ──────────────────────────────────────────────────
        TanggapLogger.logSafetyLayer(
            topicDetected = topic,
            disclaimerAdded = disclaimer,
            bpbdContact = bpbd
        )

        return """$response
 
---
$disclaimer
 
📞 $bpbd"""
    }
}
