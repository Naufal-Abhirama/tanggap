package id.tanggap.app.data

enum class TriageType { KORBAN_LUKA, BENCANA_AKTIF, TERPERANGKAP, MULTI_KORBAN }

data class TriageData(
    val type: TriageType,
    val q1: String,
    val q2: String,
    val q3: String
)

private fun isInformationalQuery(lower: String, lang: String): Boolean {
    val informationalPrefixes = if (lang == "en") listOf(
        "how to", "how do i", "how do you", "how can i", "how can you",
        "what is", "what are", "what should", "what do i", "what do you",
        "when should", "where should", "why does", "why is",
        "tips for", "tips on", "guide for", "guide to",
        "steps to", "steps for", "ways to", "method to",
        "can you explain", "can you tell me", "can you show",
        "explain how", "tell me how", "show me how",
        "what to do", "what to bring", "what to prepare",
        "is it safe", "is it possible",
        "difference between", "how does"
    ) else listOf(
        // Kata tanya + cara
        "bagaimana cara", "gimana cara", "cara membuat", "cara bikin",
        "cara mengatasi", "cara menghadapi", "cara menghindari",
        "cara menyelamatkan", "cara membersihkan", "cara menggunakan",
        "cara mendapatkan", "cara mencari", "cara menemukan",
        "cara mengobati", "cara menangani", "cara mencegah",
        // Pertanyaan informatif
        "apa yang harus", "apa yang perlu", "apa yang bisa",
        "apa itu", "apa saja", "apa penyebab", "apa bedanya",
        "bagaimana jika", "bagaimana kalau", "bagaimana supaya",
        "bagaimana agar", "gimana kalau", "gimana supaya",
        "kenapa ", "mengapa ", "kapan ", "di mana ", "dimana ",
        "tips ", "panduan ", "langkah-langkah", "langkah langkah",
        "tutorial ", "petunjuk ", "prosedur ",
        "bisa jelaskan", "tolong jelaskan", "jelaskan ",
        "apa tanda", "apa ciri", "apa gejala",
        "berapa lama", "berapa banyak", "berapa jauh",
        "apakah bisa", "apakah aman", "apakah mungkin",
        "boleh ", "bisakah ", "dapatkah ",
        "informasi tentang", "info tentang", "tahu tentang",
        "ceritakan tentang", "jelaskan tentang",
        // Frasa "membuat" + objek (bukan laporan darurat)
        "membuat air", "membuat minuman", "membuat makanan",
        "menyaring air", "memurnikan air", "mendapatkan air bersih",
        "mengolah air"
    )

    return informationalPrefixes.any { lower.startsWith(it) || lower.contains(it) }
}

