package com.yabah.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** Context that keeps the Activity (for launchers, dialogs, Back) but serves resources in the chosen language. */
private class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** In-app language override (Arabic-first, English). `null` follows the system / per-app language setting. */
@Composable
fun LocalizedApp(language: String?, content: @Composable () -> Unit) {
    if (language == null) { content(); return }
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val locale = remember(language) { Locale.forLanguageTag(if (language == "ar") "ar-IQ" else language) }
    val config = remember(baseConfig, locale) { Configuration(baseConfig).apply { setLocale(locale); setLayoutDirection(locale) } }
    val ctx = remember(base, config) { LocalizedContext(base, base.createConfigurationContext(config).resources) }
    val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        LocalContext provides ctx,
        LocalConfiguration provides config,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}
