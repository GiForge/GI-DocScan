package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "scanned_documents")
data class ScannedDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val imagePath: String, // Primary page / thumbnail path
    val pagesJson: String = "", // JSON array of all page image file paths
    val rawText: String = "",
    val category: String = "Document",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val summary: String = "",
    val keyEntities: String = "",
    val filterApplied: String = "ORIGINAL",
    val pageCount: Int = 1
) {
    fun getAllPagePaths(): List<String> {
        if (pagesJson.isNotBlank()) {
            try {
                val array = JSONArray(pagesJson)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val p = array.getString(i)
                    if (p.isNotBlank()) list.add(p)
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return if (imagePath.isNotBlank()) listOf(imagePath) else emptyList()
    }

    companion object {
        fun encodePages(paths: List<String>): String {
            val array = JSONArray()
            paths.forEach { array.put(it) }
            return array.toString()
        }
    }
}
