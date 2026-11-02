package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM scanned_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<ScannedDocument>>

    @Query("SELECT * FROM scanned_documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): ScannedDocument?

    @Query("SELECT * FROM scanned_documents WHERE id = :id")
    fun getDocumentByIdFlow(id: Long): Flow<ScannedDocument?>

    @Query("SELECT * FROM scanned_documents WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteDocuments(): Flow<List<ScannedDocument>>

    @Query("SELECT * FROM scanned_documents WHERE category = :category ORDER BY createdAt DESC")
    fun getDocumentsByCategory(category: String): Flow<List<ScannedDocument>>

    @Query("""
        SELECT * FROM scanned_documents 
        WHERE title LIKE '%' || :query || '%' 
           OR rawText LIKE '%' || :query || '%' 
           OR summary LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<ScannedDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: ScannedDocument): Long

    @Update
    suspend fun updateDocument(document: ScannedDocument)

    @Delete
    suspend fun deleteDocument(document: ScannedDocument)

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE scanned_documents SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFav: Boolean)
}
