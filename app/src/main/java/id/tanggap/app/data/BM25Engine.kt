package id.tanggap.app.data

import kotlin.math.ln

class BM25Engine(private val chunks: List<Chunk>) {
    private val k1 = 1.5f
    private val b  = 0.75f

    private val tokenizedCorpus: List<List<String>> = chunks.map { tokenize(it.text) }
    private val avgDocLen: Float = tokenizedCorpus.map { it.size }.average().toFloat()
    private val df: Map<String, Int> = buildDf()

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("\\s+")).filter { it.length > 2 }

    private fun buildDf(): Map<String, Int> {
        val df = mutableMapOf<String, Int>()
        tokenizedCorpus.forEach { doc ->
            doc.toSet().forEach { term -> df[term] = (df[term] ?: 0) + 1 }
        }
        return df
    }

    // BM25Engine hanya terima tipe tunggal (GEMPA/BANJIR/LONGSOR/UMUM).
    fun search(query: String, disasterType: DisasterType, topK: Int = 3): List<Chunk> {
        val queryTerms = tokenize(query)
        val n = chunks.size.toFloat()

        val effectiveType = when (disasterType) {
            is DisasterType.COMPOUND -> disasterType.primary
            else                     -> disasterType
        }

        val candidates = when (effectiveType) {
            is DisasterType.UMUM -> chunks.indices.toList()
            else -> {
                val typeName = when (effectiveType) {
                    is DisasterType.GEMPA   -> "gempa"
                    is DisasterType.BANJIR  -> "banjir"
                    is DisasterType.LONGSOR -> "longsor"
                    else                    -> ""
                }
                chunks.indices.filter {
                    chunks[it].disasterType == typeName ||
                            chunks[it].disasterType == "kedaruratan_medis" ||
                            chunks[it].disasterType == "${typeName}_banjir" ||
                            chunks[it].disasterType == "gempa_${typeName}" ||
                            chunks[it].disasterType == "pasca_bencana"  // ← TAMBAH INI
                }
            }
        }

        val scores = candidates.map { idx ->
            val doc    = tokenizedCorpus[idx]
            val docLen = doc.size.toFloat()
            val tf     = doc.groupingBy { it }.eachCount()

            val score = queryTerms.sumOf { term ->
                val termTf = tf[term] ?: 0
                val termDf = df[term] ?: 0
                if (termDf == 0) return@sumOf 0.0
                val idf    = ln((n - termDf + 0.5) / (termDf + 0.5) + 1)
                val tfNorm = (termTf * (k1 + 1)) / (termTf + k1 * (1 - b + b * docLen / avgDocLen))
                idf * tfNorm
            }
            idx to score
        }

        return scores
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { chunks[it.first] }
    }
}
