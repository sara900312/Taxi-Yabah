package com.yabah.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yabah.app.LocalAppContainer
import com.yabah.app.R
import com.yabah.app.core.auth.PhoneNumbers
import com.yabah.app.ui.components.KeyValueRow
import com.yabah.app.ui.components.SectionTitle
import com.yabah.app.ui.components.YabahSecondaryButton
import com.yabah.app.ui.theme.Spacing

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit, onCredits: () -> Unit, onSignOut: () -> Unit) {
    val c = LocalAppContainer.current
    val lang by c.prefs.language.collectAsStateWithLifecycle()
    val session by c.auth.session.collectAsStateWithLifecycle()
    val demo = session == null && c.prefs.demoMode
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(Spacing.lg).testTag("settings_screen"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }
        SectionTitle(stringResource(R.string.language))
        listOf(null to "System / النظام", "ar" to stringResource(R.string.lang_ar), "en" to stringResource(R.string.lang_en)).forEach { (tag, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).selectable(lang == tag, role = Role.RadioButton) { c.prefs.setLanguage(tag) }
                .testTag("lang_${tag ?: "system"}"), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = lang == tag, onClick = null)
                Text(label, Modifier.padding(start = Spacing.sm), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider()
        KeyValueRow(stringResource(R.string.mode_label), stringResource(if (demo) R.string.mode_demo else R.string.mode_live))
        KeyValueRow(stringResource(R.string.map_engine), stringResource(if (c.config.mapboxNative) R.string.map_engine_mapbox else R.string.map_engine_demo))
        session?.phone?.let { KeyValueRow(stringResource(R.string.phone_label), PhoneNumbers.display("+$it".replace("++", "+"))) }
        KeyValueRow("", stringResource(R.string.build_info, c.config.versionName))
        HorizontalDivider()
        Text(stringResource(R.string.data_credits), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(role = Role.Button, onClick = onCredits).padding(vertical = Spacing.sm))
        YabahSecondaryButton(stringResource(if (demo) R.string.exit_demo else R.string.sign_out), onClick = { onSignOut(); onSignedOut() },
            modifier = Modifier.testTag("signout_button"))
    }
}

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val text = androidx.compose.runtime.remember {
        listOf("licenses/DATA_CREDITS.txt", "licenses/IBM_PLEX_OFL.txt").joinToString("\n\n————\n\n") { p ->
            runCatching { ctx.assets.open(p).bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.data_credits), style = MaterialTheme.typography.titleLarge)
        }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
    }
}