fun detectTriageType(query: String, lang: String): TriageType? {
    val lower = query.lowercase()

    // --- GUARD: skip triage untuk pertanyaan informatif ---
    if (isInformationalQuery(lower, lang)) return null

    val multiKorbanKw = if (lang == "en") listOf(
        "several people", "many people", "multiple victims", "multiple people",
        "people are injured", "some people are", "a few people",
        "two people", "three people", "four people",
        "there are victims", "there are injured people",
        "group of people", "my neighbor is also", "my family members are"
    ) else listOf(
        "beberapa orang", "banyak orang", "banyak korban", "banyak yang terluka",
        "banyak yang pingsan", "ada korban lain", "beberapa korban",
        "dua orang", "tiga orang", "empat orang", "lima orang",
        "ada banyak korban", "banyak yang terjebak",
        "keluarga saya juga", "tetangga saya juga",
        "ada yang luka parah", "ada yang tidak sadar",
        "orang lain juga luka", "orang lain juga terluka"
    )

    val lukaKw = if (lang == "en") listOf(
        "unconscious", "fainted", "bleeding", "severe injury", "not breathing",
        "passed out", "can't breathe", "cannot breathe", "heart attack",
        "seizure", "choking", "broken bone", "fracture",
        "someone is hurt", "badly hurt", "seriously hurt", "critical condition",
        "head injury", "chest pain", "can't move", "cannot move"
    ) else listOf(
        "pingsan", "tidak sadar", "nggak sadar", "pendarahan", "luka parah",
        "tidak bernapas", "nggak bernapas", "koma", "nadi lemah",
        "kejang", "tersedak", "sesak napas", "darah banyak",
        "patah tulang", "retak tulang",
        "luka berat", "cedera parah", "cedera berat", "luka serius",
        "kepala bocor", "tulang patah", "nggak bisa gerak", "tidak bisa bergerak",
        "tidak bergerak", "nggak bergerak", "sadar tapi", "masih bernapas tapi",
        "ada luka", "ada cedera", "ada yang terluka", "ada yang cedera",
        "terluka parah", "ada darah", "keluar darah", "darah keluar",
        "ada yang jatuh", "jatuh dan", "tertimpa dan", "kena benturan"
    )

    val terperangkapKw = if (lang == "en") listOf(
        "trapped", "stuck", "buried", "cant get out", "can't get out",
        "help me out", "i'm stuck", "im stuck", "rubble", "collapsed on me",
        "cannot exit", "can't exit", "no way out",
        "can't escape", "cannot escape", "blocked", "debris on me",
        "wall fell on me", "roof collapsed", "ceiling fell", "pinned down",
        "under rubble", "under debris"
    ) else listOf(
        "terjebak", "terperangkap", "tertimpa", "tertimbun", "gabisa keluar",
        "tidak bisa keluar", "nggak bisa keluar", "reruntuhan", "timpa",
        "kejebak", "kejepit", "tidak ada jalan keluar",
        "gabisa kemana-mana", "gabisa kemana mana", "nggak bisa kemana-mana",
        "tembok jatuh", "atap jatuh", "atap roboh", "dinding roboh",
        "tertimpa benda", "tertimpa balok", "tertimpa tembok", "tertimpa atap",
        "di bawah reruntuhan", "di balik reruntuhan", "pintu tersumbat",
        "pintu nggak bisa dibuka", "keluar nggak bisa", "susah keluar",
        "mau keluar tapi", "coba keluar tapi", "masuk tapi nggak bisa"
    )

    val bencanaKw = if (lang == "en") listOf(
        "flood", "water rising", "water is rising", "earthquake", "landslide",
        "eruption", "wave", "fire spreading", "tsunami", "shaking",
        "ground moving", "building collapsing",
        "there's a flood", "there's an earthquake", "there's a landslide",
        "earthquake is happening", "flood is happening", "flash flood",
        "the ground is shaking", "things are falling", "water level rising",
        "i'm in a flood", "i'm in an earthquake", "during the earthquake",
        "during the flood", "right now earthquake", "right now flood"
    ) else listOf(
        "air naik", "air terus naik", "gempa", "longsor",
        "erupsi", "gelombang", "api menyebar", "tsunami",
        "tanah gerak", "gunung meletus", "banjir bandang",
        "bangunan runtuh", "gedung runtuh",
        "sedang gempa", "lagi gempa", "ada gempa", "kena gempa",
        "sedang banjir", "lagi banjir", "ada banjir", "kena banjir",
        "sedang longsor", "ada longsor", "kena longsor",
        "airnya naik", "air makin naik", "air semakin naik",
        "guncangan", "getaran kuat", "tanah bergetar",
        "terjebak gempa", "terjebak banjir", "terjebak longsor",
        "saat gempa", "waktu gempa", "ketika gempa",
        "saat banjir", "waktu banjir", "ketika banjir",
        "rumah goyang", "gedung goyang", "lantai bergetar",
        "air masuk rumah", "air masuk ke rumah", "rumah kebanjiran",
        "banjir masuk", "banjir datang"
    )

    // Keyword yang hanya match jika berdiri sendiri sebagai konteks darurat
    // (bukan bagian dari kalimat informatif — sudah di-guard di atas)
    val bencanaKwExact = if (lang == "en") listOf(
        "flood!", "earthquake!", "help flood", "help earthquake"
    ) else listOf(
        "banjir!", "tolong banjir", "tolong gempa", "tolong ada banjir",
        "tolong ada gempa", "ada banjir sekarang", "ada gempa sekarang"
    )

    return when {
        multiKorbanKw.any { lower.contains(it) } -> TriageType.MULTI_KORBAN
        lukaKw.any { lower.contains(it) } -> TriageType.KORBAN_LUKA
        terperangkapKw.any { lower.contains(it) } -> TriageType.TERPERANGKAP
        bencanaKw.any { lower.contains(it) } -> TriageType.BENCANA_AKTIF
        bencanaKwExact.any { lower.contains(it) } -> TriageType.BENCANA_AKTIF
        else -> null
    }
}

