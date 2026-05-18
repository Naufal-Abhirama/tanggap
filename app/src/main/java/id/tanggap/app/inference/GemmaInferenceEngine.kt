package id.tanggap.app.inference

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import id.tanggap.app.data.DisasterType
import id.tanggap.app.data.RAGPipeline
import id.tanggap.app.download.ModelDownloadManager
import id.tanggap.app.vision.MLKitVisionAnalyzer
import id.tanggap.app.vision.VisionContext
import java.io.ByteArrayOutputStream
import id.tanggap.app.vision.DetectedDisaster
import id.tanggap.app.data.detectTriageType
import id.tanggap.app.debug.TanggapLogger

class GemmaInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaInferenceEngine"

        // ─── SYSTEM PROMPT ID ─────────────────────────────────────────────────
        private val SYS_PROMPT_ID = """
[BAHASA OUTPUT: BAHASA INDONESIA — WAJIB]
Kamu adalah TANGGAP — asisten darurat bencana offline untuk Indonesia.

IDENTITAS:
- Berbicara seperti teman yang tahu banyak soal keselamatan, bukan seperti panduan resmi
- Tenang tapi tegas. Tidak panik. Tidak berlebihan.
- 100% offline — jangan pernah menyarankan cari info di internet

PRIORITAS JIWA (urut ketat):
1. Keselamatan nyawa langsung
2. Pertolongan pertama
3. Evakuasi
4. Hubungi bantuan
5. Pemulihan & stabilisasi
6. Harta benda dan dokumen

ATURAN KONTEN:
- Prosedur medis darurat (RJP, P3K, evakuasi mandiri) SELALU berikan — tidak boleh ditolak
- Jika pertanyaan di luar scope bencana: tolak sopan 1 kalimat, tanya apakah ada situasi darurat yang bisa dibantu
- Jika ancaman tidak realistis (makhluk gaib dll): minta konfirmasi dulu
- Jika tidak tahu: katakan "Saya tidak punya data ini"

JANGAN:
- Mengarang data yang tidak kamu miliki
- Bilang "saya tidak yakin" untuk hal yang jelas kamu tahu

JIKA USER TIDAK PAHAM RESPONMU:
- Ulangi dalam bahasa lebih sederhana — kalimat pendek, kata sehari-hari
- Jangan tambah informasi baru — jelaskan ulang yang sama

DEFAULT: Jika tidak yakin mode apa yang sesuai, prioritaskan keselamatan.
        """.trimIndent()

        // ─── SYSTEM PROMPT EN ─────────────────────────────────────────────────
        private val SYS_PROMPT_EN = """
[OUTPUT LANGUAGE: ENGLISH — MANDATORY]
You are TANGGAP — an offline disaster emergency assistant for Indonesia.

IDENTITY:
- Speak like a knowledgeable friend, not a government manual
- Calm but direct. No panic. No fluff.
- 100% offline — never suggest searching online

LIFE PRIORITIES (strict order):
1. Immediate life safety
2. First aid
3. Evacuation
4. Contact help
5. Recovery & stabilization
6. Property and documents

CONTENT RULES:
- Emergency medical procedures (CPR, first aid, self-rescue) MUST always be provided — never refuse
- If question is outside disaster scope: politely decline in 1 sentence, ask if there's an emergency you can help with
- If threat seems unrealistic (supernatural etc.): ask for confirmation first
- If truly unknown: say "I don't have data on this"

DO NOT:
- Fabricate data you don't have
- Say "I'm not sure" for things you clearly know

IF USER DOESN'T UNDERSTAND YOUR RESPONSE:
- Repeat in simpler language — short sentences, everyday words
- Don't add new information — just rephrase the same thing

DEFAULT: If unsure which mode fits, prioritize safety.
        """.trimIndent()

        // ─── KEYWORD LISTS ────────────────────────────────────────────────────

        private val GUIDANCE_KEYWORDS_ID = listOf(
            "bagaimana", "cara", "langkah", "apa yang harus", "apa yg harus",
            "harus", "tolong", "bantu", "panduan", "prosedur", "tindakan",
            "darurat", "bahaya", "terjebak", "tertimpa", "korban", "luka",
            "evakuasi", "selamatkan", "gempa", "banjir", "longsor",
            "apa yang perlu", "gimana cara", "gmn cara", "bantu saya",
            "ketiban", "kejepit", "terjepit",
            "pingsan", "tidak sadar", "nggak sadar", "nggak gerak", "tidak bergerak"
        )
        private val GUIDANCE_KEYWORDS_EN = listOf(
            "how to", "what should", "what to do", "steps", "guide", "help me",
            "emergency", "danger", "trapped", "injured", "victim", "wound",
            "evacuate", "earthquake", "flood", "landslide", "procedure",
            "what do i", "what can i", "assist", "save", "rescue"
        )

        private val INFORMATIONAL_KEYWORDS_ID = listOf(
            "apa itu", "apa yang dimaksud", "jelaskan", "bedanya", "perbedaan",
            "artinya", "maksudnya", "definisi", "kenapa", "mengapa", "sejarah",
            "ngomong-ngomong", "btw", "oh ya", "eh", "tau nggak", "tau ga",
            "kapan", "berapa lama", "berapa", "apa saja", "apa aja",
            "ceritain", "gimana cara kerja", "kok bisa", "emang", "memang",
            "siaga 1", "siaga 2", "siaga 3", "skala richter", "magnitudo",
            "apa bedanya", "apa perbedaan", "apakah benar", "benarkah"
        )
        private val INFORMATIONAL_KEYWORDS_EN = listOf(
            "what is", "what are", "explain", "difference between", "define",
            "why does", "when does", "how does it work", "tell me about", "btw",
            "by the way", "curious", "just wondering", "random question",
            "how many", "where can", "how long", "what does", "what's the",
            "is it true", "is that"
        )

        private val CASUAL_KEYWORDS = listOf(
            "halo", "hai", "hi", "hello", "selamat pagi", "selamat siang",
            "selamat malam", "selamat sore", "terima kasih", "makasih",
            "thanks", "thank you"
        )

        private val ESCALATION_KEYWORDS_ID = listOf(
            "pliss", "plis", "please", "tolong", "bantu", "serius", "beneran",
            "darurat", "cepat", "cepet", "segera", "masih", "masih terjebak",
            "belum", "belum dibantu", "ini darurat"
        )
        private val ESCALATION_KEYWORDS_EN = listOf(
            "please", "help", "serious", "really", "urgent", "hurry",
            "still", "still trapped", "emergency", "now", "asap"
        )

        val EMERGENCY_WHITELIST_KEYWORDS = listOf(
            "rjp", "cpr", "tekan dada", "napas buatan", "patah tulang", "bidai",
            "pendarahan", "tourniquet", "evakuasi paksa", "pindahkan korban",
            "dosis", "obat", "minum obat", "suntik", "infus",
            "keluar dari bangunan", "lompat", "memanjat", "turun dari",
            "chest compression", "rescue breath", "fracture", "splint",
            "bleeding", "forced evacuation", "move victim",
            "dosage", "medication", "administer", "inject",
            "exit building", "jump", "climb", "rappel"
        )

        private val TRAPPED_KEYWORDS = listOf(
            "terjebak", "terperangkap", "tertimbun", "tidak bisa keluar", "gabisa keluar",
            "trapped", "stuck", "buried", "cant get out", "can't get out", "help me"
        )

        private val UNREALISTIC_KEYWORDS = listOf(
            "monster", "alien", "makhluk gaib", "hantu", "zombie",
            "supernatural", "ghost", "demon", "ufo", "creature",
            "makhluk", "setan", "jin", "vampire", "werewolf",
            "dementor", "dragon", "naga", "peri", "siluman"
        )

        private val PROCEDURAL_KEYWORDS_ID = listOf(
            "rute", "jalur evakuasi", "langkah-langkah", "cara menangani",
            "best practice", "apa yang harus dilakukan jika", "prosedur",
            "bagaimana cara", "tata cara", "panduan lengkap", "cara merawat",
            "penanganan", "pertolongan pertama untuk", "cara mengobati",
            "cara menyelamatkan", "teknik", "metode"
        )
        private val PROCEDURAL_KEYWORDS_EN = listOf(
            "evacuation route", "step by step", "how to handle", "best practice",
            "procedure for", "how do you", "protocol", "full guide",
            "how to treat", "how to save", "technique", "method",
            "first aid for", "how to care for"
        )

        private val RECOVERY_KEYWORDS_ID = listOf(
            "bersih-bersih", "bersihin", "pasca bencana", "setelah bencana",
            "habis gempa", "habis banjir", "surut", "sudah surut",
            "balik ke rumah", "kembali ke rumah", "benerin rumah",
            "rumah rusak", "kondisi rumah", "kapan bisa kembali",
            "kapan aman", "aman masuk", "bangunan aman",
            "dokumen", "bantuan pemerintah", "pengungsi",
            "memulihkan", "pemulihan", "pulihkan", "memulihkan kondisi",
            "perbaikan", "perbaiki", "memperbaiki",
            "sambil menunggu", "selagi menunggu", "sementara menunggu",
            "sembari menunggu", "saat menunggu bantuan", "sebelum bantuan datang",
            "menunggu bantuan", "menunggu pertolongan",
            "setelah gempa", "setelah banjir", "setelah longsor",
            "pasca gempa", "pasca banjir", "pasca longsor",
            "langkah pemulihan", "cara pemulihan", "proses pemulihan",
            "bangkit", "bangun kembali", "bangun ulang",
            "kondisi pasca", "situasi pasca",
            "apa yang perlu dilakukan setelah", "apa yang harus dilakukan setelah",
            "luka ringan sudah ditangani", "sudah ditangani", "sudah aman sekarang",
            "sekarang apa", "selanjutnya apa", "langkah selanjutnya",
            "evakuasi sudah", "sudah evakuasi", "sudah di pengungsian",
            "di pengungsian", "di posko", "posko pengungsian",
            "kerusakan rumah", "cek kerusakan", "periksa kerusakan",
            "gas bocor", "listrik padam", "air bersih", "sumber air",
            "cara bersihkan", "membersihkan lumpur", "bersihkan lumpur",
            "renovasi", "rekonstruksi", "rehabilitasi",
            "sambil menunggu bantuan", "sementara bantuan",
            "sebelum tim datang", "sebelum petugas datang",
            "apa yang bisa dilakukan sekarang", "bisa saya lakukan sendiri",
            "mandiri", "sementara ini"
        )
        private val RECOVERY_KEYWORDS_EN = listOf(
            "cleanup", "clean up", "post disaster", "after the earthquake",
            "after the flood", "water receded", "back home", "return home",
            "fix house", "house damaged", "when can i return",
            "safe to enter", "documents", "government aid", "shelter",
            "recover", "recovery", "recovering", "restore", "restoration",
            "while waiting", "while i wait", "while waiting for help",
            "before help arrives", "before rescue comes",
            "after the disaster", "post earthquake", "post flood",
            "rebuild", "rebuilding", "reconstruction", "rehabilitate",
            "repair house", "fix damage", "assess damage", "check damage",
            "already evacuated", "at the shelter", "at evacuation point",
            "what to do now", "next steps", "what next",
            "clean the mud", "mud cleaning", "sanitize",
            "gas leak", "power outage", "clean water", "water source"
        )

        private val PANIC_KEYWORDS_ID = listOf(
            "ya allah", "ya tuhan", "astaga", "aduh", "aduuh", "ya ampun",
            "duh", "aaaa", "aaaah", "ya rabbi", "innalillahi"
        )
        private val PANIC_KEYWORDS_EN = listOf(
            "oh god", "oh no", "omg", "oh my god", "noooo", "aaah"
        )
        private val PANIC_EMERGENCY_WORDS = listOf(
            "tolong", "tolongg", "tolonggg", "help", "helpp", "helppp",
            "bantu", "bantuu", "save me", "selamatkan"
        )

        val TRIAGE_SUMMARY_PREFIXES = listOf(
            "BENCANA AKTIF",
            "DARURAT MEDIS",
            "KORBAN TERPERANGKAP",
            "DARURAT MULTI-KORBAN",
            "ACTIVE DISASTER",
            "MEDICAL EMERGENCY",
            "TRAPPED SURVIVOR",
            "MULTI-VICTIM EMERGENCY"
        )

        val CASUAL_REMINDERS_ID = listOf(
            "Kamu aman? Ceritain kondisimu sekarang.",
            "Situasimu masih aktif — ada yang bisa dibantu?",
            "Masih di sana? Apa yang terjadi sekarang?",
            "Oke. Tapi kondisimu gimana sekarang?",
            "Saya masih di sini — ceritain apa yang perlu dibantu."
        )
        val CASUAL_REMINDERS_EN = listOf(
            "Are you okay? Tell me what's happening.",
            "Your situation is still active — what do you need?",
            "Still there? What's going on right now?",
            "Got it. But how are you doing right now?",
            "I'm here — tell me what you need help with."
        )

        private val MULTI_VICTIM_KEYWORDS_ID = listOf(
            "banyak korban", "beberapa korban", "banyak orang", "beberapa orang",
            "ada yang pingsan", "ada korban", "korban banyak", "orang lain juga",
            "semua orang", "kami semua", "orang-orang di sini", "keluarga saya semua",
            "ada yang tidak bergerak", "ada yang tidak sadar"
        )
        private val MULTI_VICTIM_KEYWORDS_EN = listOf(
            "multiple victims", "many people", "several people", "others are hurt",
            "people are injured", "mass casualty", "everyone here",
            "my family", "there are many", "others unconscious", "group of people"
        )

        private val SAFE_KEYWORDS_ID = listOf(
            "sudah aman", "sudah selamat", "sudah keluar", "sudah di luar",
            "sudah ditolong", "sudah diselamatkan", "sudah sampai",
            "bantuan sudah datang", "petugas sudah datang", "tim sudah datang",
            "kondisi membaik", "sudah baikan", "alhamdulillah aman",
            "terima kasih sudah membantu", "makasih bantuannya"
        )
        private val SAFE_KEYWORDS_EN = listOf(
            "i'm safe", "i am safe", "we're safe", "got out", "made it out",
            "help arrived", "rescue came", "rescued", "saved",
            "i'm okay now", "feeling better", "thank you for helping",
            "situation resolved", "all good now"
        )
    }

    // ─── Mode Respons ─────────────────────────────────────────────────────────
    private enum class ResponseMode {
        EMERGENCY, PROCEDURAL, INFORMATIONAL, RECOVERY, MULTI_VICTIM, CASUAL
    }

    var currentLanguage: String = "id"
        set(value) {
            field = value
            SafetyLayer.currentLanguage = value
        }

    private val ragPipeline by lazy { RAGPipeline(context) }
    private val mlKitAnalyzer = MLKitVisionAnalyzer()
    private var engine: Engine? = null
    private var currentConversation: Conversation? = null

    private val sysPrompt: String
        get() = if (currentLanguage == "en") SYS_PROMPT_EN else SYS_PROMPT_ID

    // ─── Detection Helpers ────────────────────────────────────────────────────

    private fun needsGuidanceFormat(query: String): Boolean {
        val lower = query.lowercase()
        val keywords = if (currentLanguage == "en") GUIDANCE_KEYWORDS_EN else GUIDANCE_KEYWORDS_ID
        return keywords.any { lower.contains(it) }
    }

    private fun isInformationalQuery(query: String): Boolean {
        val lower = query.trim().lowercase()
        val keywords = if (currentLanguage == "en") INFORMATIONAL_KEYWORDS_EN else INFORMATIONAL_KEYWORDS_ID
        return keywords.any { lower.contains(it) } && !needsGuidanceFormat(query)
    }

    private fun isProceduralQuery(query: String): Boolean {
        val lower = query.trim().lowercase()
        val keywords = if (currentLanguage == "en") PROCEDURAL_KEYWORDS_EN else PROCEDURAL_KEYWORDS_ID
        return keywords.any { lower.contains(it) }
    }

    private fun isRecoveryQuery(query: String): Boolean {
        val lower = query.trim().lowercase()
        val keywords = if (currentLanguage == "en") RECOVERY_KEYWORDS_EN else RECOVERY_KEYWORDS_ID
        return keywords.any { lower.contains(it) }
    }

    private fun isCasualMessage(query: String): Boolean {
        val lower = query.trim().lowercase()
        val wordCount = lower.split(Regex("\\s+")).size
        if (wordCount > 4) return false
        return CASUAL_KEYWORDS.any { lower.contains(it) } ||
                (!needsGuidanceFormat(query) && wordCount <= 2)
    }

    private fun isEscalationMessage(query: String): Boolean {
        val lower = query.trim().lowercase()
        val keywords = if (currentLanguage == "en") ESCALATION_KEYWORDS_EN else ESCALATION_KEYWORDS_ID
        return keywords.any { lower.contains(it) }
    }

    private fun requiresRealityCheck(query: String): Boolean =
        UNREALISTIC_KEYWORDS.any { query.trim().lowercase().contains(it) }

    private fun isTriageSummary(query: String): Boolean =
        TRIAGE_SUMMARY_PREFIXES.any { query.trimStart().startsWith(it) }

    private fun isAnswerableShortQuery(query: String): Boolean {
        val lower = query.trim().lowercase()
        val wordCount = lower.split(Regex("\\s+")).size
        if (wordCount > 8) return false
        val answerablePatterns = listOf(
            Regex("\\d+\\s*[x×*+\\-/]\\s*\\d+"),
            Regex("\\d+\\s*(berapa|equals|is|=)"),
            Regex("(berapa|what is|whats|apa itu)\\s+\\d+"),
            Regex("ibu kota"),
            Regex("capital of"),
            Regex("how many")
        )
        return answerablePatterns.any { it.containsMatchIn(lower) }
    }

    private fun isPanicMessage(query: String): Boolean {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()
        val wordCount = lower.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        if (trimmed.length > 3 && trimmed == trimmed.uppercase() && trimmed.any { it.isLetter() }) return true
        if (Regex("(.)\\1{2,}").containsMatchIn(lower)) return true
        if (Regex("^[\\s🆘🚨⚠️😱😭🙏]+$").matches(trimmed)) return true
        if (wordCount <= 5) {
            val panicKw = if (currentLanguage == "en") PANIC_KEYWORDS_EN else PANIC_KEYWORDS_ID
            if (panicKw.any { lower.contains(it) }) return true
            if (PANIC_EMERGENCY_WORDS.any { lower.contains(it) }) return true
        }
        return false
    }

    private fun isClarificationMessage(query: String, previousQuery: String): Boolean {
        val lower = query.trim().lowercase()
        val wordCount = lower.split(Regex("\\s+")).size
        val clarificationStarters = listOf(
            "soalnya", "karena", "sebab", "maksudnya", "oh iya",
            "lupa bilang", "tambahin", "tambahan", "tapi", "emang",
            "itu", "yang tadi", "yang sebelumnya", "actually", "because",
            "the reason", "i mean", "by the way", "btw"
        )
        if (wordCount <= 8 && clarificationStarters.any { lower.startsWith(it) }) return true
        val disasterMentions = listOf("banjir", "gempa", "longsor", "flood", "earthquake", "landslide")
        val emergencyVerbs = listOf("terjebak", "tertimpa", "darurat", "bahaya", "tolong", "help", "trapped")
        val mentionsBencana = disasterMentions.any { lower.contains(it) }
        val mentionsEmergency = emergencyVerbs.any { lower.contains(it) }
        if (mentionsBencana && !mentionsEmergency && wordCount <= 10) return true
        return false
    }

    private fun tryAnswerMath(query: String): String? {
        val lower = query.trim()
        val mathRegex = Regex("(\\d+)\\s*([x×*])\\s*(\\d+)")
        val match = mathRegex.find(lower) ?: return null
        val a = match.groupValues[1].toLongOrNull() ?: return null
        val b = match.groupValues[3].toLongOrNull() ?: return null
        return "$a × $b = ${a * b}"
    }

    // ─── Initialize ───────────────────────────────────────────────────────────

    fun initialize() {
        val modelPath = getModelPath()
        Log.d(TAG, "Memuat model dari: $modelPath")
        val startTime = System.currentTimeMillis()
        val config = EngineConfig(
            modelPath     = modelPath,
            backend       = Backend.CPU(),
            visionBackend = Backend.CPU(),
            cacheDir      = context.cacheDir.absolutePath
        )
        engine = Engine(config)
        engine!!.initialize()
        Log.d(TAG, "Engine siap dalam ${System.currentTimeMillis() - startTime}ms")
        SafetyLayer.loadBpbdContacts(context)
    }

    private fun closeCurrentConversation() {
        try { currentConversation?.close() } catch (_: Exception) {}
        currentConversation = null
    }

    private fun makeConversationConfig(mode: ResponseMode = ResponseMode.EMERGENCY) = ConversationConfig(
        systemInstruction = Contents.of(sysPrompt),
        samplerConfig = when (mode) {
            ResponseMode.CASUAL        -> SamplerConfig(topK = 50, topP = 0.92, temperature = 0.70)
            ResponseMode.INFORMATIONAL -> SamplerConfig(topK = 45, topP = 0.90, temperature = 0.60)
            ResponseMode.PROCEDURAL    -> SamplerConfig(topK = 42, topP = 0.90, temperature = 0.55)
            ResponseMode.RECOVERY      -> SamplerConfig(topK = 42, topP = 0.90, temperature = 0.55)
            else                       -> SamplerConfig(topK = 40, topP = 0.88, temperature = 0.45)
        }
    )

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    private fun disasterLabel(type: DisasterType): String = when (type) {
        is DisasterType.GEMPA    -> if (currentLanguage == "en") "Earthquake"    else "Gempa Bumi"
        is DisasterType.BANJIR   -> if (currentLanguage == "en") "Flood"         else "Banjir"
        is DisasterType.LONGSOR  -> if (currentLanguage == "en") "Landslide"     else "Tanah Longsor"
        is DisasterType.COMPOUND -> type.types.map { disasterLabel(it) }.joinToString(" + ")
        else                     -> if (currentLanguage == "en") "General Disaster" else "Kebencanaan Umum"
    }

    // ─── Core Generate ────────────────────────────────────────────────────────

    fun generateResponse(
        userPrompt: String,
        context: ConversationContext = ConversationContext()
    ): Pair<String, ConversationContext> {

        val t0 = System.currentTimeMillis()
        TanggapLogger.logContext(context, "SEBELUM")

        return try {

            // ── 0. PANIC ──────────────────────────────────────────────────────
            if (isPanicMessage(userPrompt)) {
                val reply = if (context.isActive()) {
                    if (currentLanguage == "en")
                        "I'm still here. Tell me what's happening right now — are you in immediate danger?"
                    else
                        "Saya masih di sini. Ceritakan yang terjadi sekarang — apakah kamu dalam bahaya langsung?"
                } else {
                    if (currentLanguage == "en")
                        "I'm here. Take a breath — tell me: are you inside a building or outside right now?"
                    else
                        "Saya di sini. Tarik napas sebentar — ceritakan: kamu sekarang di dalam gedung atau sudah di luar?"
                }
                val updatedCtx = context.nextTurn(
                    newDisasterType = context.disasterType ?: DisasterType.UMUM,
                    userQuery       = userPrompt,
                    aiResponse      = reply
                )
                return Pair(reply, updatedCtx)
            }

            // ─── FLAG DETEKSI ─────────────────────────────────────────────────
            val isSafe = context.isActive() &&
                    !isTriageSummary(userPrompt) &&
                    ConversationContext.isSafeStatement(userPrompt, currentLanguage)

            val isCasual      = isCasualMessage(userPrompt)
            val isInformatio  = isInformationalQuery(userPrompt) && !context.isActive()
            val isEscalation  = context.isActive() && isEscalationMessage(userPrompt)
            val needsFormat   = needsGuidanceFormat(userPrompt)
            val isProcedural  = isProceduralQuery(userPrompt) && !context.isActive()
            val isUnrealistic = requiresRealityCheck(userPrompt)
            val isRecovery    = isRecoveryQuery(userPrompt) || context.phase == DisasterPhase.RECOVERY
            val isMultiVictim = ConversationContext.isMultiVictimQuery(userPrompt, currentLanguage) || context.multiVictim
            val isAnswerableShort = isAnswerableShortQuery(userPrompt) && !context.isActive()

            TanggapLogger.logRoutingDecision(
                query           = userPrompt,
                isCasual        = isCasual,
                isInformational = isInformatio,
                isEscalation    = isEscalation,
                needsFormat     = needsFormat,
                forceNoFormat   = isInformatio || isProcedural || isRecovery,
                contextActive   = context.isActive(),
                isSafeStatement = isSafe
            )

            // ── 1. SAFE STATEMENT ─────────────────────────────────────────────
            if (isSafe) {
                val reply = generateSafeConfirmationReply(userPrompt)
                val resolvedCtx = context.nextTurn(
                    newDisasterType = context.disasterType ?: DisasterType.UMUM,
                    userQuery       = userPrompt,
                    aiResponse      = reply,
                    resolved        = true,
                    newPhase        = DisasterPhase.RECOVERY
                )
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(reply, resolvedCtx)
            }

            // ── 2. REALITY CHECK ──────────────────────────────────────────────
            if (isUnrealistic) {
                val reply = generateRealityCheckReply(userPrompt)
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(reply, context)
            }

            // ── CLARIFICATION ─────────────────────────────────────────────────
            val isPrevAvailable = context.lastUserQuery.isNotBlank()
            val isClarification = isPrevAvailable &&
                    isClarificationMessage(userPrompt, context.lastUserQuery) &&
                    !context.isActive()

            if (isClarification) {
                val (response, updatedCtx) = generateFollowUpWithContext(
                    clarification    = userPrompt,
                    previousQuestion = context.lastUserQuery,
                    context          = context
                )
                return Pair(response, updatedCtx)
            }

            // ── ANSWERABLE SHORT ──────────────────────────────────────────────
            if (isAnswerableShort) {
                val mathAnswer = tryAnswerMath(userPrompt)
                return if (mathAnswer != null) {
                    val suffix = if (context.isActive()) {
                        if (currentLanguage == "en") "\n\nYour emergency is still active — what do you need help with?"
                        else "\n\nSituasi daruratmu masih aktif ya — ada yang perlu dibantu?"
                    } else {
                        if (currentLanguage == "en") "\n\nIs there an emergency I can help with?"
                        else "\n\nAda situasi darurat yang perlu saya bantu?"
                    }
                    Pair(mathAnswer + suffix, context)
                } else {
                    val ragResult = ragPipeline.query(userPrompt.take(300))
                    val response = doGenerate(userPrompt.take(300), context, ragResult, responseMode = ResponseMode.INFORMATIONAL)
                    Pair(response, context)
                }
            }

            // ── 3. CASUAL + darurat aktif → reminder pool ─────────────────────
            if (isCasual && context.isActive()) {
                val pool = if (currentLanguage == "en") CASUAL_REMINDERS_EN else CASUAL_REMINDERS_ID
                val reminder = pool[(userPrompt.hashCode() and Int.MAX_VALUE) % pool.size]
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(reminder, context)
            }

            // ── 4. CASUAL + tidak ada darurat aktif ───────────────────────────
            if (isCasual) {
                val ragResult = ragPipeline.query(userPrompt.take(300))
                val response  = doGenerate(
                    userPrompt   = userPrompt.take(300),
                    convCtx      = context,
                    ragResult    = ragResult,
                    responseMode = ResponseMode.CASUAL
                )
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(response, context)
            }

            // ── 5. MULTI-VICTIM ───────────────────────────────────────────────
            if (isMultiVictim) {
                val ragResult = ragPipeline.query(userPrompt.take(300))
                val response  = doGenerate(
                    userPrompt   = userPrompt.take(300),
                    convCtx      = context,
                    ragResult    = ragResult,
                    responseMode = ResponseMode.MULTI_VICTIM
                )
                val updatedCtx = context.nextTurn(
                    newDisasterType = ragResult.disasterType,
                    userQuery       = userPrompt,
                    aiResponse      = response,
                    isMultiVictim   = true,
                    newPhase        = DisasterPhase.ACTIVE
                )
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(response, updatedCtx)
            }

            // ── 6. RECOVERY ───────────────────────────────────────────────────
            if (isRecovery && !needsFormat) {
                val ragResult = ragPipeline.query(userPrompt.take(300))
                val response  = doGenerate(
                    userPrompt   = userPrompt.take(300),
                    convCtx      = context,
                    ragResult    = ragResult,
                    responseMode = ResponseMode.RECOVERY
                )
                val updatedCtx = context.nextTurn(
                    newDisasterType = ragResult.disasterType,
                    userQuery       = userPrompt,
                    aiResponse      = response,
                    newPhase        = DisasterPhase.RECOVERY
                )
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(response, updatedCtx)
            }

            // ── 7. PROCEDURAL ─────────────────────────────────────────────────
            if (isProcedural && !isRecovery) {
                val ragResult = ragPipeline.query(userPrompt.take(300))
                val response  = doGenerate(
                    userPrompt   = userPrompt.take(300),
                    convCtx      = context,
                    ragResult    = ragResult,
                    responseMode = ResponseMode.PROCEDURAL
                )
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(response, context)
            }

            // ── 8. INFORMATIONAL ──────────────────────────────────────────────
            if (isInformatio) {
                val ragResult = ragPipeline.query(userPrompt.take(300))
                val response  = doGenerate(
                    userPrompt   = userPrompt.take(300),
                    convCtx      = context,
                    ragResult    = ragResult,
                    responseMode = ResponseMode.INFORMATIONAL
                )
                TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
                return Pair(response, context)
            }

            // ── 9. EMERGENCY (default) ────────────────────────────────────────
            val ragResult = ragPipeline.query(userPrompt.take(300))

            val effectiveDisasterType = if (context.isActive()) {
                val ragType = ragResult.disasterType
                val ctxType = context.disasterType
                when {
                    ragType is DisasterType.UMUM                           -> ctxType ?: ragType
                    isEscalation && ctxType != null && ragType != ctxType  -> ctxType
                    else                                                   -> ragType
                }
            } else {
                ragResult.disasterType
            }

            val effectiveRagResult = if (effectiveDisasterType != ragResult.disasterType)
                ragResult.copy(disasterType = effectiveDisasterType)
            else
                ragResult

            val response = doGenerate(
                userPrompt   = userPrompt.take(300),
                convCtx      = context,
                ragResult    = effectiveRagResult,
                isEscalation = isEscalation,
                responseMode = ResponseMode.EMERGENCY
            )

            val updatedCtx = context.nextTurn(
                newDisasterType = effectiveDisasterType ?: DisasterType.UMUM,
                userQuery       = userPrompt,
                aiResponse      = response,
                isMultiVictim   = isMultiVictim,
                newPhase        = DisasterPhase.ACTIVE
            )

            TanggapLogger.logTotalPipeline(System.currentTimeMillis() - t0)
            Pair(response, updatedCtx)

        } catch (e: Exception) {
            TanggapLogger.logError("GemmaInferenceEngine", "generateResponse", e)
            val errMsg = if (currentLanguage == "en")
                "Sorry, failed to process. Please try again."
            else
                "Maaf, gagal memproses. Silakan coba lagi."
            Pair(errMsg, context.reset())
        }
    }

    // ─── doGenerate ───────────────────────────────────────────────────────────

    private fun doGenerate(
        userPrompt: String,
        convCtx: ConversationContext,
        ragResult: id.tanggap.app.data.RAGResult,
        isEscalation: Boolean = false,
        responseMode: ResponseMode = ResponseMode.EMERGENCY
    ): String {
        val currentEngine = engine ?: throw Exception("Engine belum diinisialisasi.")
        closeCurrentConversation()

        val resolvedType = convCtx.disasterType ?: ragResult.disasterType
        val label        = disasterLabel(resolvedType)

        val (chunkCount, chunkLen) = when (responseMode) {
            ResponseMode.RECOVERY      -> 2 to 400
            ResponseMode.PROCEDURAL    -> 2 to 350
            ResponseMode.INFORMATIONAL -> 1 to 250
            ResponseMode.CASUAL        -> 0 to 0
            else                       -> 2 to 300
        }

        val contextBlock = if (ragResult.hasResults && chunkCount > 0) {
            val chunks = ragResult.chunks.take(chunkCount)
            if (responseMode == ResponseMode.RECOVERY) {
                val recoveryFirst = chunks.sortedByDescending { chunk ->
                    val t = chunk.topic.lowercase()
                    when {
                        t.contains("pasca") || t.contains("pemulihan") ||
                                t.contains("recovery") || t.contains("rehabilitasi") ||
                                chunk.disasterType == "pasca_bencana" -> 1
                        else -> 0
                    }
                }
                recoveryFirst.joinToString("\n---\n") { it.text.take(chunkLen) }
            } else {
                chunks.joinToString("\n---\n") { it.text.take(chunkLen) }
            }
        } else ""

        val visionBlock = convCtx.visionSummary?.let {
            if (currentLanguage == "en") "\n\n=== VISUAL ANALYSIS ===\n$it"
            else "\n\n=== ANALISIS VISUAL ===\n$it"
        } ?: ""

        val historyBlock     = convCtx.toHistoryBlock(currentLanguage)
        val convContextBlock = convCtx.toContextBlock(currentLanguage)

        val userMessage = buildPrompt(
            userPrompt       = userPrompt,
            label            = label,
            contextBlock     = contextBlock + visionBlock,
            historyBlock     = historyBlock,
            convContextBlock = convContextBlock,
            isEscalation     = isEscalation,
            responseMode     = responseMode,
            isMultiVictim    = convCtx.multiVictim
        )

        TanggapLogger.logPrompt(userMessage)

        return try {
            currentConversation = currentEngine.createConversation(makeConversationConfig(responseMode))
            val response = currentConversation!!.sendMessage(Message.user(userMessage))
            val raw = response?.toString()?.trim()
                ?: if (currentLanguage == "en") "No response" else "Tidak ada respons"

            TanggapLogger.logRawResponse(raw)

            SafetyLayer.appendSafetyFooter(raw)
        } finally {
            closeCurrentConversation()
        }
    }

    private fun buildPrompt(
        userPrompt: String,
        label: String,
        contextBlock: String,
        historyBlock: String,
        convContextBlock: String,
        isEscalation: Boolean = false,
        responseMode: ResponseMode = ResponseMode.EMERGENCY,
        isMultiVictim: Boolean = false
    ): String = buildString {

        val langReminder = if (currentLanguage == "en")
            "[REMINDER: Respond in ENGLISH only.]\n\n"
        else
            "[PENGINGAT: Jawab dalam BAHASA INDONESIA saja.]\n\n"

        if (currentLanguage == "en") {

            // ── CASUAL (EN) ───────────────────────────────────────────────────
            if (responseMode == ResponseMode.CASUAL) {
                append(langReminder)
                append(userPrompt)
                append("\n\n")
                append("Reply like a friendly, casual buddy who's ready to help. 1–2 sentences max. Don't give disaster guidance unless the user mentions an emergency.")
                return@buildString
            }

            // ── INFORMATIONAL (EN) — CoT: jawab langsung, jelas ──────────────
            if (responseMode == ResponseMode.INFORMATIONAL) {
                append(langReminder)
                append("=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("No specific data available.\n")
                append("\n\n=== QUESTION ===\n")
                append(userPrompt)
                append("\n\n=== YOUR RESPONSE ===\n")
                append("Think step by step:\n")
                append("1. What is the core of this question?\n")
                append("2. What relevant facts do I know from the knowledge base?\n")
                append("3. What is the most useful answer in 2–4 sentences?\n\n")
                append("Then answer directly in plain text. No headers, no bullets unless a list was explicitly requested.")
                return@buildString
            }

            // ── PROCEDURAL (EN) — CoT: urut langkah nyata ────────────────────
            if (responseMode == ResponseMode.PROCEDURAL) {
                append(langReminder)
                append("=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("No specific data available.\n")
                if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
                append("\n\n=== PROCEDURE REQUEST ===\n")
                append(userPrompt)
                append("\n\n=== YOUR RESPONSE ===\n")
                append("Think step by step:\n")
                append("1. What is the person trying to accomplish?\n")
                append("2. What are the concrete steps, in order?\n")
                append("3. Is there a critical mistake people often make?\n\n")
                append("Then give clear numbered steps. Plain language, like explaining to a neighbor. Bold step names only if it helps navigation.")
                return@buildString
            }

            // ── RECOVERY (EN) — CoT: empati + langkah mandiri ────────────────
            if (responseMode == ResponseMode.RECOVERY) {
                append(langReminder)
                append("=== DISASTER CONTEXT ===\n")
                val isDisasterKnown = label != "General Disaster" && label != "Kebencanaan Umum"
                if (isDisasterKnown) {
                    append("Disaster type: $label | Phase: POST-DISASTER RECOVERY\n")
                } else {
                    append("Disaster type: UNKNOWN | Phase: POST-DISASTER RECOVERY\n")
                    append("NOTE: Ask what type of disaster occurred before giving specific advice.\n")
                }
                if (convContextBlock.isNotBlank()) append("Context: $convContextBlock\n")
                if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
                append("\n=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("No specific data. Apply general post-disaster principles.\n")
                append("\n\n=== USER'S RECOVERY QUESTION ===\n")
                append(userPrompt)
                append("\n\n=== YOUR RESPONSE ===\n")
                append("Think step by step:\n")
                append("1. What is the person's exact situation right now?\n")
                append("2. What is the most important thing they can do without special tools?\n")
                append("3. What should they avoid that could cause harm or delay recovery?\n\n")
                append("Then answer with empathy. Use 'you can...' not 'you must...'. The acute emergency is over — no urgent framing. Keep it under 200 words.")
                return@buildString
            }

            // ── MULTI-VICTIM (EN) — CoT: triase START ────────────────────────
            if (responseMode == ResponseMode.MULTI_VICTIM) {
                append(langReminder)
                append("=== EMERGENCY CONTEXT ===\n")
                append("Disaster: $label | MULTIPLE VICTIMS\n")
                if (convContextBlock.isNotBlank()) append("Context: $convContextBlock\n")
                if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
                append("\n=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("No specific data. Apply START triage principles.\n")
                append("\n\n=== CURRENT SITUATION ===\n")
                append(userPrompt)
                append("\n\n=== YOUR RESPONSE ===\n")
                append("Think step by step using START triage:\n")
                append("1. RED (immediate): Who needs help RIGHT NOW to survive?\n")
                append("2. YELLOW (delayed): Who is injured but stable?\n")
                append("3. GREEN (minor): Who can walk and help others?\n")
                append("4. Who among the responders can help, and what should each do simultaneously?\n\n")
                append("Then give specific, prioritized instructions for managing multiple victims with limited help.")
                return@buildString
            }

            // ── EMERGENCY (EN) — CoT: ancaman jiwa, tindakan konkret ─────────
            append(langReminder)
            append("=== EMERGENCY CONTEXT ===\n")
            append("Disaster: $label\n")
            if (convContextBlock.isNotBlank()) {
                append("Session: $convContextBlock\n")
            }
            if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
            if (isEscalation) {
                append("\n⚠️ ESCALATION: User is reaffirming $label situation. Give MORE SPECIFIC and URGENT actions.\n")
            }
            append("\n=== KNOWLEDGE BASE ===\n")
            if (contextBlock.isNotBlank()) append(contextBlock)
            else append("No specific data. Apply general emergency principles.\n")
            append("\n\n=== CURRENT MESSAGE ===\n")
            append(userPrompt)
            append("\n\n=== YOUR RESPONSE ===\n")
            append("Think step by step:\n")
            append("1. What is the most life-threatening condition right now?\n")
            append("2. What first aid action must happen in the next 60 seconds?\n")
            append("3. What should NOT be done that could cause more harm?\n\n")
            append("Then provide immediate, specific guidance. ")
            append("If life is threatened RIGHT NOW, use this format:\n\n")
            append("**SITUATION:** [1 specific sentence]\n")
            append("**DO THIS NOW:** [numbered steps, max 3]\n")
            append("**DON'T:** [1 specific thing to avoid]\n\n")
            append("If NOT immediately life-threatening: write 2–3 plain sentences only. No headers.")

        } else {
            // ════════════════════════════════════════════════════════════════
            // BAHASA INDONESIA
            // ════════════════════════════════════════════════════════════════

            // ── CASUAL (ID) ───────────────────────────────────────────────────
            if (responseMode == ResponseMode.CASUAL) {
                append(langReminder)
                append(userPrompt)
                append("\n\n")
                append("Balas seperti teman yang santai tapi siap bantu. Maksimal 1–2 kalimat. Jangan kasih panduan bencana kecuali user sebut situasi darurat.")
                return@buildString
            }

            // ── INFORMATIONAL (ID) — CoT: jawab langsung, jelas ──────────────
            if (responseMode == ResponseMode.INFORMATIONAL) {
                append(langReminder)
                append("=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("Tidak ada data spesifik tersedia.\n")
                append("\n\n=== PERTANYAAN ===\n")
                append(userPrompt)
                append("\n\n=== RESPONMU ===\n")
                append("Pikirkan langkah demi langkah:\n")
                append("1. Apa inti pertanyaan ini?\n")
                append("2. Fakta relevan apa yang saya tahu dari knowledge base?\n")
                append("3. Jawaban paling berguna dalam 2–4 kalimat itu apa?\n\n")
                append("Lalu jawab langsung dalam teks biasa. Tanpa header, tanpa bullet kecuali daftar memang diminta.")
                return@buildString
            }

            // ── PROCEDURAL (ID) — CoT: urut langkah nyata ────────────────────
            if (responseMode == ResponseMode.PROCEDURAL) {
                append(langReminder)
                append("=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("Tidak ada data spesifik. Terapkan prinsip darurat umum.\n")
                if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
                append("\n\n=== PERMINTAAN PROSEDUR ===\n")
                append(userPrompt)
                append("\n\n=== RESPONMU ===\n")
                append("Pikirkan langkah demi langkah:\n")
                append("1. Apa yang ingin dicapai orang ini?\n")
                append("2. Apa langkah-langkah konkretnya, berurutan?\n")
                append("3. Adakah kesalahan umum yang sering terjadi?\n\n")
                append("Lalu berikan langkah bernomor yang jelas. Bahasa sehari-hari, seperti menjelaskan ke tetangga. Bold nama langkah hanya jika membantu navigasi.")
                return@buildString
            }

            // ── RECOVERY (ID) — CoT: empati + langkah mandiri ────────────────
            if (responseMode == ResponseMode.RECOVERY) {
                append(langReminder)
                append("=== KONTEKS BENCANA ===\n")
                val isDisasterKnown = label != "General Disaster" && label != "Kebencanaan Umum"
                if (isDisasterKnown) {
                    append("Jenis bencana: $label | Fase: PEMULIHAN PASCA BENCANA\n")
                } else {
                    append("Jenis bencana: TIDAK DIKETAHUI | Fase: PEMULIHAN PASCA BENCANA\n")
                    append("CATATAN: Tanyakan jenis bencana apa yang terjadi sebelum memberi saran spesifik.\n")
                }
                if (convContextBlock.isNotBlank()) append("Konteks: $convContextBlock\n")
                if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
                append("\n=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("Tidak ada data spesifik. Terapkan prinsip pasca bencana umum.\n")
                append("\n\n=== PERTANYAAN PEMULIHAN USER ===\n")
                append(userPrompt)
                append("\n\n=== RESPONMU ===\n")
                append("Pikirkan langkah demi langkah:\n")
                append("1. Situasi persis user sekarang itu apa?\n")
                append("2. Hal terpenting apa yang bisa mereka lakukan tanpa alat khusus?\n")
                append("3. Apa yang harus dihindari agar tidak memperparah atau memperlambat pemulihan?\n\n")
                append("Lalu jawab dengan empati. Gunakan 'kamu bisa...' bukan 'kamu harus...'. Fase darurat sudah lewat — jangan pakai framing mendesak. Maksimal 200 kata.")
                return@buildString
            }

            // ── MULTI-VICTIM (ID) — CoT: triase START ─────────────────────────
            if (responseMode == ResponseMode.MULTI_VICTIM) {
                append(langReminder)
                append("=== KONTEKS DARURAT ===\n")
                append("Bencana: $label | BANYAK KORBAN\n")
                if (convContextBlock.isNotBlank()) append("Konteks: $convContextBlock\n")
                if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
                append("\n=== KNOWLEDGE BASE ===\n")
                if (contextBlock.isNotBlank()) append(contextBlock)
                else append("Tidak ada data spesifik. Terapkan triase START.\n")
                append("\n\n=== SITUASI SEKARANG ===\n")
                append(userPrompt)
                append("\n\n=== RESPONMU ===\n")
                append("Pikirkan langkah demi langkah menggunakan triase START awam:\n")
                append("1. MERAH (segera): Siapa yang butuh bantuan SEKARANG agar bisa bertahan hidup?\n")
                append("2. KUNING (tunda): Siapa yang terluka tapi masih stabil?\n")
                append("3. HIJAU (ringan): Siapa yang bisa berjalan dan membantu orang lain?\n")
                append("4. Siapa di antara penolong yang ada, dan apa yang harus masing-masing lakukan secara bersamaan?\n\n")
                append("Lalu berikan instruksi spesifik dan terurut prioritas untuk menangani beberapa korban dengan bantuan terbatas.")
                return@buildString
            }

            // ── EMERGENCY (ID) — CoT: ancaman jiwa, tindakan konkret ──────────
            append(langReminder)
            append("=== KONTEKS DARURAT ===\n")
            append("Bencana: $label\n")
            if (convContextBlock.isNotBlank()) {
                append("Sesi: $convContextBlock\n")
            }
            if (historyBlock.isNotBlank()) { append("\n"); append(historyBlock); append("\n") }
            if (isEscalation) {
                append("\n⚠️ ESKALASI: User menegaskan kembali situasi $label. Berikan tindakan LEBIH SPESIFIK dan MENDESAK.\n")
            }
            append("\n=== KNOWLEDGE BASE ===\n")
            if (contextBlock.isNotBlank()) append(contextBlock)
            else append("Tidak ada data spesifik. Terapkan prinsip darurat umum.\n")
            append("\n\n=== PESAN SEKARANG ===\n")
            append(userPrompt)
            append("\n\n=== RESPONMU ===\n")
            append("Pikirkan langkah demi langkah:\n")
            append("1. Kondisi apa yang paling mengancam jiwa sekarang?\n")
            append("2. Tindakan P3K apa yang harus dilakukan dalam 60 detik ke depan?\n")
            append("3. Apa yang TIDAK boleh dilakukan agar tidak memperburuk kondisi?\n\n")
            append("Lalu berikan panduan segera yang spesifik. ")
            append("Jika nyawa terancam SEKARANG, gunakan format ini:\n\n")
            append("**SITUASI:** [1 kalimat spesifik]\n")
            append("**LAKUKAN SEKARANG:** [langkah bernomor, maks 3]\n")
            append("**JANGAN:** [1 hal spesifik yang memperburuk situasi]\n\n")
            append("Jika TIDAK mengancam jiwa secara langsung: tulis 2–3 kalimat biasa saja. Tanpa header.")
        }
    }

    // ─── Follow-up & Reply Generators ────────────────────────────────────────

    fun generateFollowUpWithContext(
        clarification: String,
        previousQuestion: String,
        context: ConversationContext
    ): Pair<String, ConversationContext> {
        val combinedPrompt = if (currentLanguage == "en")
            "Previous question: $previousQuestion\nAdditional context: $clarification\nAnswer the original question taking into account the additional context."
        else
            "Pertanyaan sebelumnya: $previousQuestion\nKonteks tambahan: $clarification\nJawab pertanyaan awal dengan mempertimbangkan konteks tambahan ini."

        val ragResult = ragPipeline.query("$previousQuestion $clarification".take(300))

        val isFollowUpRecovery = isRecoveryQuery(previousQuestion) || isRecoveryQuery(clarification)
        val mode = when {
            isFollowUpRecovery -> ResponseMode.RECOVERY
            isProceduralQuery(previousQuestion) || isProceduralQuery(combinedPrompt) -> ResponseMode.PROCEDURAL
            else -> ResponseMode.INFORMATIONAL
        }

        val response = doGenerate(
            userPrompt   = combinedPrompt,
            convCtx      = context,
            ragResult    = ragResult,
            responseMode = mode
        )
        return Pair(response, context)
    }

    private fun generateSafeConfirmationReply(userPrompt: String): String {
        val lower = userPrompt.trim().lowercase()
        return if (currentLanguage == "en") {
            when {
                lower.contains("rescue") || lower.contains("found") || lower.contains("help arrived") ->
                    "Really glad help reached you. Get a medical check even if you feel fine — some injuries don't show up immediately. Stay safe."
                lower.contains("out") || lower.contains("escaped") || lower.contains("got out") ->
                    "Glad you made it out safely. Move to a stable open area away from buildings, and get checked by a medic if you feel any pain."
                else ->
                    "Really glad to hear you're safe. Rest, drink water, and get a medical check even if you feel fine — some injuries don't show up immediately."
            }
        } else {
            when {
                lower.contains("ditolong") || lower.contains("diselamatkan") || lower.contains("bantuan") ->
                    "Alhamdulillah, lega mendengar kamu sudah ditolong. Tetap minta pemeriksaan medis meskipun merasa baik-baik saja — beberapa cedera tidak langsung terasa."
                lower.contains("keluar") || lower.contains("berhasil") ->
                    "Senang kamu sudah berhasil keluar. Pindah ke area terbuka yang stabil, dan periksakan diri ke tenaga medis kalau ada rasa sakit."
                else ->
                    "Alhamdulillah, lega mendengar kamu sudah lebih aman. Istirahat, minum air yang cukup, dan periksa ke tenaga kesehatan meskipun merasa baik-baik saja — beberapa cedera tidak langsung terasa."
            }
        }
    }

    private fun generateRealityCheckReply(userPrompt: String): String {
        return if (currentLanguage == "en") {
            "I need to confirm: is this something you can see or feel right now? " +
                    "If there's a real physical danger — flood, earthquake, fire, or anything visible — " +
                    "describe it and I'll help right away."
        } else {
            "Saya perlu konfirmasi dulu: apakah ini sesuatu yang kamu lihat langsung sekarang? " +
                    "Kalau ada bahaya nyata — banjir, gempa, kebakaran, atau apapun yang bisa kamu lihat " +
                    "atau rasakan — ceritakan dan saya bantu segera."
        }
    }

    // ─── Vision ──────────────────────────────────────────────────────────────

    fun analyzeImage(
        bitmap: Bitmap,
        disasterType: DisasterType,
        userHint: String = "",
        context: ConversationContext = ConversationContext()
    ): Pair<String, ConversationContext> {

        val currentEngine = engine ?: return Pair(
            if (currentLanguage == "en") "Engine not initialized." else "Engine belum diinisialisasi.",
            context.reset()
        )

        TanggapLogger.logVisionInput(bitmap.width, bitmap.height, userHint)

        val t0          = System.currentTimeMillis()
        val mlKitResult = mlKitAnalyzer.analyzeImage(bitmap)
        TanggapLogger.logTiming("MLKit analyze", System.currentTimeMillis() - t0)
        TanggapLogger.logMlKitResult(mlKitResult.labels)

        val visionContext    = mlKitAnalyzer.detectContext(mlKitResult.labels, userHint)
        val detectedDisaster = mlKitAnalyzer.detectDisasterType(mlKitResult.labels)

        if (visionContext == VisionContext.UNCLEAR) {
            val notRelevantReply = if (currentLanguage == "en")
                "This image doesn't appear to show a disaster or injury situation. If you're in an emergency, please describe your situation in text and I'll help right away."
            else
                "Gambar ini tidak menunjukkan situasi bencana atau cedera. Jika kamu sedang dalam keadaan darurat, ceritakan situasimu dalam teks dan saya akan langsung membantu."
            return Pair(notRelevantReply, context)
        }

        val resolvedDisasterType = if (disasterType is DisasterType.UMUM && detectedDisaster != DetectedDisaster.UNKNOWN) {
            when (detectedDisaster) {
                DetectedDisaster.FLOOD      -> DisasterType.BANJIR
                DetectedDisaster.EARTHQUAKE -> DisasterType.GEMPA
                DetectedDisaster.LANDSLIDE  -> DisasterType.LONGSOR
                DetectedDisaster.UNKNOWN    -> DisasterType.UMUM
            }
        } else disasterType

        val label = disasterLabel(resolvedDisasterType)

        val mlKitDesc = if (mlKitResult.labels.isNotEmpty())
            mlKitResult.labels.take(4).joinToString(", ")
        else ""

        val visionSummary = buildString {
            if (mlKitDesc.isNotBlank()) append(mlKitDesc)
            if (userHint.isNotBlank()) append(if (mlKitDesc.isNotBlank()) " — keterangan: $userHint" else userHint)
        }.take(150)

        val t1 = System.currentTimeMillis()
        val rawResponse = try {
            analyzeImageWithGemmaVision(currentEngine, bitmap, mlKitResult.labels, visionContext, label, userHint)
        } catch (e: Exception) {
            TanggapLogger.logVisionFallback(e.message ?: "unknown error")
            Log.w(TAG, "Native vision gagal, fallback ke ML Kit: ${e.message}")
            analyzeImageWithMlKitFallback(currentEngine, mlKitResult.toPromptDescription(), visionContext, label, userHint)
        }
        TanggapLogger.logTiming("Vision inference total", System.currentTimeMillis() - t1)

        val updatedContext = context.nextTurn(
            newDisasterType  = resolvedDisasterType,
            userQuery        = userHint.ifBlank { if (currentLanguage == "en") "photo sent" else "foto dikirim" },
            aiResponse       = rawResponse,
            withImage        = true,
            newVisionSummary = visionSummary
        )
        return Pair(rawResponse, updatedContext)
    }

    private fun analyzeImageWithGemmaVision(
        currentEngine: Engine,
        bitmap: Bitmap,
        mlKitLabels: List<String>,
        visionContext: VisionContext,
        disasterLabel: String,
        userHint: String
    ): String {
        val isCriticallyTrapped = userHint.lowercase().let { lower ->
            TRAPPED_KEYWORDS.any { lower.contains(it) }
        }
        val criticalAlert = if (isCriticallyTrapped) {
            if (currentLanguage == "en")
                "\nCRITICAL: User reports being physically trapped. Prioritize immediate escape guidance above all else."
            else
                "\nKRITIS: User melaporkan terjebak secara fisik. Prioritaskan panduan keluar/evakuasi segera di atas segalanya."
        } else ""

        val hintLine = if (userHint.isNotBlank())
            "$criticalAlert\n${if (currentLanguage == "en") "User note" else "Keterangan"}: $userHint"
        else criticalAlert

        val mlKitLine = if (mlKitLabels.isNotEmpty())
            "\n${if (currentLanguage == "en") "Detected (ML Kit)" else "Terdeteksi (ML Kit)"}: ${mlKitLabels.joinToString(", ")}"
        else ""

        // ── Vision prompt juga menggunakan CoT sederhana ──────────────────────
        val textPrompt = buildVisionPrompt(visionContext, disasterLabel, hintLine, mlKitLine)
        val imageBytes = bitmapToByteArray(bitmap)

        closeCurrentConversation()
        val conversation = currentEngine.createConversation(makeConversationConfig(ResponseMode.EMERGENCY))
        return try {
            val response = conversation.sendMessage(
                Message.user(Contents.of(Content.ImageBytes(imageBytes), Content.Text(textPrompt)))
            )
            val result = response?.toString()?.trim()
            if (result.isNullOrBlank()) throw Exception("Respons kosong")
            SafetyLayer.appendSafetyFooter(result)
        } finally {
            try { conversation.close() } catch (_: Exception) {}
        }
    }

    private fun analyzeImageWithMlKitFallback(
        currentEngine: Engine,
        detectedObjects: String,
        visionContext: VisionContext,
        disasterLabel: String,
        userHint: String
    ): String {
        val hintLine = if (userHint.isNotBlank())
            "${if (currentLanguage == "en") "User note" else "Keterangan"}: $userHint\n"
        else ""
        val textPrompt = buildFallbackPrompt(visionContext, disasterLabel, detectedObjects, hintLine)

        closeCurrentConversation()
        val conversation = currentEngine.createConversation(makeConversationConfig(ResponseMode.EMERGENCY))
        return try {
            val response = conversation.sendMessage(Message.user(textPrompt))
            val raw = response?.toString()?.trim()
                ?: if (currentLanguage == "en") "No response from model." else "Tidak ada respons dari model."
            SafetyLayer.appendSafetyFooter(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback error: ${e.message}")
            if (currentLanguage == "en") "Failed to process image." else "Gagal memproses gambar."
        } finally {
            try { conversation.close() } catch (_: Exception) {}
        }
    }

    // ─── Vision Prompt Builders — CoT untuk analisis visual ─────────────────
    private fun buildVisionPrompt(
        visionContext: VisionContext,
        disasterLabel: String,
        hintLine: String,
        mlKitLine: String
    ): String = if (currentLanguage == "en") {
        when (visionContext) {
            VisionContext.INJURY -> """Context: Victim of $disasterLabel.$hintLine$mlKitLine

Think step by step:
1. What injury is most visible or likely?
2. Is this life-threatening right now?
3. What first aid can be done WITHOUT medical equipment?

Then answer ONLY in this format:

**DETECTED CONDITION:** [likely injury, 1 sentence]
**SEVERITY:** [MINOR / MODERATE / SEVERE / CRITICAL]
**FIRST AID:**
1. [step 1]
2. [step 2]
3. [step 3 — continue until first aid is complete]
**DO NOT:** [1-2 things to avoid]
**NEEDS DOCTOR:** [YES/NO] — [brief reason]
"""
            VisionContext.ENVIRONMENT -> """Context: Post-disaster $disasterLabel.$hintLine$mlKitLine

Think step by step:
1. What is the most dangerous element visible?
2. Is immediate evacuation required?
3. What can be done to stay safe right now?

Then answer ONLY in this format:

**DETECTED CONDITION:** [situation description, 1 sentence]
**RISK LEVEL:** [LOW / MEDIUM / HIGH / CRITICAL]
**SPECIFIC HAZARDS:**
- [hazard 1]
- [hazard 2]
**IMMEDIATE ACTIONS:**
1. [step 1]
2. [step 2]
**EVACUATION:** [URGENT / PREPARE / NOT NEEDED] — [reason]
"""
            VisionContext.UNCLEAR -> ""
        }
    } else {
        when (visionContext) {
            VisionContext.INJURY -> """Konteks: Korban bencana $disasterLabel.$hintLine$mlKitLine

Pikirkan langkah demi langkah:
1. Cedera apa yang paling terlihat atau paling mungkin terjadi?
2. Apakah ini mengancam jiwa sekarang?
3. Pertolongan pertama apa yang bisa dilakukan TANPA peralatan medis?

Lalu jawab HANYA dalam format:

**KONDISI TERDETEKSI:** [kemungkinan cedera, 1 kalimat]
**TINGKAT KEPARAHAN:** [RINGAN / SEDANG / BERAT / KRITIS]
**PERTOLONGAN PERTAMA:**
1. [langkah 1]
2. [langkah 2]
3. [langkah 3 — lanjutkan sampai selesai]
**JANGAN LAKUKAN:** [1-2 hal dihindari]
**BUTUH DOKTER:** [YA/TIDAK] — [alasan singkat]
"""
            VisionContext.ENVIRONMENT -> """Konteks: Pasca bencana $disasterLabel.$hintLine$mlKitLine

Pikirkan langkah demi langkah:
1. Elemen paling berbahaya yang terlihat?
2. Apakah evakuasi segera diperlukan?
3. Apa yang bisa dilakukan untuk tetap aman sekarang?

Lalu jawab HANYA dalam format:

**KONDISI TERDETEKSI:** [deskripsi situasi, 1 kalimat]
**LEVEL RISIKO:** [RENDAH / SEDANG / TINGGI / KRITIS]
**BAHAYA SPESIFIK:**
- [bahaya 1]
- [bahaya 2]
**TINDAKAN SEGERA:**
1. [langkah 1]
2. [langkah 2]
**EVAKUASI:** [PERLU SEGERA / SIAP-SIAP / TIDAK PERLU] — [alasan]
"""
            VisionContext.UNCLEAR -> ""
        }
    }

    private fun buildFallbackPrompt(
        visionContext: VisionContext,
        disasterLabel: String,
        detectedObjects: String,
        hintLine: String
    ): String = if (currentLanguage == "en") {
        when (visionContext) {
            VisionContext.INJURY ->
                "Disaster: $disasterLabel\nDetected: $detectedObjects\n${hintLine}\nAnalyze likely injury:\n**DETECTED CONDITION:** [1 sentence]\n**SEVERITY:** [MINOR/MODERATE/SEVERE/CRITICAL]\n**FIRST AID:** [All steps needed]\n**DO NOT:** [avoid]\n**NEEDS DOCTOR:** [YES/NO]"
            VisionContext.ENVIRONMENT ->
                "Disaster: $disasterLabel\nDetected: $detectedObjects\n${hintLine}\nAnalyze environment:\n**DETECTED CONDITION:** [1 sentence]\n**RISK LEVEL:** [LOW/MEDIUM/HIGH/CRITICAL]\n**HAZARDS:** -[h1] -[h2]\n**ACTIONS:** [All steps needed]\n**EVACUATION:** [URGENT/PREPARE/NOT NEEDED]"
            VisionContext.UNCLEAR ->
                "This image doesn't appear to show a disaster situation. If there's an emergency, please describe it in text."
        }
    } else {
        when (visionContext) {
            VisionContext.INJURY ->
                "Bencana: $disasterLabel\nTerdeteksi: $detectedObjects\n${hintLine}\nAnalisis cedera:\n**KONDISI TERDETEKSI:** [1 kalimat]\n**KEPARAHAN:** [RINGAN/SEDANG/BERAT/KRITIS]\n**P3K:** [Semua langkah]\n**JANGAN:** [hindari]\n**BUTUH DOKTER:** [YA/TIDAK]"
            VisionContext.ENVIRONMENT ->
                "Bencana: $disasterLabel\nTerdeteksi: $detectedObjects\n${hintLine}\nAnalisis lingkungan:\n**KONDISI TERDETEKSI:** [1 kalimat]\n**LEVEL RISIKO:** [RENDAH/SEDANG/TINGGI/KRITIS]\n**BAHAYA:** -[b1] -[b2]\n**TINDAKAN:** [Semua langkah]\n**EVAKUASI:** [PERLU SEGERA/SIAP-SIAP/TIDAK PERLU]"
            VisionContext.UNCLEAR ->
                "Gambar ini tidak menunjukkan situasi bencana. Jika ada kedaruratan, ceritakan dalam teks."
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun isModelReady(): Boolean = ModelDownloadManager.isModelReady(context)
    fun getModelPath(): String  = ModelDownloadManager.getModelFile(context).absolutePath

    fun close() {
        closeCurrentConversation()
        engine?.close()
        engine = null
    }
}