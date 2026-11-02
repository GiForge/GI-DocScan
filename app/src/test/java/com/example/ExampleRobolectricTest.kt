package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.OcrManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GI-DocScan", appName)
    }

    @Test
    fun `test launch MainActivity`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun `test ocr entity analysis on invoice text`() {
        val sampleText = """
            ACME DIGITAL SOLUTIONS
            INVOICE #INV-2026-0842
            Date: November 1, 2026
            BILLED TO: Global Tech Enterprises
            TOTAL DUE: $6,480.00
        """.trimIndent()

        val result = OcrManager.analyzeExtractedText(sampleText)
        assertEquals("Invoice", result.category)
        assertEquals("$6,480.00", result.detectedTotal)
        assertNotNull(result.detectedDate)
    }
}