fun buildTriageData(type: TriageType, lang: String): TriageData = when (type) {

    TriageType.KORBAN_LUKA -> if (lang == "en") TriageData(
        type = type,
        q1 = "Is the person still conscious?",
        q2 = "Is there active bleeding?",
        q3 = "Is the person breathing normally?"
    ) else TriageData(
        type = type,
        q1 = "Korban masih sadar?",
        q2 = "Ada pendarahan aktif?",
        q3 = "Korban masih bisa bernapas normal?"
    )

    TriageType.TERPERANGKAP -> if (lang == "en") TriageData(
        type = type,
        q1 = "Can you move your limbs?",
        q2 = "Is anyone badly injured nearby?",
        q3 = "Can you hear rescuers outside?"
    ) else TriageData(
        type = type,
        q1 = "Kamu masih bisa menggerakkan anggota tubuh?",
        q2 = "Ada orang lain yang luka parah di sekitarmu?",
        q3 = "Bisa dengar suara penolong di luar?"
    )

    TriageType.BENCANA_AKTIF -> if (lang == "en") TriageData(
        type = type,
        q1 = "Are you currently indoors or outdoors?",
        q2 = "Is anyone else with you?",
        q3 = "Can you safely exit your current location?"
    ) else TriageData(
        type = type,
        q1 = "Kamu sekarang di dalam atau luar ruangan?",
        q2 = "Ada orang lain bersamamu?",
        q3 = "Bisa keluar dari lokasi sekarang dengan aman?"
    )

    TriageType.MULTI_KORBAN -> if (lang == "en") TriageData(
        type = type,
        q1 = "Is anyone NOT breathing at all (even after repositioning airway)?",
        q2 = "How many people cannot walk on their own?",
        q3 = "Is there anyone still conscious but severely injured or bleeding heavily?"
    ) else TriageData(
        type = type,
        q1 = "Ada yang sama sekali tidak bernapas (bahkan setelah kepala dimiringkan)?",
        q2 = "Berapa orang yang tidak bisa berjalan sendiri?",
        q3 = "Ada yang masih sadar tapi luka parah atau pendarahan hebat?"
    )
}

