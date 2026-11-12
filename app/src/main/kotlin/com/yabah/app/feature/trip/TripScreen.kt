package com.yabah.app.feature.trip

import com.yabah.app.ui.findActivity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yabah.app.LocalAppContainer
import com.yabah.app.R
import com.yabah.app.core.model.TripDetails
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.net.message
import com.yabah.app.core.util.Format
import com.yabah.app.core.util.UiText
import com.yabah.app.map.MapCameraState
import com.yabah.app.map.MapMarker
import com.yabah.app.map.MarkerKind
import com.yabah.app.map.YabahMap
import com.yabah.app.ui.components.BadgeTone
import com.yabah.app.ui.components.KeyValueRow
import com.yabah.app.ui.components.MessageCard
import com.yabah.app.ui.components.StatusBadge
import com.yabah.app.ui.components.YabahPrimaryButton
import com.yabah.app.ui.theme.LocalYabahExtras
import com.yabah.app.ui.theme.Spacing
import com.yabah.app.ui.theme.YabahColors
import java.text.DateFormat
import java.util.Date

@Composable
fun TripScreen(vm: TripViewModel, onDone: () -> Unit, onSessionExpired: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = LocalAppContainer.current
    LifecycleStartEffect(vm) { vm.onVisible(); onStopOrDispose { vm.onHidden() } }
    LaunchedEffect(state.sessionExpired) { if (state.sessionExpired) onSessionExpired() }

    val d = state.details
    // While a trip is active this screen is the app's home: Back leaves the app instead of hiding the trip.
    BackHandler(enabled = d?.status?.isActive == true) { context.findActivity()?.moveTaskToBack(true) }

    val camera = remember { MapCameraState(com.yabah.app.data.repo.DemoRideRepository.DEMO_BAGHDAD.center, 12.0) }
    val markers = d?.let {
        buildList {
            add(MapMarker(it.pickup, MarkerKind.Pickup)); add(MapMarker(it.dropoff, MarkerKind.Dropoff))
            it.captainLocation?.let { c -> add(MapMarker(c.point, MarkerKind.Captain)) }
        }
    }.orEmpty()
    LaunchedEffect(d?.id) { d?.let { camera.fitBounds(listOf(it.pickup, it.dropoff)) } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("trip_screen")) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            YabahMap(camera, markers, null, null, container.places, PaddingValues(top = 48.dp), onTap = {}, publicMapToken = null)
            Surface(Modifier.statusBarsPadding().padding(Spacing.md), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Text(stringResource(R.string.app_name), Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (d == null) {
                if (state.loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.trip_searching), style = MaterialTheme.typography.bodyLarge)
                }
                state.failure?.let { MessageCard(it.message(), BadgeTone.Danger, actionLabel = stringResource(R.string.retry), onAction = vm::refresh) }
                return@Column
            }
            StatusHeader(d)
            Timeline(d.status)
            state.failure?.let { MessageCard(it.message(), BadgeTone.Warn, actionLabel = stringResource(R.string.retry), onAction = vm::refresh) }
            if (d.captain != null && d.status in setOf(TripStatus.Accepted, TripStatus.Arrived, TripStatus.InProgress)) {
                CaptainCard(d, state, onCall = { phone -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) })
            }
            FareBlock(d)
            if (d.status.passengerMayCancel) CancelButton(state.cancel == ActionUi.Running, vm::cancelTrip)
            (state.cancel as? ActionUi.Failed)?.let { MessageCard(it.failure.message(), BadgeTone.Danger) }
            if (d.status == TripStatus.Completed) RatingBlock(d, state, vm)
            if (!d.status.isActive) YabahPrimaryButton(stringResource(R.string.back_home), onDone, Modifier.testTag("back_home"))
            state.lastCheckedMs?.let {
                Text(stringResource(R.string.trip_last_checked, DateFormat.getTimeInstance(DateFormat.MEDIUM, LocalConfiguration.current.locales[0]).format(Date(it))),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusHeader(d: TripDetails) {
    val (title, note) = when (d.status) {
        TripStatus.Requested -> R.string.trip_searching to R.string.trip_searching_note
        TripStatus.Accepted -> R.string.trip_accepted to null
        TripStatus.Arrived -> R.string.trip_arrived to null
        TripStatus.InProgress -> R.string.trip_in_progress to null
        TripStatus.Completed -> R.string.trip_completed to null
        TripStatus.Cancelled, TripStatus.Unknown -> R.string.trip_cancelled to null
    }
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }.testTag("trip_status_title"))
    if (note != null) Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (d.status == TripStatus.Requested) LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
    if (d.status == TripStatus.Cancelled && !d.cancellationReason.isNullOrBlank()) {
        Text(d.cancellationReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Timeline(status: TripStatus) {
    val steps = listOf(
        TripStatus.Requested to R.string.tl_requested, TripStatus.Accepted to R.string.tl_accepted,
        TripStatus.Arrived to R.string.tl_arrived, TripStatus.InProgress to R.string.tl_in_progress,
        TripStatus.Completed to R.string.tl_completed,
    )
    val reached = steps.indexOfFirst { it.first == status }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        steps.forEachIndexed { i, (_, label) ->
            val done = status != TripStatus.Cancelled && i <= reached
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CaptainCard(d: TripDetails, state: TripUiState, onCall: (String) -> Unit) {
    val c = d.captain ?: return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.testTag("captain_card")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text((c.fullName ?: "؟").take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(c.fullName ?: "—", style = MaterialTheme.typography.titleMedium)
                    c.ratingAvg?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = YabahColors.Gold, modifier = Modifier.size(16.dp))
                            Text(String.format(LocalConfiguration.current.locales[0], " %.1f", r) + (c.ratingCount?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                c.phoneE164?.let { phone ->
                    val desc = stringResource(R.string.call_captain)
                    IconButton(onClick = { onCall(phone) }, modifier = Modifier.semantics { contentDescription = desc }) {
                        Icon(Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            d.vehicle?.let { v ->
                val desc = listOfNotNull(v.color, v.make, v.model, v.manufactureYear?.toString()).joinToString(" ")
                if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodyMedium)
                v.plateNumber?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(LocalYabahExtras.current.warnContainer).padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            when {
                state.captainLocationAgeMin == null -> StatusBadge(stringResource(R.string.captain_location_unknown), BadgeTone.Warn)
                state.captainLocationStale -> StatusBadge(stringResource(R.string.captain_location_stale, state.captainLocationAgeMin!!), BadgeTone.Warn,
                    Modifier.testTag("stale_location"))
                else -> StatusBadge(stringResource(R.string.captain_location_fresh), BadgeTone.Success)
            }
        }
    }
}

@Composable
private fun FareBlock(d: TripDetails) {
    val locale = LocalConfiguration.current.locales[0]
    Column {
        d.estimatedFareIqd?.let { KeyValueRow(stringResource(R.string.estimated_fare), stringResource(R.string.currency_iqd, Format.iqd(it, locale))) }
        (d.passengerPaysIqd ?: d.finalFareIqd)?.takeIf { d.status == TripStatus.Completed }?.let {
            KeyValueRow(stringResource(R.string.final_fare), stringResource(R.string.currency_iqd, Format.iqd(it, locale)), emphasize = true)
        }
        Spacer(Modifier.height(Spacing.xs))
        StatusBadge(stringResource(R.string.payment_cash), BadgeTone.Info)
    }
}

@Composable
private fun CancelButton(running: Boolean, onConfirm: () -> Unit) {
    var ask by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { ask = true }, enabled = !running, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("cancel_trip")) {
        Text(stringResource(R.string.cancel_trip), color = MaterialTheme.colorScheme.error)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(stringResource(R.string.cancel_confirm_title)) },
        confirmButton = { TextButton(onClick = { ask = false; onConfirm() }) { Text(stringResource(R.string.cancel_confirm_yes), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(stringResource(R.string.cancel_confirm_no)) } },
    )
}

@Composable
private fun RatingBlock(d: TripDetails, state: TripUiState, vm: TripViewModel) {
    if (d.myRatingSubmitted || state.rating == ActionUi.Done) {
        MessageCard(UiText.Res(R.string.rate_thanks), BadgeTone.Success); return
    }
    Text(stringResource(R.string.rate_trip), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (1..5).forEach { n ->
            val desc = stringResource(R.string.rate_star_desc, n)
            IconButton(onClick = { vm.setStars(n) }, modifier = Modifier.semantics { contentDescription = desc }.testTag("star_$n")) {
                Icon(Icons.Filled.Star, null, tint = if (n <= state.stars) YabahColors.Gold else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(32.dp))
            }
        }
    }
    (state.rating as? ActionUi.Failed)?.let { MessageCard(it.failure.message(), BadgeTone.Danger) }
    YabahPrimaryButton(stringResource(R.string.rate_submit), vm::submitRating, enabled = state.stars in 1..5, loading = state.rating == ActionUi.Running,
        modifier = Modifier.testTag("submit_rating"))
}
