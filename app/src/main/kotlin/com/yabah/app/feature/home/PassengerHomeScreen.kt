package com.yabah.app.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yabah.app.LocalAppContainer
import com.yabah.app.R
import com.yabah.app.core.geo.Geo
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.message
import com.yabah.app.core.util.Format
import com.yabah.app.core.util.UiText
import com.yabah.app.data.places.Place
import com.yabah.app.data.repo.DemoRideRepository
import com.yabah.app.map.MapCameraState
import com.yabah.app.map.MapMarker
import com.yabah.app.map.MapRoute
import com.yabah.app.map.MarkerKind
import com.yabah.app.map.RouteStyle
import com.yabah.app.map.ServiceOverlay
import com.yabah.app.map.YabahMap
import com.yabah.app.ui.components.BadgeTone
import com.yabah.app.ui.components.DemoChip
import com.yabah.app.ui.components.LocationRow
import com.yabah.app.ui.components.MessageCard
import com.yabah.app.ui.components.SheetValue
import com.yabah.app.ui.components.StatusBadge
import com.yabah.app.ui.components.YabahBottomSheetLayout
import com.yabah.app.ui.components.YabahPrimaryButton
import com.yabah.app.ui.components.YabahSecondaryButton
import com.yabah.app.ui.components.rememberYabahSheetState
import com.yabah.app.ui.theme.Spacing
import com.yabah.app.ui.theme.YabahColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PassengerHomeScreen(
    vm: PassengerHomeViewModel,
    onOpenTrip: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSessionExpired: () -> Unit,
    nowProvider: () -> Long = System::currentTimeMillis,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val camera = remember { MapCameraState(DemoRideRepository.DEMO_BAGHDAD.center, 12.5) }
    val sheet = rememberYabahSheetState(SheetValue.Half)
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val arabic = locale.language == "ar"

    // One-shot camera commands from the view model.
    LaunchedEffect(state.camera?.id) {
        when (val c = state.camera) {
            is CameraCommand.MoveTo -> camera.animateTo(c.point, c.zoom ?: camera.zoom)
            is CameraCommand.Fit -> camera.fitBounds(c.points)
            null -> Unit
        }
    }
    // Camera idle → selection. Keyed once; recomposition never re-triggers it.
    LaunchedEffect(camera) {
        snapshotFlow { camera.center to camera.isGesturing }.collectLatest { (center, gesturing) ->
            if (!gesturing) { delay(350); vm.onCameraIdle(center) }
        }
    }
    LaunchedEffect(state.stage) { vm.onCameraIdle(camera.center) }
    LaunchedEffect(state.openTripId) { state.openTripId?.let { onOpenTrip(it); vm.resetAfterBooking() } }
    LaunchedEffect(state.sessionExpired) { if (state.sessionExpired) onSessionExpired() }
    LifecycleResumeEffect(Unit) { vm.onResume(); onPauseOrDispose { } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.onLocationPermissionResult(res.values.any { it })
    }
    val onLocate = {
        if (container.location.hasPermission()) vm.locate()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val city = state.city
    val overlay = city?.let { ServiceOverlay(container.serviceAreas.outline(it.code), it.center, it.serviceRadiusM) }
    val markers = buildList {
        if (state.stage != Stage.Pickup) state.pickup?.let { add(MapMarker(it.point, MarkerKind.Pickup)) }
        if (state.stage != Stage.Dropoff) state.dropoff?.let { add(MapMarker(it.point, MarkerKind.Dropoff)) }
    }
    val route = when (val r = state.route) {
        is RouteUi.Ready -> MapRoute(r.route.points, RouteStyle.ServerRoad)
        is RouteUi.DemoOnly -> listOfNotNull(state.pickup?.point, state.dropoff?.point).takeIf { it.size == 2 }?.let { MapRoute(it, RouteStyle.DemoStraight) }
        else -> null
    }
    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val statusTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val headerHeight = statusTop + 104.dp

    YabahBottomSheetLayout(
        state = sheet,
        peekHeight = 196.dp + navBottom,
        expandedTopInset = statusTop + 56.dp,
        modifier = Modifier.testTag("home_screen"),
        background = { visible ->
            val mapBottom = min(visible, maxHeight * 0.52f)
            YabahMap(
                camera = camera,
                markers = markers,
                route = route,
                overlay = overlay,
                places = container.places,
                contentPadding = PaddingValues(top = headerHeight, bottom = mapBottom),
                onTap = vm::onMapTap,
                publicMapToken = state.mapToken,
            )
            if (state.stage != Stage.Review) {
                CenterPin(
                    kind = if (state.stage == Stage.Pickup) MarkerKind.Pickup else MarkerKind.Dropoff,
                    lifted = camera.isGesturing,
                    modifier = Modifier.fillMaxSize().padding(top = headerHeight, bottom = mapBottom),
                )
            }
            HomeHeader(state.isDemo, simplifiedMap = state.mapToken == null || !com.yabah.app.map.NativeMap.isAvailable, state.online,
                city?.let { if (arabic) it.nameAr else it.nameEn }, onOpenSettings)
            AnimatedVisibility(
                visible = sheet.currentValue != SheetValue.Expanded,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.lg, bottom = mapBottom + Spacing.md),
            ) {
                val desc = stringResource(R.string.my_location)
                SmallFloatingActionButton(
                    onClick = onLocate,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = desc }.testTag("locate_button"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    if (state.locate == LocateUi.Locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_my_location), null)
                }
            }
        },
    ) {
        val focus = LocalFocusManager.current
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding().padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.stage == Stage.Pickup && state.pickup == null) {
                Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.home_question), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
            }
            val pickupValue = if (state.stage == Stage.Pickup) state.pin else state.pickup
            val dropoffValue = if (state.stage == Stage.Dropoff) state.pin else state.dropoff
            LocationRow(
                label = stringResource(R.string.pickup_label),
                value = pickupValue?.let { pointLabel(it, arabic) },
                placeholder = stringResource(R.string.pickup_hint),
                dotColor = YabahColors.Jade,
                active = state.stage == Stage.Pickup,
                onClick = if (state.stage != Stage.Pickup) vm::editPickup else null,
                modifier = Modifier.testTag("pickup_row"),
                trailing = { if (state.stage != Stage.Pickup && state.pickup != null) EditIcon() },
            )
            LocationRow(
                label = stringResource(R.string.dropoff_label),
                value = dropoffValue?.let { pointLabel(it, arabic) },
                placeholder = stringResource(R.string.dropoff_hint),
                dotColor = YabahColors.Gold,
                active = state.stage == Stage.Dropoff,
                onClick = if (state.stage == Stage.Review || (state.stage == Stage.Pickup && state.dropoff != null)) vm::editDropoff else null,
                modifier = Modifier.testTag("dropoff_row"),
                trailing = { if (state.stage == Stage.Review) EditIcon() },
            )

            state.cityFailure?.let { MessageCard(it.message(), BadgeTone.Danger, actionLabel = stringResource(R.string.retry), onAction = vm::loadCity) }

            when (state.stage) {
                Stage.Pickup, Stage.Dropoff -> PickingSection(
                    state = state,
                    arabic = arabic,
                    onQuery = vm::onSearchQuery,
                    onPlace = { focus.clearFocus(); vm.onPlaceChosen(it); scope.launch { sheet.animateTo(SheetValue.Half) } },
                    onSearchFocused = { scope.launch { sheet.animateTo(SheetValue.Expanded) } },
                    onConfirm = { focus.clearFocus(); vm.confirmPin() },
                    onDismissLocate = vm::dismissLocateMessage,
                )
                Stage.Review -> ReviewSection(state, vm, nowProvider)
            }
            if (state.isDemo) MessageCard(UiText.Res(R.string.demo_banner), BadgeTone.Demo, Modifier.testTag("demo_notice"))
        }
    }
}

