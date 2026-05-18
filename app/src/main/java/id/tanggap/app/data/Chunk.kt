package id.tanggap.app.data

data class Chunk(
    val chunkId: String,
    val disasterType: String, // "gempa", "banjir", "longsor", "umum"
    val source: String,
    val topic: String,
    val text: String
)
