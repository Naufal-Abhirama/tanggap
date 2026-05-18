package id.tanggap.app.vision

enum class VisionContext {
    INJURY,      // Foto luka/cedera → langkah P3K
    ENVIRONMENT, // Foto bangunan/lingkungan → level bahaya + evakuasi
    UNCLEAR,     // Foto tidak dikenali / tidak relevan bencana → respons santai
}
