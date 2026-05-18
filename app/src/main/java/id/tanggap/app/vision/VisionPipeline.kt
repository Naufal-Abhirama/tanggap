package id.tanggap.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import id.tanggap.app.data.DisasterType
import java.io.ByteArrayOutputStream

data class VisionResult(
    val context: VisionContext,
    val disasterType: DisasterType,
    val rawResponse: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class VisionPipeline(private val appContext: Context) {

    companion object {
        private const val TAG = "VisionPipeline"
        const val TARGET_SIZE = 512
        const val JPEG_QUALITY = 85
    }

    fun preprocessImage(uri: Uri): Bitmap {
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Tidak bisa buka URI: $uri")
        val original = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("Gagal decode gambar dari URI: $uri")
        val (targetW, targetH) = calculateTargetDimensions(original.width, original.height)
        val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
        if (scaled !== original) original.recycle()
        return scaled
    }

    fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val (targetW, targetH) = calculateTargetDimensions(bitmap.width, bitmap.height)
        if (targetW == bitmap.width && targetH == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun calculateTargetDimensions(w: Int, h: Int): Pair<Int, Int> {
        if (w <= TARGET_SIZE && h <= TARGET_SIZE) return w to h
        return if (w > h) TARGET_SIZE to (h * TARGET_SIZE / w)
        else (w * TARGET_SIZE / h) to TARGET_SIZE
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    // Deteksi konteks dari hint teks — hanya INJURY atau ENVIRONMENT
    fun detectContextFromHint(userHint: String?): VisionContext {
        if (userHint == null) return VisionContext.ENVIRONMENT
        val lower = userHint.lowercase()
        return if (listOf(
                "luka", "cedera", "darah", "patah", "sakit",
                "injury", "hurt", "berdarah", "lebam", "memar"
            ).any { lower.contains(it) }
        ) VisionContext.INJURY
        else VisionContext.ENVIRONMENT
    }
}
