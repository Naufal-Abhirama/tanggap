package id.tanggap.app.inference

import id.tanggap.app.data.DisasterType

enum class DisasterPhase { UNKNOWN, ACTIVE, RECOVERY }

/**
 * ConversationContext — menyimpan state sesi darurat antar turn.
 *
 * PERUBAHAN:
 * - Tambah `phase: DisasterPhase` untuk routing ACTIVE vs RECOVERY
 * - Tambah `multiVictim: Boolean` untuk flag triage multi-korban
 * - Tambah `visionSummary: String?` untuk inject konteks visual ke prompt
 * - Tambah `triageDone: Boolean` — flag persistent pengganti deteksi dari UI hidden messages
 */
data class ConversationContext(
    val disasterType: DisasterType? = null,
    val lastLocation: String? = null,
    val hasImage: Boolean = false,
    val turnCount: Int = 0,
    val lastUserQuery: String = "",
    val lastResponseSummary: String = "",
    val chatHistory: List<Pair<String, String>> = emptyList(),
    val isResolved: Boolean = false,
    val phase: DisasterPhase = DisasterPhase.UNKNOWN,
    val multiVictim: Boolean = false,
    val visionSummary: String? = null,
    // triageDone: true setelah user menyelesaikan 1 sesi triage.
    // Digunakan sebagai guard agar triage card tidak muncul lagi di turn berikutnya.
    val triageDone: Boolean = false
) {
    companion object {
        const val MAX_TURNS   = 5
        const val MAX_HISTORY = 4

        private val RECOVERY_KEYWORDS_ID = listOf(
            "sudah surut", "udah surut", "banjir surut", "air surut",
            "gempa sudah berhenti", "gempa udah berhenti", "sudah aman",
            "sekarang mau", "abis ini", "setelah ini", "besok",
            "bersih-bersih", "bersihin", "bersihkan", "pembersihan",
            "balik ke rumah", "kembali ke rumah", "mau pulang",
            "benerin rumah", "perbaiki rumah", "renovasi", "bangun ulang",
            "sudah keluar", "udah keluar", "sudah evakuasi", "udah evakuasi",
            "pasca bencana", "setelah bencana", "habis gempa", "habis banjir",
            "rumah rusak", "kondisi rumah", "cek rumah", "periksa rumah",
            "bagaimana cara membersihkan", "cara bersihkan",
            "kapan bisa kembali", "kapan bisa masuk", "aman untuk masuk",
            "dokumen", "bantuan pemerintah", "pengungsi", "posko",
            "memulihkan", "pemulihan", "pulihkan", "memulihkan kondisi",
            "perbaikan", "memperbaiki",
            "sambil menunggu", "selagi menunggu", "sementara menunggu",
            "sembari menunggu", "saat menunggu bantuan", "sebelum bantuan datang",
            "menunggu bantuan", "menunggu pertolongan",
            "setelah gempa", "setelah banjir", "setelah longsor",
            "pasca gempa", "pasca banjir", "pasca longsor",
            "langkah pemulihan", "cara pemulihan", "proses pemulihan",
            "bangkit", "bangun kembali",
            "kondisi pasca", "situasi pasca",
            "apa yang perlu dilakukan setelah", "apa yang harus dilakukan setelah",
            "sudah ditangani", "sudah aman sekarang",
            "sekarang apa", "selanjutnya apa", "langkah selanjutnya",
            "evakuasi sudah", "sudah di pengungsian",
            "di pengungsian", "di posko", "posko pengungsian",
            "kerusakan rumah", "cek kerusakan", "periksa kerusakan",
            "gas bocor", "listrik padam", "air bersih", "sumber air",
            "membersihkan lumpur", "bersihkan lumpur",
            "rekonstruksi", "rehabilitasi", "sambil menunggu bantuan", "sementara bantuan",
            "sebelum tim datang", "sebelum petugas datang",
            "apa yang bisa dilakukan sekarang", "bisa saya lakukan sendiri",
            "mandiri", "sementara ini"
        )
        private val RECOVERY_KEYWORDS_EN = listOf(
            "water receded", "flood gone", "earthquake stopped", "already safe",
            "going to", "after this", "tomorrow", "cleanup", "clean up",
            "back home", "return home", "fix house", "repair house", "rebuild",
            "already out", "already evacuated", "post disaster",
            "after the earthquake", "after the flood",
            "house damaged", "check house", "inspect building",
            "when can i return", "safe to enter", "safe to go back",
            "documents", "government aid", "shelter", "refugee",
            "recover", "recovery", "recovering", "restore", "restoration",
            "while waiting", "while i wait", "while waiting for help",
            "before help arrives", "before rescue comes",
            "after the disaster", "post earthquake", "post flood",
            "rebuilding", "reconstruction", "rehabilitate",
            "repair house", "fix damage", "assess damage", "check damage",
            "already evacuated", "at the shelter", "at evacuation point",
            "what to do now", "next steps", "what next",
            "clean the mud", "mud cleaning", "sanitize",
            "gas leak", "power outage", "clean water", "water source"
        )

        val SAFE_KEYWORDS_ID = listOf(
            "udah aman", "sudah aman", "aman", "udah keluar", "sudah keluar",
            "udah selamat", "sudah selamat", "selamat", "udah mendingan",
            "sudah mendingan", "mendingan", "udah baikan", "sudah baikan",
            "baikan", "sudah berhasil", "udah berhasil", "sudah bisa keluar",
            "udah bisa keluar", "sudah diselamatkan", "sudah ditolong",
            "udah ditolong", "bantuan sudah datang", "bantuan udah datang",
            "sudah sampai", "udah sampai", "sudah di tempat aman",
            "udah di tempat aman", "tidak apa-apa", "tidak apa apa",
            "gapapa", "ga papa", "engga apa", "nggak apa", "sudah lebih baik",
            "lebih baik", "agak mendingan", "agak membaik", "membaik"
        )
        val SAFE_KEYWORDS_EN = listOf(
            "i'm safe", "im safe", "i am safe", "i'm okay", "im okay",
            "i am okay", "i'm fine", "im fine", "i am fine", "already safe",
            "made it out", "got out", "escaped", "rescued", "help arrived",
            "help is here", "someone found me", "i'm out", "im out",
            "i am out", "better now", "feeling better", "i survived",
            "we're safe", "we are safe", "all good", "no longer trapped",
            "free now", "got rescued"
        )

        val MULTI_VICTIM_KEYWORDS_ID = listOf(
            "beberapa orang", "banyak orang", "banyak korban", "banyak yang terluka",
            "banyak yang pingsan", "ada korban lain", "beberapa korban",
            "dua orang", "tiga orang", "empat orang", "lima orang",
            "ada banyak korban", "banyak yang terjebak",
            "orang lain juga luka", "orang lain juga terluka"
        )
        val MULTI_VICTIM_KEYWORDS_EN = listOf(
            "several people", "many people", "multiple victims", "multiple people",
            "people are injured", "some people are injured", "a few people",
            "two people", "three people", "four people",
            "there are victims", "there are injured people",
            "group of people", "others are also injured"
        )

        fun extractLocation(query: String): String? {
            val locationKeywords = listOf(
                "di ", "daerah ", "wilayah ", "kota ", "kabupaten ", "desa ",
                "in ", "near ", "at ", "around ", "city of ", "area of "
            )
            val lower = query.lowercase()
            for (kw in locationKeywords) {
                val idx = lower.indexOf(kw)
                if (idx != -1) {
                    val after    = query.substring(idx + kw.length).trim()
                    val location = after.split(" ", ",", ".").firstOrNull()
                    if (!location.isNullOrBlank() && location.length > 2) return location
                }
            }
            return null
        }

        fun summarizeQuery(query: String) = query.take(200)

        fun summarizeResponse(response: String): String {
            val situasiLine = response.lines().find {
                it.startsWith("**SITUASI:**") || it.startsWith("**SITUATION:**") ||
                        it.startsWith("**KONDISI TERDETEKSI:**") || it.startsWith("**DETECTED CONDITION:**") ||
                        it.startsWith("**KONDISI:**") || it.startsWith("**CONDITION:**")
            }
            return (situasiLine ?: response.take(150)).take(150)
        }

        fun compressResponse(response: String): String {
            val lines = response.lines()
            val situasi = lines.find {
                it.startsWith("**SITUASI:**") || it.startsWith("**SITUATION:**") ||
                        it.startsWith("**KONDISI:**") || it.startsWith("**CONDITION:**")
            } ?: ""
            val lakukan = lines.dropWhile {
                !it.startsWith("**LAKUKAN") && !it.startsWith("**DO THIS") &&
                        !it.startsWith("**PRIORITAS") && !it.startsWith("**PRIORITY")
            }.take(4).joinToString(" ")
            return "$situasi $lakukan".trim().take(300)
        }

        fun isSafeStatement(query: String, lang: String = "id"): Boolean {
            val lower = query.trim().lowercase()
            val keywords = if (lang == "en") SAFE_KEYWORDS_EN else SAFE_KEYWORDS_ID
            return keywords.any { lower.contains(it) }
        }

        fun isRecoveryQuery(query: String, lang: String = "id"): Boolean {
            val lower = query.trim().lowercase()
            val keywords = if (lang == "en") RECOVERY_KEYWORDS_EN else RECOVERY_KEYWORDS_ID
            return keywords.any { lower.contains(it) }
        }

        fun isMultiVictimQuery(query: String, lang: String = "id"): Boolean {
            val lower = query.trim().lowercase()
            val keywords = if (lang == "en") MULTI_VICTIM_KEYWORDS_EN else MULTI_VICTIM_KEYWORDS_ID
            return keywords.any { lower.contains(it) }
        }

        fun isFollowUpClarification(
            query: String,
            history: List<Pair<String, String>>,
            lang: String = "id"
        ): Boolean {
            if (history.isEmpty()) return false
            val lower = query.trim().lowercase()
            val wordCount = lower.split(Regex("\\s+")).size
            if (wordCount > 10) return false

            val clarificationMarkers = if (lang == "en") listOf(
                "because", "since", "it's", "its", "the ", "that", "this",
                "actually", "i mean", "i meant", "specifically", "also", "and ",
                "by the way", "oh and", "forgot to mention", "plus", "oh ",
                "just so you know", "fyi"
            ) else listOf(
                "soalnya", "karena", "sebab", "itu ", "yang ", "tadi", "maksudnya",
                "lebih tepatnya", "oh iya", "lupa", "juga ", "dan ", "sama ",
                "tambahan", "btw", "eh iya", "sebenarnya", "tepatnya", "oh ",
                "oh ya", "eh ", "nah ", "trus ", "terus "
            )

            return clarificationMarkers.any {
                lower.startsWith(it.trim()) || lower.contains(" ${it.trim()} ")
            }
        }
    }

    fun isActive() = disasterType != null && turnCount < MAX_TURNS && !isResolved

    fun isFollowUpClarification(query: String, lang: String = "id"): Boolean {
        return Companion.isFollowUpClarification(query, chatHistory, lang)
    }

    fun toHistoryBlock(lang: String = "id"): String {
        if (chatHistory.isEmpty()) return ""

        return buildString {
            if (lang == "en") {
                append("=== CONVERSATION HISTORY ===\n")
                chatHistory.forEach { (userMsg, aiMsg) ->
                    append("User: $userMsg\n")
                    append("Assistant: $aiMsg\n\n")
                }
                append("=== CONTINUE FROM ABOVE CONTEXT ===\n")
                append("The user's situation has already been established. ")
                append("DO NOT start over with generic advice. ")
                append("Build upon what is already known about their condition.\n")
            } else {
                append("=== RIWAYAT PERCAKAPAN ===\n")
                chatHistory.forEach { (userMsg, aiMsg) ->
                    append("User: $userMsg\n")
                    append("Asisten: $aiMsg\n\n")
                }
                append("=== LANJUTKAN DARI KONTEKS DI ATAS ===\n")
                append("Kondisi user sudah diketahui dari percakapan sebelumnya. ")
                append("JANGAN mulai dari awal dengan saran umum. ")
                append("Lanjutkan berdasarkan kondisi spesifik yang sudah dinyatakan.\n")
            }
        }
    }

    fun toContextBlock(lang: String = "id"): String {
        if (!isActive()) return ""
        val parts = mutableListOf<String>()

        if (lang == "en") {
            if (lastLocation != null) parts.add("- Location: $lastLocation")
            parts.add("- Disaster context: ${disasterTypeLabel(lang)}")
            parts.add("- Phase: ${if (phase == DisasterPhase.RECOVERY) "POST-DISASTER RECOVERY" else "ACTIVE EMERGENCY"}")
            if (multiVictim) parts.add("- Multiple victims present")
            if (visionSummary != null) parts.add("- Visual analysis: $visionSummary")
        } else {
            if (lastLocation != null) parts.add("- Lokasi: $lastLocation")
            parts.add("- Konteks bencana: ${disasterTypeLabel(lang)}")
            parts.add("- Fase: ${if (phase == DisasterPhase.RECOVERY) "PEMULIHAN PASCA BENCANA" else "DARURAT AKTIF"}")
            if (multiVictim) parts.add("- Ada beberapa korban")
            if (visionSummary != null) parts.add("- Analisis visual: $visionSummary")
        }
        return parts.joinToString("\n")
    }

    private fun disasterTypeLabel(lang: String): String = when (disasterType) {
        is DisasterType.GEMPA   -> if (lang == "en") "earthquake" else "gempa bumi"
        is DisasterType.BANJIR  -> if (lang == "en") "flood" else "banjir"
        is DisasterType.LONGSOR -> if (lang == "en") "landslide" else "tanah longsor"
        is DisasterType.COMPOUND -> {
            val types = (disasterType as DisasterType.COMPOUND).types
            if (lang == "en") types.joinToString(" + ") { t ->
                when (t) {
                    is DisasterType.GEMPA   -> "earthquake"
                    is DisasterType.BANJIR  -> "flood"
                    is DisasterType.LONGSOR -> "landslide"
                    else                    -> "disaster"
                }
            }
            else types.joinToString(" + ") { t ->
                when (t) {
                    is DisasterType.GEMPA   -> "gempa bumi"
                    is DisasterType.BANJIR  -> "banjir"
                    is DisasterType.LONGSOR -> "tanah longsor"
                    else                    -> "bencana"
                }
            }
        }
        else -> if (lang == "en") "general disaster" else "kebencanaan umum"
    }

    fun nextTurn(
        newDisasterType: DisasterType,
        userQuery: String,
        aiResponse: String,
        withImage: Boolean = false,
        resolved: Boolean = false,
        newPhase: DisasterPhase? = null,
        isMultiVictim: Boolean = false,
        newVisionSummary: String? = null
    ): ConversationContext {
        val newEntry = Pair(
            userQuery.take(200),
            compressResponse(aiResponse)
        )
        val updatedHistory = (chatHistory + newEntry).takeLast(MAX_HISTORY)

        val resolvedPhase = newPhase ?: when {
            resolved            -> DisasterPhase.RECOVERY
            phase == DisasterPhase.RECOVERY -> DisasterPhase.RECOVERY
            isRecoveryQuery(userQuery, "id") || isRecoveryQuery(userQuery, "en")
                -> DisasterPhase.RECOVERY
            else                -> DisasterPhase.ACTIVE
        }

        return ConversationContext(
            disasterType        = newDisasterType,
            lastLocation        = extractLocation(userQuery) ?: lastLocation,
            hasImage            = withImage,
            turnCount           = if (turnCount >= MAX_TURNS) 1 else turnCount + 1,
            lastUserQuery       = summarizeQuery(userQuery),
            lastResponseSummary = summarizeResponse(aiResponse),
            chatHistory         = updatedHistory,
            isResolved          = resolved,
            phase               = resolvedPhase,
            multiVictim         = isMultiVictim || multiVictim,
            visionSummary       = newVisionSummary ?: visionSummary,
            triageDone          = triageDone  // pertahankan flag triage — di-set dari luar via markTriageDone()
        )
    }

    fun reset() = ConversationContext()
    fun markResolved(): ConversationContext = copy(isResolved = true)

    // Tandai bahwa triage sudah selesai dilakukan di sesi ini.
    // Dipanggil dari MainActivity setelah user submit jawaban triage.
    fun markTriageDone(): ConversationContext = copy(triageDone = true)
}
