package com.meterreader.ocr

/**
 * Extracts the totalizer reading from Vision API OCR text.
 *
 * The Promag display shows values like "1227642.50 m³". The regex targets
 * numbers with 4+ integer digits and 1-2 decimal places, matching both
 * dot and comma decimal separators. The largest such value is the totalizer.
 */
object NumberExtractor {

    // Matches e.g. 1227642.50 or 1,227,642.50 or 1227642,50
    private val TOTALIZER_REGEX = Regex("""(\d[\d,]*\d[.,]\d{1,2})""")

    /**
     * Parses all candidate numbers from [ocrText] and returns the largest,
     * which corresponds to the cumulative totalizer value on the Promag display.
     * Returns null if no qualifying number is found.
     */
    fun extractTotalizer(ocrText: String?): Double? {
        if (ocrText.isNullOrBlank()) return null

        return TOTALIZER_REGEX.findAll(ocrText)
            .mapNotNull { match ->
                parseNumber(match.value)
            }
            .filter { it >= 1000.0 }   // ignore small numbers (flow rate etc.)
            .maxOrNull()
    }

    /**
     * Normalises a raw match (removes thousand-separators, converts comma
     * decimal separator to dot) and parses to Double.
     */
    fun parseNumber(raw: String): Double? {
        if (raw.isBlank()) return null
        return try {
            // Determine if the last separator is the decimal point
            val lastDot = raw.lastIndexOf('.')
            val lastComma = raw.lastIndexOf(',')

            val normalised = when {
                lastDot > lastComma -> {
                    // dot is decimal separator — strip commas (thousand sep)
                    raw.replace(",", "")
                }
                lastComma > lastDot -> {
                    // comma is decimal separator — strip dots (thousand sep), replace comma with dot
                    raw.replace(".", "").replace(",", ".")
                }
                else -> raw
            }
            normalised.toDouble()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
