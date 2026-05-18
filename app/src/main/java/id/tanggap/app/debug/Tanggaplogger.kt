package id.tanggap.app.debug

import android.util.Log
import id.tanggap.app.data.DisasterType
import id.tanggap.app.data.RAGResult
import id.tanggap.app.inference.ConversationContext

/**
 * TanggapLogger — centralized debug logging untuk semua tahap pipeline.
 *
 * Cara pakai di Logcat:
 *   Filter tag: "TANGGAP_" untuk semua log pipeline
 *   Atau filter spesifik per modul, contoh: "TANGGAP_RAG", "TANGGAP_DETECT", dst.
 *
 * Format tiap log:
 *   D/TANGGAP_XXX: [STEP N] <deskripsi> | key=value | key=value
 */
object TanggapLogger {

    // ─── Toggle master — set false untuk production ───────────────────────────
    var ENABLED = true

    // ─── Tag per modul ────────────────────────────────────────────────────────
    private const val TAG_DETECT  = "TANGGAP_DETECT"   // DisasterTypeDetector
    private const val TAG_RAG     = "TANGGAP_RAG"      // RAGPipeline + BM25
    private const val TAG_ENGINE  = "TANGGAP_ENGINE"   // GemmaInferenceEngine
    private const val TAG_CONTEXT = "TANGGAP_CTX"      // ConversationContext
    private const val TAG_SAFETY  = "TANGGAP_SAFETY"   // SafetyLayer
    private const val TAG_VISION  = "TANGGAP_VISION"   // VisionPipeline + MLKit
    private const val TAG_PROMPT  = "TANGGAP_PROMPT"   // Prompt yang dikirim ke Gemma
    private const val TAG_TIMING  = "TANGGAP_TIMING"   // Waktu eksekusi tiap tahap

    // ─────────────────────────────────────────────────────────────────────────
    // 1. DISASTER TYPE DETECTOR
    // ─────────────────────────────────────────────────────────────────────────

    fun logDetectorInput(query: String) {
        if (!ENABLED) return
        Log.d(TAG_DETECT, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG_DETECT, "[STEP 1] INPUT QUERY")
        Log.d(TAG_DETECT, "  query        : \"$query\"")
        Log.d(TAG_DETECT, "  query_lower  : \"${query.lowercase()}\"")
        Log.d(TAG_DETECT, "  char_count   : ${query.length}")
    }

