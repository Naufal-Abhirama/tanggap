package id.tanggap.app.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MLKitVisionAnalyzer {

    companion object {
        private const val TAG = "MLKitVisionAnalyzer"
        private const val CONFIDENCE_THRESHOLD = 0.52f
        private const val MAX_LABELS = 8

        private val INJURY_KEYWORDS = listOf(
            "person", "human", "face", "hand", "arm", "leg",
            "finger", "foot", "head", "neck", "shoulder",
            "wound", "blood", "bandage", "skin", "body",
            "people", "man", "woman", "child", "patient"
        )

        private val FLOOD_KEYWORDS = listOf(
            "water", "flood", "river", "rain", "mud", "puddle",
            "boat", "vehicle", "roof", "sand", "wave", "current",
            "swimming", "lake", "stream", "wet", "submerged"
        )

        private val LANDSLIDE_KEYWORDS = listOf(
            "soil", "mud", "rock", "mountain", "hill", "slope",
            "debris", "cliff", "ground", "dirt", "rubble",
            "forest", "tree", "plant", "field", "road", "insect"
        )

        private val EARTHQUAKE_KEYWORDS = listOf(
            "ruins", "ruin", "building", "wall", "concrete",
            "brick", "collapse", "structure", "architecture",
            "construction", "column", "ceiling", "floor", "glass",
            "event", "wreckage", "debris"
        )

        private val DISASTER_RELEVANT_KEYWORDS = listOf(
            // Banjir
            "water", "flood", "river", "rain", "mud", "puddle",
            "boat", "wave", "current", "swimming", "wet", "submerged",
            // Gempa / keruntuhan
            "ruins", "ruin", "building", "wall", "concrete",
            "brick", "collapse", "structure", "wreckage", "debris",
            // Longsor
            "soil", "rock", "slope", "cliff", "rubble",
            // Cedera / manusia di situasi darurat
            "wound", "blood", "bandage", "patient",
            // Kebakaran / asap
            "fire", "smoke", "flame", "ash", "burn",
            // Umum
            "disaster", "emergency", "destruction", "damage", "broken"
        )

        // Keyword yang menunjukkan teks hint memang berkaitan dengan darurat/bencana.
        // Digunakan agar "halo" atau teks kosong tidak mem-bypass filter foto.
        private val EMERGENCY_HINT_KEYWORDS = listOf(
            // Indonesia
            "banjir", "gempa", "longsor", "terjebak", "luka", "cedera", "darah",
            "pingsan", "tolong", "darurat", "bahaya", "terbakar", "kebakaran",
            "reruntuhan", "tertimpa", "tidak sadar", "patah", "pendarahan",
            "evakuasi", "selamatkan", "bantuan", "korban",
            // English
            "flood", "earthquake", "landslide", "trapped", "injury", "hurt",
            "bleeding", "unconscious", "help", "emergency", "danger", "fire",
            "ruins", "rescue", "victim", "evacuation", "save"
        )
    }

    fun analyzeImage(bitmap: Bitmap): MLKitResult {
        val labels = mutableListOf<String>()
        val latch = CountDownLatch(1)
        var errorMsg: String? = null

        val image = InputImage.fromBitmap(bitmap, 0)
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
                .build()
        )

        labeler.process(image)
            .addOnSuccessListener { results ->
                results.take(MAX_LABELS).forEach { label ->
                    labels.add(label.text)
                    Log.d(TAG, "Detected: ${label.text} (${(label.confidence * 100).toInt()}%)")
                }
                latch.countDown()
            }
            .addOnFailureListener { e ->
                errorMsg = e.message
                Log.e(TAG, "ML Kit error: ${e.message}")
                latch.countDown()
            }

        latch.await(5, TimeUnit.SECONDS)

        return MLKitResult(
            labels = labels,
            hasResult = labels.isNotEmpty(),
            error = errorMsg
        )
    }

    fun isDisasterRelevant(labels: List<String>, userHint: String = ""): Boolean {
        // Cek hint dulu — hanya bypass filter jika hint mengandung kata darurat nyata
        if (userHint.isNotBlank()) {
            val hintLower = userHint.lowercase()
            if (EMERGENCY_HINT_KEYWORDS.any { hintLower.contains(it) }) return true
        }
        // Fallback: cek label MLKit
        val labelsLower = labels.map { it.lowercase() }
        return labelsLower.any { label ->
            DISASTER_RELEVANT_KEYWORDS.any { keyword -> label.contains(keyword) }
        }
    }

    fun detectContext(labels: List<String>, userHint: String = ""): VisionContext {
        if (!isDisasterRelevant(labels, userHint)) {
            Log.d(TAG, "detectContext: tidak ada label bencana → UNCLEAR")
            return VisionContext.UNCLEAR
        }
        val labelsLower = labels.map { it.lowercase() }
        val hasHuman = labelsLower.any { label ->
            INJURY_KEYWORDS.any { keyword -> label.contains(keyword) }
        }
        return if (hasHuman) VisionContext.INJURY else VisionContext.ENVIRONMENT
    }

    fun detectDisasterType(labels: List<String>): DetectedDisaster {
        val labelsLower = labels.map { it.lowercase() }

        var floodScore = 0
        var landslideScore = 0
        var earthquakeScore = 0

        labelsLower.forEach { label ->
            if (FLOOD_KEYWORDS.any { label.contains(it) }) floodScore++
            if (LANDSLIDE_KEYWORDS.any { label.contains(it) }) landslideScore++
            if (EARTHQUAKE_KEYWORDS.any { label.contains(it) }) earthquakeScore++
        }

        Log.d(TAG, "Disaster scores → Banjir:$floodScore Longsor:$landslideScore Gempa:$earthquakeScore")

        val maxScore = maxOf(floodScore, landslideScore, earthquakeScore)

        return when {
            maxScore == 0 -> DetectedDisaster.UNKNOWN
            floodScore == maxScore -> DetectedDisaster.FLOOD
            earthquakeScore == maxScore -> DetectedDisaster.EARTHQUAKE
            else -> DetectedDisaster.LANDSLIDE
        }
    }
}

enum class DetectedDisaster {
    FLOOD, LANDSLIDE, EARTHQUAKE, UNKNOWN
}

data class MLKitResult(
    val labels: List<String>,
    val hasResult: Boolean,
    val error: String? = null
) {
    fun toPromptDescription(): String {
        return if (hasResult) labels.joinToString(", ")
        else "unidentified objects in disaster scene"
    }
}
