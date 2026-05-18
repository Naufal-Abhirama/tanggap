package id.tanggap.app.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles in-app model download from HuggingFace.
 * Supports resume (Range header) sehingga download yang terputus bisa dilanjutkan.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownload"
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

    // URL dari litert-community di HuggingFace (resolve redirect otomatis)
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    private const val MIN_VALID_SIZE = 100_000_000L   // 100 MB — file partial dianggap invalid

    fun getModelFile(context: Context): File =
        File(context.filesDir, MODEL_FILENAME)

    fun isModelReady(context: Context): Boolean {
        val f = getModelFile(context)
        return f.exists() && f.length() > MIN_VALID_SIZE
    }

    /**
     * Download model dengan resume support.
     * Callback dipanggil di IO thread — caller harus switch ke Main untuk update UI.
     */
    suspend fun download(
        context: Context,
        onProgress: (downloadedMB: Int, totalMB: Int, percent: Int) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dest = getModelFile(context)

        // Jika sudah ada file partial, lanjutkan dari sana
        val existingBytes = if (dest.exists()) dest.length() else 0L

        try {
            val url = URL(MODEL_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout    = 60_000
                setRequestProperty("User-Agent", "TANGGAP-AI/1.0")
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                    Log.d(TAG, "Melanjutkan download dari byte $existingBytes")
                }
            }

            conn.connect()

            val responseCode = conn.responseCode
            val isResume = responseCode == 206   // Partial Content
            val isFresh  = responseCode == 200

            if (!isResume && !isFresh) {
                onError("Server error: HTTP $responseCode")
                return@withContext
            }

            val contentLength  = conn.contentLengthLong
            val totalBytes     = if (isResume) existingBytes + contentLength else contentLength
            val alreadyWritten = if (isResume) existingBytes else 0L

            Log.d(TAG, "Total size: ${totalBytes / 1_000_000} MB, resume: $isResume")

            conn.inputStream.use { input ->
                val mode = if (isResume) "rw" else "rw"  // overwrite kalau fresh
                if (!isResume) dest.delete()
                dest.parentFile?.mkdirs()

                java.io.RandomAccessFile(dest, mode).use { raf ->
                    if (isResume) raf.seek(alreadyWritten)

                    val buffer = ByteArray(256 * 1024)  // 256 KB buffer
                    var downloaded = alreadyWritten
                    var bytes = input.read(buffer)

                    while (bytes >= 0) {
                        raf.write(buffer, 0, bytes)
                        downloaded += bytes

                        if (totalBytes > 0) {
                            val dlMB    = (downloaded / 1_000_000).toInt()
                            val totalMB = (totalBytes / 1_000_000).toInt()
                            val pct     = ((downloaded * 100) / totalBytes).toInt()
                            onProgress(dlMB, totalMB, pct)
                        }

                        bytes = input.read(buffer)
                    }
                }
            }

            if (isModelReady(context)) {
                Log.d(TAG, "Download selesai: ${dest.length() / 1_000_000} MB")
                onDone()
            } else {
                onError("File tidak lengkap setelah download (${dest.length()} bytes)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            onError(e.message ?: "Koneksi gagal")
        }
    }

    /** Hapus file partial agar bisa download ulang dari awal */
    fun deletePartial(context: Context) {
        getModelFile(context).delete()
    }
}