    fun logDetectorScores(gempaScore: Int, banjirScore: Int, longsorScore: Int, result: DisasterType) {
        if (!ENABLED) return
        Log.d(TAG_DETECT, "[STEP 2] SKOR DETEKSI BENCANA")
        Log.d(TAG_DETECT, "  gempa_score  : $gempaScore")
        Log.d(TAG_DETECT, "  banjir_score : $banjirScore")
        Log.d(TAG_DETECT, "  longsor_score: $longsorScore")
        Log.d(TAG_DETECT, "  semua_nol    : ${gempaScore == 0 && banjirScore == 0 && longsorScore == 0}")
        Log.d(TAG_DETECT, "  ▶ HASIL      : $result")
        Log.d(TAG_DETECT, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. RAG PIPELINE
    // ─────────────────────────────────────────────────────────────────────────

    fun logRagTranslation(originalQuery: String, translatedQuery: String) {
        if (!ENABLED) return
        Log.d(TAG_RAG, "[STEP 3] TERJEMAHAN QUERY (EN→ID)")
        Log.d(TAG_RAG, "  original   : \"$originalQuery\"")
        Log.d(TAG_RAG, "  translated : \"$translatedQuery\"")
        Log.d(TAG_RAG, "  ada_ubahan : ${originalQuery.lowercase() != translatedQuery}")
    }

    fun logBm25Search(query: String, disasterType: DisasterType, totalChunks: Int, topK: Int) {
        if (!ENABLED) return
        Log.d(TAG_RAG, "[STEP 4] BM25 SEARCH")
        Log.d(TAG_RAG, "  search_query    : \"$query\"")
        Log.d(TAG_RAG, "  filter_disaster : $disasterType")
        Log.d(TAG_RAG, "  total_chunks_kb : $totalChunks")
        Log.d(TAG_RAG, "  top_k           : $topK")
    }

    fun logRagResult(result: RAGResult) {
        if (!ENABLED) return
        Log.d(TAG_RAG, "[STEP 5] HASIL RAG")
        Log.d(TAG_RAG, "  disaster_type : ${result.disasterType}")
        Log.d(TAG_RAG, "  has_results   : ${result.hasResults}")
        Log.d(TAG_RAG, "  chunk_count   : ${result.chunks.size}")
        result.chunks.forEachIndexed { idx, chunk ->
            Log.d(TAG_RAG, "  ── CHUNK #${idx + 1} ──────────────────────────────")
            Log.d(TAG_RAG, "     chunk_id      : ${chunk.chunkId}")
            Log.d(TAG_RAG, "     disaster_type : ${chunk.disasterType}")
            Log.d(TAG_RAG, "     source        : ${chunk.source}")
            Log.d(TAG_RAG, "     topic         : ${chunk.topic.ifBlank { "(kosong)" }}")
            Log.d(TAG_RAG, "     text_preview  : \"${chunk.text.take(120)}...\"")
            Log.d(TAG_RAG, "     text_length   : ${chunk.text.length} karakter")
        }
        if (result.chunks.isEmpty()) {
            Log.w(TAG_RAG, "  ⚠ Tidak ada chunk yang ditemukan — Gemma akan menjawab tanpa konteks RAG")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. CONVERSATION CONTEXT
    // ─────────────────────────────────────────────────────────────────────────

    fun logContext(ctx: ConversationContext, phase: String = "SEBELUM") {
        if (!ENABLED) return
        Log.d(TAG_CONTEXT, "[CTX $phase]")
        Log.d(TAG_CONTEXT, "  is_active       : ${ctx.isActive()}")
        Log.d(TAG_CONTEXT, "  disaster_type   : ${ctx.disasterType ?: "null"}")
        Log.d(TAG_CONTEXT, "  turn_count      : ${ctx.turnCount}/${ConversationContext.MAX_TURNS}")
        Log.d(TAG_CONTEXT, "  is_resolved     : ${ctx.isResolved}")
        Log.d(TAG_CONTEXT, "  has_image       : ${ctx.hasImage}")
        Log.d(TAG_CONTEXT, "  last_location   : ${ctx.lastLocation ?: "(tidak ada)"}")
        Log.d(TAG_CONTEXT, "  history_turns   : ${ctx.chatHistory.size}")
        Log.d(TAG_CONTEXT, "  last_query_prev : \"${ctx.lastUserQuery.take(80)}\"")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GEMMA INFERENCE ENGINE — decision routing
    // ─────────────────────────────────────────────────────────────────────────

    fun logRoutingDecision(
        query: String,
        isCasual: Boolean,
        isInformational: Boolean,
        isEscalation: Boolean,
        needsFormat: Boolean,
        forceNoFormat: Boolean,
        contextActive: Boolean,
        isSafeStatement: Boolean
    ) {
        if (!ENABLED) return
        Log.d(TAG_ENGINE, "[STEP 6] ROUTING KEPUTUSAN")
        Log.d(TAG_ENGINE, "  query_snippet      : \"${query.take(60)}\"")
        Log.d(TAG_ENGINE, "  is_safe_statement  : $isSafeStatement")
        Log.d(TAG_ENGINE, "  is_casual          : $isCasual")
        Log.d(TAG_ENGINE, "  is_informational   : $isInformational")
        Log.d(TAG_ENGINE, "  context_is_active  : $contextActive")
        Log.d(TAG_ENGINE, "  is_escalation      : $isEscalation")
        Log.d(TAG_ENGINE, "  needs_format       : $needsFormat")
        Log.d(TAG_ENGINE, "  force_no_format    : $forceNoFormat")

        val route = when {
            isSafeStatement  -> "→ SAFE_CONFIRMATION (situasi dinyatakan aman)"
            isCasual && contextActive -> "→ EMERGENCY_REMINDER (casual di tengah sesi darurat)"
            isCasual         -> "→ CASUAL_REPLY (sapa/terima kasih)"
            isInformational  -> "→ INFORMATIONAL (pengetahuan umum, tanpa format darurat)"
            isEscalation     -> "→ ESCALATION (darurat mendesak, prioritas tinggi)"
            else             -> "→ EMERGENCY_GUIDANCE (panduan darurat normal)"
        }
        Log.d(TAG_ENGINE, "  ▶ ROUTE            : $route")
    }

    fun logDisasterTypeResolution(
        ragType: DisasterType,
        ctxType: DisasterType?,
        isEscalation: Boolean,
        effectiveType: DisasterType
    ) {
        if (!ENABLED) return
        Log.d(TAG_ENGINE, "[STEP 7] RESOLUSI JENIS BENCANA")
        Log.d(TAG_ENGINE, "  rag_type      : $ragType")
        Log.d(TAG_ENGINE, "  context_type  : ${ctxType ?: "null (sesi baru)"}")
        Log.d(TAG_ENGINE, "  is_escalation : $isEscalation")
        Log.d(TAG_ENGINE, "  ▶ EFEKTIF     : $effectiveType")
        if (ctxType != null && ragType != ctxType) {
            Log.w(TAG_ENGINE, "  ⚠ Konflik tipe: RAG=$ragType vs Context=$ctxType → pakai $effectiveType")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. PROMPT YANG DIKIRIM KE GEMMA
    // ─────────────────────────────────────────────────────────────────────────

    fun logPrompt(prompt: String) {
        if (!ENABLED) return
        Log.d(TAG_PROMPT, "[STEP 8] PROMPT DIKIRIM KE GEMMA")
        Log.d(TAG_PROMPT, "  total_panjang : ${prompt.length} karakter")
        Log.d(TAG_PROMPT, "  ── ISI PROMPT (penuh) ──────────────────────────")
        // Cetak per 500 karakter agar tidak terpotong di Logcat
        prompt.chunked(500).forEachIndexed { i, part ->
            Log.d(TAG_PROMPT, "  [bagian ${i+1}] $part")
        }
        Log.d(TAG_PROMPT, "  ── AKHIR PROMPT ────────────────────────────────")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. RESPONSE DARI GEMMA + SAFETY LAYER
    // ─────────────────────────────────────────────────────────────────────────

    fun logRawResponse(raw: String) {
        if (!ENABLED) return
        Log.d(TAG_ENGINE, "[STEP 9] RESPONSE MENTAH DARI GEMMA")
        Log.d(TAG_ENGINE, "  panjang_karakter : ${raw.length}")
        Log.d(TAG_ENGINE, "  preview          : \"${raw.take(200)}\"")
    }

    fun logSafetyLayer(topicDetected: String, disclaimerAdded: String, bpbdContact: String) {
        if (!ENABLED) return
        Log.d(TAG_SAFETY, "[STEP 10] SAFETY LAYER")
        Log.d(TAG_SAFETY, "  topic_terdeteksi : $topicDetected")
        Log.d(TAG_SAFETY, "  disclaimer       : \"$disclaimerAdded\"")
        Log.d(TAG_SAFETY, "  kontak_bpbd      : $bpbdContact")
    }

    fun logFinalResponse(final: String) {
        if (!ENABLED) return
        Log.d(TAG_ENGINE, "[STEP 11] RESPONSE FINAL (setelah safety layer)")
        Log.d(TAG_ENGINE, "  panjang_total : ${final.length} karakter")
        Log.d(TAG_ENGINE, "  preview       : \"${final.take(300)}\"")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. TIMING
    // ─────────────────────────────────────────────────────────────────────────

    fun logTiming(label: String, ms: Long) {
        if (!ENABLED) return
        val emoji = when {
            ms < 500  -> "🟢"
            ms < 2000 -> "🟡"
            ms < 5000 -> "🟠"
            else      -> "🔴"
        }
        Log.d(TAG_TIMING, "$emoji $label : ${ms}ms")
    }

    fun logTotalPipeline(ms: Long) {
        if (!ENABLED) return
        Log.d(TAG_TIMING, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG_TIMING, "⏱ TOTAL PIPELINE : ${ms}ms")
        Log.d(TAG_TIMING, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. VISION PIPELINE
    // ─────────────────────────────────────────────────────────────────────────

    fun logVisionInput(width: Int, height: Int, userHint: String) {
        if (!ENABLED) return
        Log.d(TAG_VISION, "[VISION STEP 1] INPUT GAMBAR")
        Log.d(TAG_VISION, "  dimensi   : ${width}x${height}px")
        Log.d(TAG_VISION, "  user_hint : \"$userHint\"")
    }

    fun logMlKitResult(labels: List<String>) {
        if (!ENABLED) return
        Log.d(TAG_VISION, "[VISION STEP 2] HASIL ML KIT")
        if (labels.isEmpty()) {
            Log.w(TAG_VISION, "  ⚠ Tidak ada label terdeteksi")
        } else {
            labels.forEachIndexed { i, label ->
                Log.d(TAG_VISION, "  label[$i] : $label")
            }
        }
    }

    fun logVisionContext(visionCtx: String, detectedDisaster: String, resolvedType: String) {
        if (!ENABLED) return
        Log.d(TAG_VISION, "[VISION STEP 3] KONTEKS VISUAL")
        Log.d(TAG_VISION, "  vision_context   : $visionCtx")
        Log.d(TAG_VISION, "  detected_disaster: $detectedDisaster")
        Log.d(TAG_VISION, "  ▶ resolved_type  : $resolvedType")
    }

    fun logVisionFallback(reason: String) {
        if (!ENABLED) return
        Log.w(TAG_VISION, "[VISION FALLBACK] Native vision gagal → MLKit fallback")
        Log.w(TAG_VISION, "  alasan : $reason")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. ERROR
    // ─────────────────────────────────────────────────────────────────────────

    fun logError(tag: String, step: String, error: Throwable) {
        if (!ENABLED) return
        Log.e("TANGGAP_ERROR", "[$step] ERROR di $tag")
        Log.e("TANGGAP_ERROR", "  type    : ${error.javaClass.simpleName}")
        Log.e("TANGGAP_ERROR", "  message : ${error.message}")
        Log.e("TANGGAP_ERROR", "  cause   : ${error.cause?.message ?: "-"}")
    }

    private const val TAG_TRIAGE = "TANGGAP_TRIAGE"

    fun logTriageDetection(
        query: String,
        lang: String,
        triageType: String?,      // null = tidak trigger
        triageAlreadyDone: Boolean,
        hasImage: Boolean,
        shouldTriage: Boolean
    ) {
        if (!ENABLED) return
        Log.d(TAG_TRIAGE, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG_TRIAGE, "[TRIAGE STEP 1] DETEKSI TRIAGE")
        Log.d(TAG_TRIAGE, "  query              : \"${query.take(80)}\"")
        Log.d(TAG_TRIAGE, "  lang               : $lang")
        Log.d(TAG_TRIAGE, "  triage_type        : ${triageType ?: "null (tidak ada keyword cocok)"}")
        Log.d(TAG_TRIAGE, "  triage_already_done: $triageAlreadyDone")
        Log.d(TAG_TRIAGE, "  has_image          : $hasImage  ← triage skip jika ada gambar")
        Log.d(TAG_TRIAGE, "  ▶ should_triage    : $shouldTriage")

        if (!shouldTriage) {
            val reason = when {
                hasImage           -> "SKIP — ada gambar, langsung ke vision pipeline"
                triageType == null -> "SKIP — tidak ada keyword triage terdeteksi"
                triageAlreadyDone  -> "SKIP — triage sudah dilakukan di pesan sebelumnya"
                else               -> "SKIP — alasan tidak diketahui"
            }
            Log.d(TAG_TRIAGE, "  ✗ alasan skip      : $reason")
        } else {
            Log.d(TAG_TRIAGE, "  ✓ TRIAGE CARD akan ditampilkan")
        }
        Log.d(TAG_TRIAGE, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    fun logTriageCardShown(
        triageType: String,
        q1: String,
        q2: String,
        q3: String
    ) {
        if (!ENABLED) return
        Log.d(TAG_TRIAGE, "[TRIAGE STEP 2] CARD DITAMPILKAN")
        Log.d(TAG_TRIAGE, "  triage_type : $triageType")
        Log.d(TAG_TRIAGE, "  pertanyaan_1: \"$q1\"")
        Log.d(TAG_TRIAGE, "  pertanyaan_2: \"$q2\"")
        Log.d(TAG_TRIAGE, "  pertanyaan_3: \"$q3\"")
    }

    fun logTriageAnswer(summary: String) {
        if (!ENABLED) return
        Log.d(TAG_TRIAGE, "[TRIAGE STEP 3] USER MENJAWAB CARD")
        Log.d(TAG_TRIAGE, "  summary dikirim ke Gemma:")
        // Cetak per 300 karakter karena summary bisa panjang
        summary.chunked(300).forEachIndexed { i, part ->
            Log.d(TAG_TRIAGE, "  [bagian ${i+1}] $part")
        }
        Log.d(TAG_TRIAGE, "  panjang_summary: ${summary.length} karakter")
        Log.d(TAG_TRIAGE, "  → selanjutnya masuk ke generateResponse() normal")
        Log.d(TAG_TRIAGE, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

}
