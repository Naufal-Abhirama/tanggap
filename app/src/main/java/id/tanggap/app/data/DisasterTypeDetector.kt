package id.tanggap.app.data

import id.tanggap.app.debug.TanggapLogger

sealed class DisasterType {
    object GEMPA   : DisasterType()
    object BANJIR  : DisasterType()
    object LONGSOR : DisasterType()
    object UMUM    : DisasterType()

    // Compound: dua bencana sekaligus (misal gempa + banjir)
    data class COMPOUND(val types: List<DisasterType>) : DisasterType() {
        val secondary: DisasterType get() = types.getOrElse(1) { UMUM }
    }

    // Helper: apakah mengandung tipe tertentu (termasuk compound)
    fun contains(type: DisasterType): Boolean = when (this) {
        is COMPOUND -> types.any { it == type }
        else        -> this == type
    }

    // Helper: tipe tunggal dominan (untuk backward compat)
    val primary: DisasterType get() = when (this) {
        is COMPOUND -> (this as COMPOUND).types.first()
        else        -> this
    }
}

object DisasterTypeDetector {

    private const val THRESHOLD = 2

    private fun score(lower: String, keywords: List<Pair<String, Int>>): Int =
        keywords.sumOf { (kw, weight) -> if (lower.contains(kw)) weight else 0 }

    private val gempaScores = listOf(
        "gempa" to 3, "tsunami" to 3, "seismik" to 3,
        "reruntuhan" to 3, "di bawah reruntuhan" to 4, "terjebak bawah" to 4,
        "bangunan runtuh" to 3, "gedung runtuh" to 3, "tertimpa bangunan" to 3,
        "tertimpa tembok" to 3, "tertimpa atap" to 3,
        "dinding retak" to 2, "guncang" to 2, "runtuh" to 2,
        "terjebak" to 2, "terperangkap" to 2, "tertimpa" to 2,
        "kejebak" to 2, "tidak bisa keluar" to 2, "gabisa keluar" to 2,
        "earthquake" to 3, "rubble" to 3, "collapsed" to 3,
        "trapped under" to 4, "buried under" to 4,
        "trapped" to 2, "stuck" to 1,
        "getaran" to 2, "gempa susulan" to 4, "aftershock" to 4
    )

    private val banjirScores = listOf(
        "banjir" to 3, "air naik" to 3, "banjir bandang" to 4,
        "tenggelam" to 3, "terendam" to 3, "genangan" to 2,
        "luapan" to 2, "bah" to 2, "air bah" to 3,
        "flood" to 3, "flooding" to 3, "water rising" to 3,
        "submerged" to 3, "inundated" to 3, "drowning" to 3,
        "air masuk" to 3, "air meluap" to 3, "banjir rob" to 4,
        "air pasang" to 3, "pasang tinggi" to 3
    )

    private val longsorScores = listOf(
        "longsor" to 3, "tanah longsor" to 4, "lereng" to 3,
        "tebing" to 3, "tanah bergerak" to 3, "lereng retak" to 4,
        "bukit longsor" to 4, "tanah amblas" to 3,
        "material" to 2, "runtuh" to 1,
        "landslide" to 3, "mudslide" to 3, "slope" to 2,
        "debris flow" to 3, "rockslide" to 3,
        "tanah gerak" to 3, "lereng amblas" to 4
    )

    // Jika keduanya di atas threshold, langsung COMPOUND tanpa cek selisih skor.
    private val knownCompoundPairs = setOf(
        setOf(DisasterType.GEMPA, DisasterType.BANJIR),    // gempa → tsunami/banjir
        setOf(DisasterType.LONGSOR, DisasterType.BANJIR),  // hujan → longsor + banjir
        setOf(DisasterType.GEMPA, DisasterType.LONGSOR)    // gempa → picu longsor
    )

    fun detect(query: String): DisasterType {
        val lower = query.lowercase()

        TanggapLogger.logDetectorInput(query)

        val g = score(lower, gempaScores)
        val b = score(lower, banjirScores)
        val l = score(lower, longsorScores)

        // Kumpulkan semua tipe yang memenuhi threshold
        val active = buildList {
            if (g >= THRESHOLD) add(DisasterType.GEMPA to g)
            if (b >= THRESHOLD) add(DisasterType.BANJIR to b)
            if (l >= THRESHOLD) add(DisasterType.LONGSOR to l)
        }.sortedByDescending { it.second }

        val result: DisasterType = when {
            active.isEmpty() -> DisasterType.UMUM

            active.size == 1 -> active.first().first

            // Cek apakah kombinasi ini dikenal sebagai pasangan wajar
            active.size >= 2 -> {
                val topTwo = active.take(2).map { it.first }.toSet()
                if (topTwo in knownCompoundPairs) {
                    // Urut: tipe dengan skor tertinggi jadi primary
                    DisasterType.COMPOUND(active.take(2).map { it.first })
                } else {
                    // Bukan pasangan dikenal → ambil yang skornya paling tinggi
                    // Tapi hanya jika selisih signifikan (≥ 2 poin)
                    val top    = active[0]
                    val second = active[1]
                    if (top.second - second.second >= 2) {
                        top.first
                    } else {
                        // Selisih kecil dan bukan pasangan dikenal → tetap COMPOUND
                        DisasterType.COMPOUND(listOf(top.first, second.first))
                    }
                }
            }

            else -> DisasterType.UMUM
        }

        TanggapLogger.logDetectorScores(g, b, l, result)
        return result
    }
}
