package id.tanggap.app.data

import android.content.Context
import id.tanggap.app.debug.TanggapLogger

class RAGPipeline(context: Context) {
    private val chunks: List<Chunk> = KnowledgeBaseLoader.load(context)
    private val bm25: BM25Engine = BM25Engine(chunks)

    private val enToId = listOf(
        "flash flood" to "banjir bandang",
        "water rising" to "air naik",
        "flooded" to "banjir",
        "flooding" to "banjir",
        "flood" to "banjir",
        "inundated" to "terendam",
        "submerged" to "terendam",
        "overflow" to "luapan",
        "waterlogged" to "genangan",
        "drowning" to "tenggelam",
        "drowned" to "tenggelam",
        "earthquakes" to "gempa",
        "earthquake" to "gempa",
        "aftershock" to "gempa susulan",
        "tremors" to "gempa",
        "tremor" to "gempa",
        "collapsed" to "runtuh",
        "collapse" to "runtuh",
        "shaking" to "guncang",
        "rubble" to "reruntuhan",
        "seismic" to "seismik",
        "landslides" to "longsor",
        "landslide" to "longsor",
        "mudslide" to "longsor",
        "landslip" to "longsor",
        "slopes" to "lereng",
        "slope" to "lereng",
        "debris flow" to "material longsor",
        "rockslide" to "longsor",
        "injuries" to "cedera",
        "injured" to "cedera",
        "injury" to "cedera",
        "wounds" to "luka",
        "wound" to "luka",
        "bleeding" to "darah",
        "fracture" to "patah tulang",
        "first aid" to "pertolongan pertama",
        "unconscious" to "pingsan",
        "breathing" to "pernapasan",
        "cpr" to "rjp",
        "evacuate" to "evakuasi",
        "evacuation" to "evakuasi",
        "trapped" to "terjebak",
        "rescue" to "penyelamatan",
        "survivors" to "korban selamat",
        "survivor" to "korban selamat",
        "victims" to "korban",
        "victim" to "korban",
        "emergency" to "darurat",
        "danger" to "bahaya",
        "safe" to "aman",
        "house" to "rumah",
        "building" to "bangunan",
        "roof" to "atap",
        "chest" to "dada",
        "help" to "bantuan",
        "deep" to "dalam",
        "water" to "air",
        "level" to "tinggi",
        "recovery" to "pemulihan",
        "rebuild" to "perbaikan",
        "cleanup" to "bersih-bersih",
        "contaminated" to "terkontaminasi",
        "disease" to "penyakit",
        "after" to "pasca",
        "post" to "pasca"
    )

    private fun translateToId(query: String): String {
        var result = query.lowercase()
        enToId.forEach { (en, id) -> result = result.replace(en, id) }
        return result
    }

    fun query(userInput: String): RAGResult {
        val disasterType = DisasterTypeDetector.detect(userInput)
        val searchQuery  = translateToId(userInput)

        TanggapLogger.logRagTranslation(userInput, searchQuery)

        val topChunks: List<Chunk> = when (disasterType) {
            is DisasterType.COMPOUND -> {
                // Ambil top-2 dari primary, top-1 dari secondary, total 3
                val primaryChunks   = bm25.search(searchQuery, disasterType.primary,   topK = 2)
                val secondaryChunks = bm25.search(searchQuery, disasterType.secondary, topK = 1)
                (primaryChunks + secondaryChunks).distinctBy { it.chunkId }
            }
            else -> {
                TanggapLogger.logBm25Search(searchQuery, disasterType, chunks.size, topK = 3)
                bm25.search(searchQuery, disasterType, topK = 3)
            }
        }

        val result = RAGResult(
            disasterType = disasterType,
            chunks       = topChunks,
            hasResults   = topChunks.isNotEmpty()
        )

        TanggapLogger.logRagResult(result)
        return result
    }
}

data class RAGResult(
    val disasterType: DisasterType,
    val chunks: List<Chunk>,
    val hasResults: Boolean
)
