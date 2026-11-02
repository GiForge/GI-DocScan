package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DocAiService
import com.example.data.AppDatabase
import com.example.data.DocumentRepository
import com.example.data.ScannedDocument
import com.example.ocr.DocFilter
import com.example.ocr.ImageProcessing
import com.example.ocr.OcrManager
import com.example.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data class CameraScanner(val targetDocId: Long? = null) : Screen
    data class Orthorectify(
        val capturedBitmap: Bitmap,
        val targetDocId: Long? = null,
        val pageNumber: Int = 1
    ) : Screen
    data class ScanSession(val targetDocId: Long? = null) : Screen
    data class DocumentDetail(val docId: Long) : Screen
}

data class ScanProcessingState(
    val isProcessing: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null,
    val newlyScannedDocId: Long? = null
)

data class AiActionState(
    val isRunning: Boolean = false,
    val result: String? = null,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val context: Context get() = getApplication<Application>().applicationContext

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DocumentRepository(db.documentDao())
        removeSampleDocument()
    }

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _scanState = MutableStateFlow(ScanProcessingState())
    val scanState: StateFlow<ScanProcessingState> = _scanState.asStateFlow()

    private val _aiActionState = MutableStateFlow(AiActionState())
    val aiActionState: StateFlow<AiActionState> = _aiActionState.asStateFlow()

    // Multi-page scanning session state
    private val _sessionPagePaths = MutableStateFlow<List<String>>(emptyList())
    val sessionPagePaths: StateFlow<List<String>> = _sessionPagePaths.asStateFlow()

    private val _sessionOcrText = MutableStateFlow("")
    val sessionOcrText: StateFlow<String> = _sessionOcrText.asStateFlow()

    val documents: StateFlow<List<ScannedDocument>> = combine(
        repository.allDocuments,
        _searchQuery,
        _selectedCategory
    ) { docs, query, category ->
        docs.filter { doc ->
            val matchesCategory = when (category) {
                "All" -> true
                "Favorites" -> doc.isFavorite
                else -> doc.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                doc.title.contains(query, ignoreCase = true) ||
                        doc.rawText.contains(query, ignoreCase = true) ||
                        doc.summary.contains(query, ignoreCase = true) ||
                        doc.category.contains(query, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        _currentScreen.value = Screen.Home
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelect(category: String) {
        _selectedCategory.value = category
    }

    fun toggleFavorite(document: ScannedDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(document.id, document.isFavorite)
        }
    }

    fun deleteDocument(document: ScannedDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                document.getAllPagePaths().forEach { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Could not delete files: ${e.message}")
            }
            repository.deleteDocument(document)
            if (_currentScreen.value is Screen.DocumentDetail) {
                _currentScreen.value = Screen.Home
            }
        }
    }

    fun updateDocumentDetails(document: ScannedDocument, newTitle: String, newCategory: String, newText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = document.copy(
                title = newTitle.trim(),
                category = newCategory.trim(),
                rawText = newText
            )
            repository.updateDocument(updated)
        }
    }

    /**
     * Called immediately after a photo is captured or imported.
     * Routes directly to OrthorectifyScreen for semi-automatic corner correction.
     */
    fun onRawPhotoCaptured(bitmap: Bitmap, targetDocId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val pageNum = if (targetDocId != null) {
                val doc = repository.getDocumentById(targetDocId)
                (doc?.getAllPagePaths()?.size ?: doc?.pageCount ?: 1) + 1
            } else {
                _sessionPagePaths.value.size + 1
            }
            withContext(Dispatchers.Main) {
                _currentScreen.value = Screen.Orthorectify(
                    capturedBitmap = bitmap,
                    targetDocId = targetDocId,
                    pageNumber = pageNum
                )
            }
        }
    }

    fun processImportedUri(uri: Uri, targetDocId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanProcessingState(isProcessing = true, statusMessage = "Caricamento immagine...")
            val bitmap = ImageProcessing.loadBitmapFromUri(context, uri)
            _scanState.value = ScanProcessingState(isProcessing = false)
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    onRawPhotoCaptured(bitmap, targetDocId)
                }
            } else {
                _scanState.value = ScanProcessingState(isProcessing = false, error = "Impossibile aprire l'immagine selezionata")
            }
        }
    }

    /**
     * Called when the user confirms orthorectification in OrthorectifyScreen.
     * Applies enhancement filter, saves the straightened page to disk,
     * attaches any extracted OCR text, and appends to the session or to an existing document.
     */
    fun confirmRectifiedPage(
        rectifiedBitmap: Bitmap,
        filter: DocFilter,
        targetDocId: Long?,
        extractedOcrText: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanProcessingState(isProcessing = true, statusMessage = "Salvataggio pagina raddrizzata...")
            try {
                val enhancedBitmap = ImageProcessing.applyFilter(rectifiedBitmap, filter)
                val filePath = ImageProcessing.saveBitmapToStorage(context, enhancedBitmap)

                if (targetDocId != null) {
                    // Append page to existing document
                    val existing = repository.getDocumentById(targetDocId)
                    if (existing != null) {
                        val currentPages = existing.getAllPagePaths().toMutableList()
                        currentPages.add(filePath)
                        val updatedText = if (!extractedOcrText.isNullOrBlank()) {
                            if (existing.rawText.isBlank()) extractedOcrText else "${existing.rawText}\n\n$extractedOcrText"
                        } else existing.rawText

                        val updated = existing.copy(
                            pagesJson = ScannedDocument.encodePages(currentPages),
                            pageCount = currentPages.size,
                            rawText = updatedText
                        )
                        repository.updateDocument(updated)
                    }
                    _scanState.value = ScanProcessingState(isProcessing = false)
                    withContext(Dispatchers.Main) {
                        navigateTo(Screen.DocumentDetail(targetDocId))
                    }
                } else {
                    // Add to current scan session
                    val currentList = _sessionPagePaths.value.toMutableList()
                    currentList.add(filePath)
                    _sessionPagePaths.value = currentList

                    if (!extractedOcrText.isNullOrBlank()) {
                        _sessionOcrText.value = if (_sessionOcrText.value.isBlank()) {
                            extractedOcrText
                        } else {
                            "${_sessionOcrText.value}\n\n$extractedOcrText"
                        }
                    }

                    _scanState.value = ScanProcessingState(isProcessing = false)
                    withContext(Dispatchers.Main) {
                        navigateTo(Screen.ScanSession())
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving rectified page", e)
                _scanState.value = ScanProcessingState(isProcessing = false, error = e.localizedMessage)
            }
        }
    }

    fun removeSessionPage(index: Int) {
        val current = _sessionPagePaths.value.toMutableList()
        if (index in current.indices) {
            val path = current.removeAt(index)
            File(path).runCatching { delete() }
            _sessionPagePaths.value = current
        }
    }

    fun clearScanSession() {
        _sessionPagePaths.value = emptyList()
        _sessionOcrText.value = ""
        _currentScreen.value = Screen.Home
    }

    /**
     * Completes the multi-page scan session, saves ScannedDocument into Room DB,
     * with optional OCR execution.
     */
    fun saveScanSession(docTitle: String, category: String, runOcr: Boolean) {
        val pages = _sessionPagePaths.value
        if (pages.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanProcessingState(isProcessing = true, statusMessage = if (runOcr) "Esecuzione OCR sulle pagine..." else "Salvataggio documento...")
            try {
                var extractedText = _sessionOcrText.value
                var summary = ""
                var entitiesFormatted = ""
                var autoCategory = category

                if (runOcr && extractedText.isBlank()) {
                    val fullTextBuilder = StringBuilder()
                    for ((idx, path) in pages.withIndex()) {
                        val bmp = ImageProcessing.loadBitmapFromPath(path)
                        if (bmp != null) {
                            val ocr = OcrManager.recognizeTextFromBitmap(bmp)
                            if (pages.size > 1) {
                                fullTextBuilder.append("--- PAGINA ${idx + 1} ---\n")
                            }
                            fullTextBuilder.append(ocr.rawText).append("\n\n")

                            if (idx == 0) {
                                if (autoCategory == "Document" || autoCategory.isBlank()) {
                                    autoCategory = ocr.category
                                }
                                entitiesFormatted = ocr.keyEntities.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            }
                        }
                    }
                    extractedText = fullTextBuilder.toString().trim()
                }

                if (extractedText.isNotBlank()) {
                    summary = DocAiService.summarizeDocument(extractedText)
                }

                val finalTitle = docTitle.ifBlank {
                    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                    "Scansione $dateStr"
                }

                val doc = ScannedDocument(
                    title = finalTitle,
                    imagePath = pages.first(),
                    pagesJson = ScannedDocument.encodePages(pages),
                    rawText = extractedText,
                    category = autoCategory,
                    summary = summary,
                    keyEntities = entitiesFormatted,
                    filterApplied = DocFilter.BW_DOCUMENT.name,
                    pageCount = pages.size
                )

                val newId = repository.insertDocument(doc)
                _sessionPagePaths.value = emptyList()
                _scanState.value = ScanProcessingState(isProcessing = false, newlyScannedDocId = newId)

                withContext(Dispatchers.Main) {
                    navigateTo(Screen.DocumentDetail(newId))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error finishing scan session", e)
                _scanState.value = ScanProcessingState(isProcessing = false, error = e.localizedMessage)
            }
        }
    }

    /**
     * Optional OCR executed on an existing document upon user request.
     */
    fun runOptionalOcr(document: ScannedDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanProcessingState(isProcessing = true, statusMessage = "Analisi OCR in corso...")
            try {
                val pages = document.getAllPagePaths()
                val textBuilder = StringBuilder()
                var firstPageCategory = document.category
                var entitiesFormatted = document.keyEntities

                for ((idx, path) in pages.withIndex()) {
                    val bmp = ImageProcessing.loadBitmapFromPath(path)
                    if (bmp != null) {
                        val ocr = OcrManager.recognizeTextFromBitmap(bmp)
                        if (pages.size > 1) {
                            textBuilder.append("--- PAGINA ${idx + 1} ---\n")
                        }
                        textBuilder.append(ocr.rawText).append("\n\n")

                        if (idx == 0) {
                            firstPageCategory = ocr.category
                            entitiesFormatted = ocr.keyEntities.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        }
                    }
                }

                val fullText = textBuilder.toString().trim()
                val summary = if (fullText.isNotBlank()) DocAiService.summarizeDocument(fullText) else ""

                val updated = document.copy(
                    rawText = fullText,
                    category = firstPageCategory,
                    summary = summary,
                    keyEntities = entitiesFormatted
                )
                repository.updateDocument(updated)
                _scanState.value = ScanProcessingState(isProcessing = false)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error running OCR", e)
                _scanState.value = ScanProcessingState(isProcessing = false, error = e.localizedMessage)
            }
        }
    }

    fun exportDocumentAsPdf(document: ScannedDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanProcessingState(isProcessing = true, statusMessage = "Generazione PDF A4...")
            val pages = document.getAllPagePaths()
            val pdfFile = PdfExporter.createPdfFromPages(context, document.title, pages)
            _scanState.value = ScanProcessingState(isProcessing = false)
            if (pdfFile != null) {
                withContext(Dispatchers.Main) {
                    PdfExporter.sharePdfFile(context, pdfFile, document.title)
                }
            } else {
                _scanState.value = ScanProcessingState(isProcessing = false, error = "Errore durante la creazione del PDF")
            }
        }
    }

    fun exportDocumentAsJpg(document: ScannedDocument) {
        val pages = document.getAllPagePaths()
        PdfExporter.shareJpgPages(context, pages, document.title)
    }

    fun reapplyFilterToDocument(document: ScannedDocument, pageIndex: Int, newFilter: DocFilter) {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = document.getAllPagePaths().toMutableList()
            if (pageIndex in pages.indices) {
                val originalBitmap = ImageProcessing.loadBitmapFromPath(pages[pageIndex]) ?: return@launch
                val updatedBitmap = ImageProcessing.applyFilter(originalBitmap, newFilter)
                val newPath = ImageProcessing.saveBitmapToStorage(context, updatedBitmap)
                pages[pageIndex] = newPath

                val updated = document.copy(
                    imagePath = pages.first(),
                    pagesJson = ScannedDocument.encodePages(pages),
                    filterApplied = newFilter.name
                )
                repository.updateDocument(updated)
            }
        }
    }

    fun requestAiSummary(document: ScannedDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            _aiActionState.value = AiActionState(isRunning = true)
            try {
                val summary = DocAiService.summarizeDocument(document.rawText)
                repository.updateDocument(document.copy(summary = summary))
                _aiActionState.value = AiActionState(isRunning = false, result = summary)
            } catch (e: Exception) {
                _aiActionState.value = AiActionState(isRunning = false, error = e.localizedMessage)
            }
        }
    }

    fun requestTranslation(document: ScannedDocument, targetLang: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _aiActionState.value = AiActionState(isRunning = true)
            try {
                val translated = DocAiService.translateDocument(document.rawText, targetLang)
                _aiActionState.value = AiActionState(isRunning = false, result = translated)
            } catch (e: Exception) {
                _aiActionState.value = AiActionState(isRunning = false, error = e.localizedMessage)
            }
        }
    }

    fun askAiQuestion(document: ScannedDocument, question: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _aiActionState.value = AiActionState(isRunning = true)
            try {
                val answer = DocAiService.askDocumentQuestion(document.rawText, question)
                _aiActionState.value = AiActionState(isRunning = false, result = answer)
            } catch (e: Exception) {
                _aiActionState.value = AiActionState(isRunning = false, error = e.localizedMessage)
            }
        }
    }

    fun clearAiActionState() {
        _aiActionState.value = AiActionState()
    }

    fun resetScanState() {
        _scanState.value = ScanProcessingState()
    }

    private fun removeSampleDocument() {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = repository.getDocumentById(1L)
            if (doc != null && (doc.title.contains("ACME", ignoreCase = true) || doc.title.contains("Fattura", ignoreCase = true))) {
                repository.deleteDocument(doc)
            }
        }
    }
}
