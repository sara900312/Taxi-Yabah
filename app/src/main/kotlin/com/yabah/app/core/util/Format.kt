package com.yabah.app.core.util

import java.text.NumberFormat
import java.util.Locale

object Format {
    /** IQD amounts are integers; grouping follows the UI locale (Arabic-Indic digits in Arabic). */
    fun iqd(amount: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(amount)

    fun km(meters: Int, locale: Locale): String {
        val nf = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
        return nf.format(meters / 1000.0)
    }

    fun minutes(seconds: Int): Int = ((seconds + 59) / 60).coerceAtLeast(1)

    fun mmss(totalSeconds: Long, locale: Locale): String {
        val s = totalSeconds.coerceAtLeast(0)
        return String.format(locale, "%d:%02d", s / 60, s % 60)
    }
}
