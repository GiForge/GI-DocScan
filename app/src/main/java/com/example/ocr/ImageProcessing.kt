package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DocFilter(val displayName: String) {
    ORIGINAL("Original"),
    BW_DOCUMENT("B&W Clean"),
    GRAYSCALE("Grayscale"),
    MAGIC_COLOR("Magic Color")
}

object ImageProcessing {

    fun applyFilter(source: Bitmap, filter: DocFilter): Bitmap {
        return when (filter) {
            DocFilter.ORIGINAL -> source
            DocFilter.GRAYSCALE -> applyGrayscale(source)
            DocFilter.BW_DOCUMENT -> applyHighContrastBw(source)
            DocFilter.MAGIC_COLOR -> applyMagicColor(source)
        }
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyMagicColor(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix()
        // Boost contrast and vibrant color
        matrix.set(
            floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.2f, 0f, 0f, 10f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyHighContrastBw(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalLum = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            totalLum += (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        val avgLum = if (pixels.isNotEmpty()) (totalLum / pixels.size).toInt() else 128
        val threshold = (avgLum * 0.95).toInt().coerceIn(70, 200)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = if (lum > threshold) Color.WHITE else Color.BLACK
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    fun saveBitmapToStorage(context: Context, bitmap: Bitmap, customName: String? = null): String {
        val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = customName ?: "DOC_${timeStamp}.jpg"
        val file = File(docsDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun loadBitmapFromPath(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateSampleInvoiceBitmap(): Bitmap {
        val width = 800
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white paper background
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true }

        // Top Accent bar
        paint.color = Color.parseColor("#0284C7")
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, paint)

        // Header Title
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 42f
        paint.isFakeBoldText = true
        canvas.drawText("ACME DIGITAL SOLUTIONS", 60f, 90f, paint)

        paint.textSize = 24f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#475569")
        canvas.drawText("INVOICE #INV-2026-0842", 60f, 130f, paint)
        canvas.drawText("Date: November 1, 2026", 60f, 165f, paint)
        canvas.drawText("Due Date: November 15, 2026", 60f, 200f, paint)

        // Separator
        paint.color = Color.parseColor("#CBD5E1")
        paint.strokeWidth = 2f
        canvas.drawLine(60f, 230f, 740f, 230f, paint)

        // Bill to section
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("BILLED TO:", 60f, 275f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 22f
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Global Tech Enterprises Inc.", 60f, 310f, paint)
        canvas.drawText("100 Innovation Way, Suite 400", 60f, 340f, paint)
        canvas.drawText("accounts@globaltech.io", 60f, 370f, paint)

        // Table Header
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(60f, 420f, 740f, 470f, paint)

        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("DESCRIPTION", 80f, 452f, paint)
        canvas.drawText("QTY", 480f, 452f, paint)
        canvas.drawText("AMOUNT", 620f, 452f, paint)

        // Table Rows
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#334155")
        paint.textSize = 20f

        val items = listOf(
            Triple("Cloud Architecture Consultation", "10 hrs", "$1,500.00"),
            Triple("AI Logic Integration & Security", "1", "$2,400.00"),
            Triple("Automated OCR Pipeline Engine", "1", "$1,250.00"),
            Triple("Mobile Client Deployment & Support", "1", "$850.00")
        )

        var y = 510f
        for (item in items) {
            canvas.drawText(item.first, 80f, y, paint)
            canvas.drawText(item.second, 490f, y, paint)
            canvas.drawText(item.third, 620f, y, paint)
            paint.color = Color.parseColor("#E2E8F0")
            canvas.drawLine(60f, y + 15f, 740f, y + 15f, paint)
            paint.color = Color.parseColor("#334155")
            y += 50f
        }

        // Totals
        y += 20f
        paint.textSize = 22f
        canvas.drawText("Subtotal:", 480f, y, paint)
        canvas.drawText("$6,000.00", 620f, y, paint)

        y += 35f
        canvas.drawText("Tax (8%):", 480f, y, paint)
        canvas.drawText("$480.00", 620f, y, paint)

        y += 45f
        paint.textSize = 28f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#0284C7")
        canvas.drawText("TOTAL DUE:", 440f, y, paint)
        canvas.drawText("$6,480.00", 620f, y, paint)

        // Footer note
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 18f
        canvas.drawText("Payment Terms: Net 15 days via Wire Transfer or ACH.", 60f, 980f, paint)
        canvas.drawText("Thank you for your business!", 60f, 1010f, paint)

        return bitmap
    }
}
