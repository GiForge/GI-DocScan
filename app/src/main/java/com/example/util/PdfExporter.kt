package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    /**
     * Generates a multi-page PDF file from a list of page image paths.
     * Dimensions: Standard A4 (595 x 842 points).
     */
    fun createPdfFromPages(context: Context, docTitle: String, pagePaths: List<String>): File? {
        if (pagePaths.isEmpty()) return null

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 24f

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            isDither = true
        }

        try {
            for ((index, path) in pagePaths.withIndex()) {
                val file = File(path)
                if (!file.exists()) continue

                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Fill white background for print clean finish
                canvas.drawColor(Color.WHITE)

                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                val bmpWidth = bitmap.width.toFloat()
                val bmpHeight = bitmap.height.toFloat()

                val scale = minOf(availableWidth / bmpWidth, availableHeight / bmpHeight)
                val scaledW = bmpWidth * scale
                val scaledH = bmpHeight * scale

                val left = margin + (availableWidth - scaledW) / 2
                val top = margin + (availableHeight - scaledH) / 2

                canvas.drawBitmap(bitmap, null, RectF(left, top, left + scaledW, top + scaledH), paint)
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
            val cleanTitle = docTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val pdfFile = File(docsDir, "${cleanTitle}_$timeStamp.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    fun sharePdfFile(context: Context, pdfFile: File, title: String) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF non trovato", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Condividi PDF ($title)"))
        } catch (e: Exception) {
            Toast.makeText(context, "Errore condivisione PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareJpgPages(context: Context, pagePaths: List<String>, title: String) {
        val validFiles = pagePaths.map { File(it) }.filter { it.exists() }
        if (validFiles.isEmpty()) {
            Toast.makeText(context, "Nessuna immagine trovata", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (validFiles.size == 1) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    validFiles.first()
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Condividi Immagine ($title)"))
            } else {
                val uris = ArrayList<Uri>()
                for (file in validFiles) {
                    uris.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
                }
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Condividi ${validFiles.size} Pagine JPG ($title)"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Errore condivisione JPG: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