fun buildTriageSummary(
    type: TriageType,
    answers: Map<Int, Boolean?>,
    lang: String
): String {
    val a0 = answers[0]
    val a1 = answers[1]
    val a2 = answers[2]

    return when (type) {

        TriageType.KORBAN_LUKA -> if (lang == "en") {
            val conscious = if (a0 == true) "still conscious" else "UNCONSCIOUS"
            val bleeding  = if (a1 == true) "ACTIVE bleeding present" else "no active bleeding"
            val breathing = if (a2 == true) "breathing normally" else "NOT breathing normally — airway may be compromised"
            val urgency   = if (a0 == false || a2 == false) "CRITICAL" else if (a1 == true) "SEVERE" else "MODERATE"

            """
MEDICAL EMERGENCY [$urgency]
Victim status: $conscious | $bleeding | $breathing

Think step by step:
1. What is the most life-threatening condition right now?
2. What first aid action must happen in the next 60 seconds?
3. What should NOT be done that could cause more harm?

Provide immediate, specific first aid instructions for this exact condition.
            """.trimIndent()
        } else {
            val conscious = if (a0 == true) "masih sadar" else "TIDAK SADAR"
            val bleeding  = if (a1 == true) "ADA pendarahan aktif" else "tidak ada pendarahan aktif"
            val breathing = if (a2 == true) "bernapas normal" else "TIDAK bernapas normal — jalur napas mungkin tersumbat"
            val urgency   = if (a0 == false || a2 == false) "KRITIS" else if (a1 == true) "BERAT" else "SEDANG"

            """
DARURAT MEDIS [$urgency]
Kondisi korban: $conscious | $bleeding | $breathing

Pikirkan langkah demi langkah:
1. Kondisi apa yang paling mengancam jiwa sekarang?
2. Tindakan P3K apa yang harus dilakukan dalam 60 detik ke depan?
3. Apa yang TIDAK boleh dilakukan agar tidak memperburuk kondisi?

Berikan instruksi pertolongan pertama yang spesifik dan langsung untuk kondisi ini.
            """.trimIndent()
        }

        TriageType.TERPERANGKAP -> if (lang == "en") {
            val move    = if (a0 == true) "can move limbs" else "CANNOT move limbs — possible spinal/limb injury"
            val injured = if (a1 == true) "someone nearby is badly injured" else "no severe injuries nearby"
            val rescuer = if (a2 == true) "can hear rescuers outside" else "no rescuers audible — may need to self-rescue"
            val urgency = if (a0 == false) "CRITICAL" else if (a1 == true) "HIGH" else "MODERATE"

            """
TRAPPED SURVIVOR [$urgency]
Physical status: $move | $injured | $rescuer

Think step by step:
1. What is the most immediate danger (crush, suffocation, dehydration)?
2. Should this person attempt to self-rescue or stay and signal?
3. What resources (sound, light, air) can they use right now?

Give specific survival and self-rescue instructions for this exact situation.
            """.trimIndent()
        } else {
            val move    = if (a0 == true) "masih bisa bergerak" else "TIDAK bisa bergerak — kemungkinan cedera tulang/anggota tubuh"
            val injured = if (a1 == true) "ada orang lain yang luka parah" else "tidak ada luka parah di sekitar"
            val rescuer = if (a2 == true) "bisa dengar penolong di luar" else "tidak ada suara penolong — mungkin perlu evakuasi mandiri"
            val urgency = if (a0 == false) "KRITIS" else if (a1 == true) "TINGGI" else "SEDANG"

            """
KORBAN TERPERANGKAP [$urgency]
Kondisi fisik: $move | $injured | $rescuer

Pikirkan langkah demi langkah:
1. Bahaya paling mendesak apa yang mengancam (tertimpa, sesak, dehidrasi)?
2. Haruskah orang ini coba keluar sendiri atau tetap diam dan beri sinyal?
3. Sumber daya apa (suara, cahaya, udara) yang bisa digunakan sekarang?

Berikan instruksi survival dan evakuasi mandiri yang spesifik untuk kondisi ini.
            """.trimIndent()
        }

        TriageType.BENCANA_AKTIF -> if (lang == "en") {
            val location = if (a0 == true) "INDOORS" else "OUTDOORS"
            val alone    = if (a1 == true) "with others" else "ALONE"
            val canExit  = if (a2 == true) "CAN safely exit" else "CANNOT safely exit — must shelter in place"
            val urgency  = if (a2 == false) "CRITICAL" else if (a1 == false) "HIGH" else "MODERATE"

            """
ACTIVE DISASTER [$urgency]
Current position: $location | $alone | $canExit

Think step by step:
1. What is the immediate environmental threat (flooding, aftershock, structural collapse)?
2. Is sheltering in place or evacuating safer right now?
3. What should this person do in the next 5 minutes?

Give immediate safety actions and evacuation guidance for this exact situation.
            """.trimIndent()
        } else {
            val location = if (a0 == true) "DI DALAM RUANGAN" else "DI LUAR RUANGAN"
            val alone    = if (a1 == true) "bersama orang lain" else "SENDIRIAN"
            val canExit  = if (a2 == true) "BISA keluar dengan aman" else "TIDAK BISA keluar — harus berlindung di tempat"
            val urgency  = if (a2 == false) "KRITIS" else if (a1 == false) "TINGGI" else "SEDANG"

            """
BENCANA AKTIF [$urgency]
Posisi saat ini: $location | $alone | $canExit

Pikirkan langkah demi langkah:
1. Ancaman lingkungan apa yang paling mendesak (banjir, gempa susulan, bangunan runtuh)?
2. Apakah berlindung di tempat atau evakuasi lebih aman sekarang?
3. Apa yang harus dilakukan orang ini dalam 5 menit ke depan?

Berikan tindakan keselamatan segera dan panduan evakuasi yang spesifik untuk kondisi ini.
            """.trimIndent()
        }

        TriageType.MULTI_KORBAN -> if (lang == "en") {
            val noBreath  = if (a0 == true) "YES — at least one person is NOT breathing" else "No — everyone is breathing"
            val cantWalk  = if (a1 == true) "MULTIPLE people cannot walk" else "Most people can walk on their own"
            val severe    = if (a2 == true) "YES — conscious but severely injured victims present" else "No conscious-but-severe victims reported"
            val urgency   = if (a0 == true) "CRITICAL" else if (a1 == true) "HIGH" else "MODERATE"

            """
MULTI-VICTIM EMERGENCY [$urgency]
Not breathing: $noBreath
Cannot walk: $cantWalk
Conscious but severe: $severe

Think step by step using START triage:
1. RED (immediate): Who needs help RIGHT NOW to survive? (not breathing after airway open, severe bleeding)
2. YELLOW (delayed): Who is injured but stable? (can wait a few minutes)
3. GREEN (minor): Who can walk and help others?
4. Who among the responders can help, and what should each person do simultaneously?

Give specific, prioritized instructions for managing multiple victims with limited help.
            """.trimIndent()
        } else {
            val noBreath  = if (a0 == true) "YA — ada yang sama sekali tidak bernapas" else "Tidak — semua masih bernapas"
            val cantWalk  = if (a1 == true) "ADA BEBERAPA yang tidak bisa berjalan" else "Kebanyakan masih bisa berjalan sendiri"
            val severe    = if (a2 == true) "YA — ada yang sadar tapi luka parah atau pendarahan hebat" else "Tidak ada laporan korban sadar-tapi-parah"
            val urgency   = if (a0 == true) "KRITIS" else if (a1 == true) "TINGGI" else "SEDANG"

            """
DARURAT MULTI-KORBAN [$urgency]
Tidak bernapas: $noBreath
Tidak bisa berjalan: $cantWalk
Sadar tapi parah: $severe

Pikirkan langkah demi langkah menggunakan triase START awam:
1. MERAH (segera): Siapa yang butuh bantuan SEKARANG agar bisa bertahan hidup? (tidak bernapas setelah jalur napas dibuka, pendarahan hebat)
2. KUNING (tunda): Siapa yang terluka tapi masih stabil? (bisa tunggu beberapa menit)
3. HIJAU (ringan): Siapa yang bisa berjalan dan membantu orang lain?
4. Siapa di antara penolong yang ada, dan apa yang harus masing-masing lakukan secara bersamaan?

Berikan instruksi spesifik dan terurut prioritas untuk menangani beberapa korban dengan bantuan terbatas.
            """.trimIndent()
        }
    }
}