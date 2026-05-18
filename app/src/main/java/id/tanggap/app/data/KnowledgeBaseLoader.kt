package id.tanggap.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray

object KnowledgeBaseLoader {
    fun load(context: Context): List<Chunk> {
        return try {
            val json = context.assets.open("knowledge_base.json")
                .bufferedReader().readText()

            val arr = JSONArray(json)

            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                Chunk(
                    chunkId      = obj.getString("chunk_id"),
                    disasterType = obj.getString("disaster_type"),
                    source       = obj.getString("source"),
                    topic        = obj.optString("topic", ""),
                    text         = obj.getString("text")
                )
            }
        } catch (e: Exception) {
            Log.e("KnowledgeBase", "Gagal load knowledge base: ${e.message}")
            emptyList()
        }
    }
}
