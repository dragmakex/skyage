package com.skyage.app.data

/**
 * Splits a typed flight number into its airline code and numeric part.
 *
 * IATA airline codes are exactly two characters and may contain a digit — JetBlue is B6, IndiGo 6E,
 * easyJet U2 — so the split is positional rather than "leading letters, then digits". Anything that
 * is not two of A-Z/0-9 followed by one to four of 0-9 is rejected.
 *
 * Nothing outside ASCII is accepted: Char.isDigit is true for Arabic-Indic and full-width digits,
 * and a code built from those would look plausible and never match a callsign.
 */
internal fun splitFlightNumber(raw: String): Pair<String, String>? {
    val compact = raw.trim().uppercase().filterNot { it.isWhitespace() || it == '-' }
    if (compact.length < 3) return null
    val code = compact.take(2)
    if (!code.all { it in 'A'..'Z' || it in '0'..'9' }) return null
    val number = compact.substring(2)
    if (number.length > 4 || !number.all { it in '0'..'9' }) return null
    return code to number
}