@Composable
private fun pointLabel(sel: PointSelection, arabic: Boolean): String =
    sel.near?.let { stringResource(R.string.near_place, it.displayName(arabic)) } ?: stringResource(R.string.map_point)

@Composable
private fun EditIcon() = Icon(Icons.Filled.Edit, stringResource(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))

@Composable
private fun HomeHeader(isDemo: Boolean, simplifiedMap: Boolean, online: Boolean, cityName: String?, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_yabah_mark), null, Modifier.size(36.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    if (cityName != null) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text("· $cityName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings_button")) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.settings_desc))
                }
            }
        }
        if (isDemo || simplifiedMap || !online) {
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (isDemo) DemoChip(Modifier.testTag("demo_chip"))
                if (!online) StatusBadge(stringResource(R.string.offline_badge), BadgeTone.Danger, Modifier.testTag("offline_badge"))
                if (simplifiedMap) StatusBadge(stringResource(R.string.demo_map_notice), BadgeTone.Info, Modifier.testTag("simplified_map_badge"))
            }
        }
    }
}

/** Selection pin whose tip marks the exact centre of the visible map area. */
@Composable
private fun CenterPin(kind: MarkerKind, lifted: Boolean, modifier: Modifier) {
    val lift by animateDpAsState(if (lifted) 10.dp else 0.dp, label = "pinLift")
    val desc = stringResource(if (kind == MarkerKind.Pickup) R.string.pin_pickup_desc else R.string.pin_dropoff_desc)
    val color = if (kind == MarkerKind.Pickup) YabahColors.Jade else YabahColors.Gold
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(8.dp, 4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)))
        Canvas(
            Modifier.size(34.dp, 46.dp).offset(y = -(23.dp + lift)).testTag("center_pin").semantics { contentDescription = desc },
        ) {
            val s = size.width / 14f // pin path in a 14x20 box, tip at bottom centre
            val path = Path().apply {
                moveTo(7f * s, 20f * s)
                cubicTo(7f * s, 20f * s, 0f, 12.2f * s, 0f, 7f * s)
                cubicTo(0f, 3.13f * s, 3.13f * s, 0f, 7f * s, 0f)
                cubicTo(10.87f * s, 0f, 14f * s, 3.13f * s, 14f * s, 7f * s)
                cubicTo(14f * s, 12.2f * s, 7f * s, 20f * s, 7f * s, 20f * s)
                close()
            }
            drawPath(path, color)
            drawPath(path, Color.White, style = Stroke(1.2f * s))
            drawCircle(Color.White, 2.6f * s, Offset(7f * s, 7f * s))
            drawCircle(YabahColors.Navy, 1.1f * s, Offset(7f * s, 7f * s))
        }
    }
}

