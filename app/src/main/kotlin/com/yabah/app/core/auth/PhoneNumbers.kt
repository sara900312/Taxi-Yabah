package com.yabah.app.core.auth

object PhoneNumbers {
    private val iraqiMobile = Regex("^(07\\d{9}|\\+9647\\d{9})$") // same rule as the Flutter client

    /** Converts Arabic-Indic / Persian digits and strips separators. */
    fun clean(input: String): String = buildString {
        for (ch in input.trim()) {
            when (ch) {
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                in '۰'..'۹' -> append('0' + (ch - '۰'))
                in '0'..'9', '+' -> append(ch)
                else -> Unit
            }
        }
    }

    /** Returns E.164 (+9647XXXXXXXXX) for a valid Iraqi mobile number, else null. */
    fun toE164(input: String): String? {
        var c = clean(input)
        if (c.startsWith("00964")) c = "+" + c.drop(2)
        if (c.startsWith("964")) c = "+$c"
        if (c.length == 10 && c.startsWith("7")) c = "0$c"
        if (!iraqiMobile.matches(c)) return null
        return if (c.startsWith("07")) "+964" + c.drop(1) else c
    }

    /** 07XX XXX XXXX for display. */
    fun display(e164: String): String {
        val local = if (e164.startsWith("+964")) "0" + e164.drop(4) else e164
        return if (local.length == 11) "${local.take(4)} ${local.substring(4, 7)} ${local.substring(7)}" else local
    }

    fun normalizeOtp(input: String): String? = clean(input).filter { it.isDigit() }.takeIf { it.length == 6 }
}
