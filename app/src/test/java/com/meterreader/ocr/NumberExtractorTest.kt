package com.meterreader.ocr

import org.junit.Assert.*
import org.junit.Test

class NumberExtractorTest {

    // ── extractTotalizer ─────────────────────────────────────────────────────

    @Test
    fun `returns null for null input`() {
        assertNull(NumberExtractor.extractTotalizer(null))
    }

    @Test
    fun `returns null for blank string`() {
        assertNull(NumberExtractor.extractTotalizer("   "))
    }

    @Test
    fun `extracts totalizer from typical Promag OCR output`() {
        val ocrText = """
            Promag
            67.74 m3/h
            1227642.50 m3
        """.trimIndent()
        val result = NumberExtractor.extractTotalizer(ocrText)
        assertEquals(1227642.50, result!!, 0.001)
    }

    @Test
    fun `picks largest number as totalizer`() {
        val ocrText = "Flow: 67.74 m3/h  Total: 9876543.21 m3"
        val result = NumberExtractor.extractTotalizer(ocrText)
        assertEquals(9876543.21, result!!, 0.001)
    }

    @Test
    fun `handles comma as decimal separator`() {
        val ocrText = "Total: 1234567,89 m3"
        val result = NumberExtractor.extractTotalizer(ocrText)
        assertEquals(1234567.89, result!!, 0.001)
    }

    @Test
    fun `handles dot-separated thousands with comma decimal`() {
        val ocrText = "1.234.567,89"
        val result = NumberExtractor.extractTotalizer(ocrText)
        assertEquals(1234567.89, result!!, 0.001)
    }

    @Test
    fun `filters out small flow rate numbers below 1000`() {
        val ocrText = "67.74 m3/h"
        assertNull(NumberExtractor.extractTotalizer(ocrText))
    }

    @Test
    fun `returns null when no numbers present`() {
        assertNull(NumberExtractor.extractTotalizer("Promag flow meter"))
    }

    @Test
    fun `handles OCR with extra whitespace and newlines`() {
        val ocrText = "\nPromag\n  67.74  m3/h\n  1227642.50  m3\n"
        val result = NumberExtractor.extractTotalizer(ocrText)
        assertEquals(1227642.50, result!!, 0.001)
    }

    @Test
    fun `returns null for strings with only values below 1000`() {
        val ocrText = "100.50 200.75 999.99"
        assertNull(NumberExtractor.extractTotalizer(ocrText))
    }

    // ── parseNumber ──────────────────────────────────────────────────────────

    @Test
    fun `parseNumber handles plain dot decimal`() {
        assertEquals(1234.56, NumberExtractor.parseNumber("1234.56")!!, 0.001)
    }

    @Test
    fun `parseNumber handles comma decimal`() {
        assertEquals(1234.56, NumberExtractor.parseNumber("1234,56")!!, 0.001)
    }

    @Test
    fun `parseNumber handles thousand-separated with dot decimal`() {
        assertEquals(1234567.89, NumberExtractor.parseNumber("1,234,567.89")!!, 0.001)
    }

    @Test
    fun `parseNumber handles European format`() {
        assertEquals(1234567.89, NumberExtractor.parseNumber("1.234.567,89")!!, 0.001)
    }

    @Test
    fun `parseNumber returns null for blank`() {
        assertNull(NumberExtractor.parseNumber(""))
    }

    @Test
    fun `parseNumber returns null for non-numeric`() {
        assertNull(NumberExtractor.parseNumber("abc"))
    }
}