@Composable
private fun PickingSection(
    state: HomeUiState,
    arabic: Boolean,
    onQuery: (String) -> Unit,
    onPlace: (Place) -> Unit,
    onSearchFocused: () -> Unit,
    onConfirm: () -> Unit,
    onDismissLocate: () -> Unit,
) {
    Text(stringResource(R.string.move_map_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("search_field").onFocusChanged { if (it.isFocused) onSearchFocused() },
        placeholder = { Text(stringResource(R.string.search_places)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (state.searchQuery.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.close)) }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { state.searchResults.firstOrNull()?.let(onPlace) }),
    )
    if (state.searchQuery.trim().length >= 2) {
        if (state.searchResults.isEmpty()) {
            Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)) {
                state.searchResults.forEachIndexed { i, place ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onPlace(place) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("search_result"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(place.displayName(arabic), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val other = (if (arabic) place.nameEn else place.nameAr)?.takeIf { it.isNotBlank() && it != place.displayName(arabic) }
                            // Distance from the city centre tells apart different places that share a name.
                            val fromCentre = state.city?.let {
                                stringResource(R.string.km_from_centre, Format.km(Geo.metersBetween(it.center, place.point).toInt(), LocalConfiguration.current.locales[0]))
                            }
                            val secondary = listOfNotNull(other, fromCentre).joinToString(" · ")
                            if (secondary.isNotEmpty()) {
                                Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.search_source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when (val l = state.locate) {
        LocateUi.Denied -> MessageCard(UiText.Res(R.string.location_permission_denied), BadgeTone.Warn, actionLabel = stringResource(R.string.close), onAction = onDismissLocate)
        LocateUi.Unavailable -> MessageCard(UiText.Res(R.string.location_unavailable), BadgeTone.Warn, actionLabel = stringResource(R.string.close), onAction = onDismissLocate)
        is LocateUi.Approximate -> MessageCard(UiText.Res(R.string.location_inaccurate, listOf(l.accuracyM)), BadgeTone.Warn)
        else -> Unit
    }
    if (state.pinOutsideArea) {
        MessageCard(UiText.Res(R.string.outside_area_local), BadgeTone.Warn, Modifier.testTag("outside_area"))
    }
    YabahPrimaryButton(
        text = stringResource(if (state.stage == Stage.Pickup) R.string.confirm_pickup else R.string.confirm_dropoff),
        onClick = onConfirm,
        enabled = state.pin != null,
        modifier = Modifier.testTag("confirm_button"),
    )
}

@Composable
private fun ColumnScope.ReviewSection(state: HomeUiState, vm: PassengerHomeViewModel, nowProvider: () -> Long) {
    val locale = LocalConfiguration.current.locales[0]
    when (val r = state.route) {
        RouteUi.Loading, RouteUi.Idle -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("route_loading")) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.route_loading), style = MaterialTheme.typography.bodyMedium)
        }
        is RouteUi.Ready -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("route_ready")) {
            StatusBadge(stringResource(R.string.route_from_server), BadgeTone.Success)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.distance_km, Format.km(r.route.distanceM, locale)) + " · " +
                    stringResource(R.string.duration_min, Format.minutes(r.route.durationS)),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        is RouteUi.Failed -> MessageCard(r.failure.message(), BadgeTone.Danger, Modifier.testTag("route_error"),
            actionLabel = stringResource(R.string.retry), onAction = vm::retryRoute)
        is RouteUi.DemoOnly -> {
            MessageCard(UiText.Res(R.string.demo_route_unavailable), BadgeTone.Demo, Modifier.testTag("demo_route_notice"))
            Text(stringResource(R.string.straight_distance, stringResource(R.string.distance_km, Format.km(r.straightMeters, locale))) +
                " · " + stringResource(R.string.demo_straight_line),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (state.isDemo) {
        Text(stringResource(R.string.ride_classes_demo_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val arabic = locale.language == "ar"
        DemoRideRepository.DEMO_CLASSES.forEach { c ->
            RideOptionCard(if (arabic) c.nameAr else c.nameEn, c.maxPassengers, price = null, selected = c.code == "taxi",
                priceNote = stringResource(R.string.demo_price_placeholder))
        }
        YabahPrimaryButton(stringResource(R.string.demo_cannot_book), onClick = {}, enabled = false, modifier = Modifier.testTag("request_button"))
        return
    }

    val q = state.quote
    val option = (q as? QuoteUi.Ready)?.quote?.option
    RideOptionCard(
        name = option?.nameAr ?: stringResource(R.string.ride_taxi_fallback),
        seats = option?.maxPassengers,
        price = (q as? QuoteUi.Ready)?.quote?.displayFareIqd,
        selected = true,
        priceNote = if (q is QuoteUi.Ready) stringResource(R.string.server_price_note) else null,
    )
    when (q) {
        QuoteUi.Idle -> YabahSecondaryButton(stringResource(R.string.show_price), onClick = vm::requestQuote,
            enabled = state.route is RouteUi.Ready, modifier = Modifier.testTag("quote_button"))
        QuoteUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.price_loading), style = MaterialTheme.typography.bodyMedium)
        }
        is QuoteUi.Ready -> QuoteCountdown(q.quote.expiresAtLocalMs, nowProvider)
        is QuoteUi.Expired -> MessageCard(UiText.Res(R.string.price_expired), BadgeTone.Warn, Modifier.testTag("quote_expired"),
            actionLabel = stringResource(R.string.refresh_price), onAction = vm::requestQuote)
        is QuoteUi.Failed -> MessageCard(q.failure.message(), BadgeTone.Danger, Modifier.testTag("quote_error"),
            actionLabel = stringResource(R.string.retry), onAction = vm::requestQuote)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(stringResource(R.string.payment_cash), BadgeTone.Info)
    }
    when (val b = state.booking) {
        BookingUi.Reconciling -> MessageCard(UiText.Res(R.string.err_uncertain_booking), BadgeTone.Warn)
        BookingUi.Unresolved -> MessageCard(UiText.Res(R.string.err_uncertain_unresolved), BadgeTone.Danger, Modifier.testTag("booking_unresolved"),
            actionLabel = stringResource(R.string.check_again), onAction = { vm.reconcile() })
        is BookingUi.Failed -> MessageCard(b.failure.message(), BadgeTone.Danger, Modifier.testTag("booking_error"))
        else -> Unit
    }
    YabahPrimaryButton(
        text = stringResource(if (state.booking == BookingUi.Submitting) R.string.requesting else R.string.request_ride),
        onClick = vm::requestTrip,
        enabled = state.canRequestTrip,
        loading = state.booking == BookingUi.Submitting || state.booking == BookingUi.Reconciling,
        modifier = Modifier.testTag("request_button"),
    )
}

@Composable
private fun QuoteCountdown(expiresAtLocalMs: Long, nowProvider: () -> Long) {
    var now by remember { mutableLongStateOf(nowProvider()) }
    LaunchedEffect(expiresAtLocalMs) {
        while (now < expiresAtLocalMs) { delay(1_000); now = nowProvider() }
    }
    val locale = LocalConfiguration.current.locales[0]
    val left = ((expiresAtLocalMs - now) / 1000).coerceAtLeast(0)
    Text(stringResource(R.string.price_expires_in, Format.mmss(left, locale)), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("quote_countdown"))
}

@Composable
private fun RideOptionCard(name: String, seats: Int?, price: Long?, selected: Boolean, priceNote: String?) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_car), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (seats != null) Text(stringResource(R.string.ride_class_seats, seats), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (price != null) Text(stringResource(R.string.currency_iqd, Format.iqd(price, locale)), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("fare_amount"))
            if (priceNote != null) Text(priceNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
