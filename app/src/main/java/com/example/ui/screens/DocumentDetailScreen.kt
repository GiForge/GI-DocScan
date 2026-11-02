package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.ScannedDocument
import com.example.ocr.DocFilter
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimaryDark
import com.example.ui.theme.EmeraldAccent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    docId: Long,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val document = documents.firstOrNull { it.id == docId }
    val scanState by viewModel.scanState.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (document == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val pages = document.getAllPagePaths()
    if (selectedPageIndex >= pages.size) {
        selectedPageIndex = (pages.size - 1).coerceAtLeast(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pages.size} ${if (pages.size == 1) "Pagina" else "Pagine"} • ${document.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("btn_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Torna indietro"
                        )
                    }
                },
                actions = {
                    // Favorite Toggle
                    IconButton(
                        onClick = { viewModel.toggleFavorite(document) },
                        modifier = Modifier.testTag("btn_detail_fav")
                    ) {
                        Icon(
                            imageVector = if (document.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Preferiti",
                            tint = if (document.isFavorite) AmberAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Edit
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("btn_detail_edit")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica dettagli")
                    }

                    // Delete
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("btn_detail_delete")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Elimina Documento",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Export & Action Bar (PDF, JPG, Add Page)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Export PDF
                Button(
                    onClick = { viewModel.exportDocumentAsPdf(document) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("btn_export_pdf")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Esporta PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Export JPG
                Button(
                    onClick = { viewModel.exportDocumentAsJpg(document) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("btn_export_jpg")
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Esporta JPG",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Add Page
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.CameraScanner(targetDocId = document.id)) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_add_page_to_doc")
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Pagina", fontSize = 12.sp)
                }
            }

            // Tabs Row
            val tabs = listOf("Scansione (${pages.size})", "Testo OCR", "Smart AI")
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                1 -> Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                else -> Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> MultiPageImageTab(
                    document = document,
                    pages = pages,
                    selectedPageIndex = selectedPageIndex,
                    onSelectPage = { selectedPageIndex = it },
                    viewModel = viewModel
                )
                1 -> OcrTextTab(
                    document = document,
                    viewModel = viewModel
                )
                2 -> AiInsightsTab(
                    document = document,
                    viewModel = viewModel
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina Documento") },
            text = { Text("Sei sicuro di voler eliminare '${document.title}' e tutte le sue scansioni?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument(document)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    // Edit Document Dialog
    if (showEditDialog) {
        var editedTitle by remember { mutableStateOf(document.title) }
        var editedCategory by remember { mutableStateOf(document.category) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Modifica Documento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Titolo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editedCategory,
                        onValueChange = { editedCategory = it },
                        label = { Text("Categoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        viewModel.updateDocumentDetails(document, editedTitle, editedCategory, document.rawText)
                    }
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun MultiPageImageTab(
    document: ScannedDocument,
    pages: List<String>,
    selectedPageIndex: Int,
    onSelectPage: (Int) -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val currentPath = pages.getOrNull(selectedPageIndex) ?: document.imagePath
    val file = remember(currentPath) { File(currentPath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Multi-page Selector (if more than 1 page)
        if (pages.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pages) { idx, path ->
                    val isSel = idx == selectedPageIndex
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectPage(idx) }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = File(path)),
                            contentDescription = "Pagina ${idx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "P. ${idx + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Active Page Image View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (file.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(model = file),
                    contentDescription = "Scansione Pagina ${selectedPageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    text = "File non trovato.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhancement Filter Pills
        Text(
            text = "Filtro Pagina ${selectedPageIndex + 1}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DocFilter.entries.forEach { filter ->
                val isCurrent = document.filterApplied == filter.name
                OutlinedButton(
                    onClick = { viewModel.reapplyFilterToDocument(document, selectedPageIndex, filter) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = filter.displayName, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun OcrTextTab(
    document: ScannedDocument,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editableText by remember(document.rawText) { mutableStateOf(document.rawText) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Optional OCR Trigger Card if no text has been extracted yet
        if (document.rawText.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Riconoscimento OCR Opzionale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Il testo non è stato ancora estratto da queste pagine. Tocca per avviare l'OCR su tutte le pagine del documento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.runOptionalOcr(document) },
                        modifier = Modifier.testTag("btn_run_optional_ocr")
                    ) {
                        Text("Esegui OCR Adesso")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Stats Bar & Copy / Share / Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val words = document.rawText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val chars = document.rawText.length
            Text(
                text = "$words parole • $chars caratteri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Re-run OCR
                IconButton(
                    onClick = { viewModel.runOptionalOcr(document) },
                    modifier = Modifier.testTag("btn_rerun_ocr")
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Rifai OCR",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Copy
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Scanned Text", editableText))
                        Toast.makeText(context, "Copiato negli appunti!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("btn_copy_ocr")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copia testo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Share Text
                IconButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TITLE, document.title)
                            putExtra(Intent.EXTRA_TEXT, "${document.title}\n\n$editableText")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Condividi Testo Estratto"))
                    },
                    modifier = Modifier.testTag("btn_share_ocr_text")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Condividi Testo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Edit
                IconButton(
                    onClick = {
                        if (isEditing) {
                            viewModel.updateDocumentDetails(document, document.title, document.category, editableText)
                        }
                        isEditing = !isEditing
                    },
                    modifier = Modifier.testTag("btn_toggle_edit_text")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (isEditing) "Salva" else "Modifica",
                        tint = if (isEditing) EmeraldAccent else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editableText,
                onValueChange = { editableText = it },
                label = { Text("Modifica Testo Estratto") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    isEditing = false
                    viewModel.updateDocumentDetails(document, document.title, document.category, editableText)
                    Toast.makeText(context, "Testo salvato!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salva Modifiche")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                if (document.rawText.isBlank()) {
                    Text(
                        text = "Nessun testo estratto. Premi 'Esegui OCR Adesso' in alto per estrarre il testo dalle scansioni.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = document.rawText,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AiInsightsTab(
    document: ScannedDocument,
    viewModel: MainViewModel
) {
    val aiState by viewModel.aiActionState.collectAsState()
    var questionInput by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf("Italiano") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Extracted Key Entities Card
        if (document.keyEntities.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanPrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Dati Chiave Rilevati",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                document.keyEntities.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split(":", limit = 2)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = parts.firstOrNull()?.trim() ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = parts.getOrNull(1)?.trim() ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // AI Summary Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Riassunto Esecutivo AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = { viewModel.requestAiSummary(document) },
                    enabled = !aiState.isRunning
                ) {
                    Text("Rigenera")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = document.summary.ifBlank { "Tocca rigenera per ottenere un riassunto conciso del documento." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }

        // Translation Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = AmberAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Traduzione Documento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val languages = listOf("Italiano", "Inglese", "Spagnolo", "Francese", "Tedesco")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                languages.forEach { lang ->
                    val isSel = lang == selectedLang
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .clickable { selectedLang = lang }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = lang,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.requestTranslation(document, selectedLang) },
                enabled = !aiState.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Traduci Documento in $selectedLang")
            }
        }

        // Q&A with Document
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Text(
                text = "Fai una domanda sul documento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = questionInput,
                    onValueChange = { questionInput = it },
                    placeholder = { Text("es. Qual è la data di scadenza?", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                IconButton(
                    onClick = {
                        if (questionInput.isNotBlank()) {
                            viewModel.askAiQuestion(document, questionInput)
                        }
                    },
                    enabled = !aiState.isRunning && questionInput.isNotBlank(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Invia domanda",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // AI Result Box
        if (aiState.isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    Text("Elaborazione AI in corso...")
                }
            }
        } else if (!aiState.result.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Risultato AI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    TextButton(onClick = { viewModel.clearAiActionState() }) {
                        Text("Chiudi", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = aiState.result ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 22.sp
                )
            }
        } else if (!aiState.error.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Errore: ${aiState.error}",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
