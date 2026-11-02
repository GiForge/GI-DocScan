package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val dao: DocumentDao) {
    val allDocuments: Flow<List<ScannedDocument>> = dao.getAllDocuments()
    val favoriteDocuments: Flow<List<ScannedDocument>> = dao.getFavoriteDocuments()

    fun getDocumentByIdFlow(id: Long): Flow<ScannedDocument?> = dao.getDocumentByIdFlow(id)

    suspend fun getDocumentById(id: Long): ScannedDocument? = dao.getDocumentById(id)

    fun searchDocuments(query: String): Flow<List<ScannedDocument>> = dao.searchDocuments(query)

    fun getDocumentsByCategory(category: String): Flow<List<ScannedDocument>> =
        dao.getDocumentsByCategory(category)

    suspend fun insertDocument(document: ScannedDocument): Long = dao.insertDocument(document)

    suspend fun updateDocument(document: ScannedDocument) = dao.updateDocument(document)

    suspend fun deleteDocument(document: ScannedDocument) = dao.deleteDocument(document)

    suspend fun deleteDocumentById(id: Long) = dao.deleteDocumentById(id)

    suspend fun toggleFavorite(id: Long, current: Boolean) = dao.updateFavorite(id, !current)
}
