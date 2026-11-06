package com.skyage.app.data

/**
 * Splits a typed flight number into its airline code and numeric part.
 *
 * IATA airline codes are exactly two characters and may contain a digit — JetBlue is B6, IndiGo 6E,
 * easyJet U2 — so the split is positional rather than "leading letters, then digits". Anything that
 * is not two alphanumerics followed by one to four digits is rejected.
 */
internal fun splitFlightNumber(raw: String): Pair<String, String>? {
    val compact = raw.trim().uppercase().replace(" ", "").replace("-", "")
    if (compact.length < 3) return null
    val code = compact.take(2)
    if (!code.all { it.isLetterOrDigit() }) return null
    val number = compact.substring(2).takeWhile { it.isDigit() }
    if (number.isEmpty() || number.length > 4) return null
    return code to number
}
