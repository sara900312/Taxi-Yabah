package com.yabah.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yabah.app.core.geo.Geo
import com.yabah.app.core.geo.ServiceAreaRule
import com.yabah.app.core.location.LocationClient
import com.yabah.app.core.model.City
import com.yabah.app.core.model.FareQuote
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.LabeledPoint
import com.yabah.app.core.model.RoutePreview
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.ConnectivityObserver
import com.yabah.app.data.places.Place
import com.yabah.app.data.places.PlaceIndex
import com.yabah.app.data.repo.RideRepository
import com.yabah.app.map.NativeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class Stage { Pickup, Dropoff, Review }

/** A chosen point. Labels are derived from the offline GeoNames index ("near X"), never from a geocoder. */
data class PointSelection(val point: GeoPoint, val near: Place?) {
    /** Address text sent to pricing (3..500 chars, Arabic-first). */
    fun serverLabel(): String = near?.let { "قرب " + (it.nameAr ?: it.nameEn) }
        ?: String.format(Locale.US, "نقطة على الخريطة %.5f, %.5f", point.lat, point.lng)
}

sealed interface RouteUi {
    data object Idle : RouteUi
    data object Loading : RouteUi
    data class Ready(val route: RoutePreview) : RouteUi
    data class Failed(val failure: ApiFailure) : RouteUi
    /** Demo mode: no server route exists; only an illustrative straight distance. */
    data class DemoOnly(val straightMeters: Int) : RouteUi
}

sealed interface QuoteUi {
    data object Idle : QuoteUi
    data object Loading : QuoteUi
    data class Ready(val quote: FareQuote) : QuoteUi
    data class Expired(val quote: FareQuote) : QuoteUi
    data class Failed(val failure: ApiFailure) : QuoteUi
}

sealed interface BookingUi {
    data object Idle : BookingUi
    data object Submitting : BookingUi
    /** No answer arrived: checking active_trip before anything else. */
    data object Reconciling : BookingUi
    /** Still unknown after checking. Booking stays disabled until a successful check. */
    data object Unresolved : BookingUi
    data class Failed(val failure: ApiFailure) : BookingUi
    data class Booked(val tripId: String) : BookingUi
}

sealed interface LocateUi {
    data object Idle : LocateUi
    data object Locating : LocateUi
    data object Denied : LocateUi
    data object Unavailable : LocateUi
    data class Approximate(val accuracyM: Int) : LocateUi
}

sealed interface CameraCommand {
    val id: Long
    data class MoveTo(override val id: Long, val point: GeoPoint, val zoom: Double?) : CameraCommand
    data class Fit(override val id: Long, val points: List<GeoPoint>) : CameraCommand
}

data class HomeUiState(
    val isDemo: Boolean,
    val city: City? = null,
    val cityFailure: ApiFailure? = null,
    val stage: Stage = Stage.Pickup,
    val pickup: PointSelection? = null,
    val dropoff: PointSelection? = null,
    /** Live selection under the centre pin while picking. */
    val pin: PointSelection? = null,
    val pinOutsideArea: Boolean = false,
    val route: RouteUi = RouteUi.Idle,
    val quote: QuoteUi = QuoteUi.Idle,
    val booking: BookingUi = BookingUi.Idle,
    val locate: LocateUi = LocateUi.Idle,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val online: Boolean = true,
    val camera: CameraCommand? = null,
    /** An active trip exists on the server: the UI should open it. */
    val openTripId: String? = null,
    val sessionExpired: Boolean = false,
    val mapToken: String? = null,
    val mapTokenFailure: ApiFailure? = null,
) {
    val canRequestTrip: Boolean
        get() = !isDemo && quote is QuoteUi.Ready && route is RouteUi.Ready &&
            (booking is BookingUi.Idle || booking is BookingUi.Failed)
}

class PassengerHomeViewModel(
    private val repo: RideRepository,
    private val places: PlaceIndex,
    private val areas: ServiceAreaRule,
    private val location: LocationClient,
    private val connectivity: ConnectivityObserver,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val routeDebounceMs: Long = 250,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isDemo = repo.isDemo))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var routeJob: Job? = null
    private var quoteJob: Job? = null
    private var quoteExpiryJob: Job? = null
    private var bookingJob: Job? = null
    private var cameraSeq = 0L

    init {
        viewModelScope.launch { connectivity.online.collect { on -> _state.update { it.copy(online = on) } } }
        loadCity()
    }

    // ---- bootstrap ---------------------------------------------------------------------------------
    fun loadCity() {
        viewModelScope.launch {
            try {
                val city = repo.serviceCities().firstOrNull()
                    ?: throw ApiException(ApiFailure.server("NO_CITY_ENABLED", null))
                _state.update { it.copy(city = city, cityFailure = null) }
                if (_state.value.pickup == null) moveCamera(city.center, 12.5)
                if (!repo.isDemo) {
                    checkActiveTrip()
                    if (NativeMap.isAvailable) loadMapToken()
                }
            } catch (e: ApiException) {
                onFailure(e.failure) { _state.update { it.copy(cityFailure = e.failure) } }
            }
        }
    }

    private suspend fun loadMapToken() {
        try {
            val token = repo.mapPublicToken()
            _state.update { it.copy(mapToken = token, mapTokenFailure = null) }
        } catch (e: ApiException) {
            onFailure(e.failure) { _state.update { it.copy(mapTokenFailure = e.failure) } }
        }
    }

    /** Re-read the authoritative active trip (app start / resume). */
    fun onResume() {
        if (repo.isDemo || _state.value.city == null) return
        val b = _state.value.booking
        if (b is BookingUi.Submitting || b is BookingUi.Reconciling) return
        viewModelScope.launch { checkActiveTrip() }
    }

    private suspend fun checkActiveTrip() {
        try {
            repo.activeTrip()?.let { trip -> if (trip.status.isActive) _state.update { it.copy(openTripId = trip.id) } }
        } catch (e: ApiException) {
            onFailure(e.failure) { /* non-blocking: home stays usable */ }
        }
    }

    fun onTripOpened() {
        _state.update { it.copy(openTripId = null) }
    }

    // ---- map & selection ---------------------------------------------------------------------------
    fun onCameraIdle(center: GeoPoint) {
        val s = _state.value
        if (s.stage == Stage.Review || !center.isValid) return
        val sel = PointSelection(center, places.nearest(center))
        _state.update { it.copy(pin = sel, pinOutsideArea = it.city?.let { c -> !areas.inServiceArea(center, c) } ?: false) }
    }

    fun onMapTap(point: GeoPoint) {
        if (_state.value.stage == Stage.Review) return
        moveCamera(point, null)
    }

    fun confirmPin() {
        val s = _state.value
        val pin = s.pin ?: return
        when (s.stage) {
            Stage.Pickup -> {
                val next = if (s.dropoff != null) Stage.Review else Stage.Dropoff
                _state.update { it.copy(pickup = pin, stage = next, searchQuery = "", searchResults = emptyList()) }
                if (next == Stage.Review) enterReview()
            }
            Stage.Dropoff -> {
                _state.update { it.copy(dropoff = pin, stage = Stage.Review, searchQuery = "", searchResults = emptyList()) }
                enterReview()
            }
            Stage.Review -> Unit
        }
    }

    fun editPickup() = edit(Stage.Pickup, _state.value.pickup?.point)
    fun editDropoff() = edit(Stage.Dropoff, _state.value.dropoff?.point ?: _state.value.pickup?.point)

    private fun edit(stage: Stage, focus: GeoPoint?) {
        if (_state.value.booking.let { it is BookingUi.Submitting || it is BookingUi.Reconciling || it is BookingUi.Unresolved }) return
        invalidateRouteAndQuote()
        _state.update { it.copy(stage = stage, booking = BookingUi.Idle) }
        focus?.let { moveCamera(it, 15.0) }
    }

    /** Any change of a point makes the old route and quote meaningless. */
    private fun invalidateRouteAndQuote() {
        routeJob?.cancel(); quoteJob?.cancel(); quoteExpiryJob?.cancel()
        _state.update { it.copy(route = RouteUi.Idle, quote = QuoteUi.Idle) }
    }

    private fun enterReview() {
        invalidateRouteAndQuote()
        val s = _state.value
        val a = s.pickup?.point ?: return
        val b = s.dropoff?.point ?: return
        fitCamera(listOf(a, b))
        if (repo.isDemo) {
            _state.update { it.copy(route = RouteUi.DemoOnly(Geo.metersBetween(a, b).toInt())) }
            return
        }
        startRoute(a, b)
    }

    fun retryRoute() {
        val s = _state.value
        if (s.stage != Stage.Review || s.route is RouteUi.Loading) return
        startRoute(s.pickup?.point ?: return, s.dropoff?.point ?: return)
    }

    private fun startRoute(a: GeoPoint, b: GeoPoint) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _state.update { it.copy(route = RouteUi.Loading) }
            delay(routeDebounceMs) // collapses rapid edits into one provider call
            if (!_state.value.online) { _state.update { it.copy(route = RouteUi.Failed(ApiFailure.offline)) }; return@launch }
            try {
                val r = repo.route(a, b, _state.value.city?.id)
                // Ignore late answers for points that have since changed.
                val s = _state.value
                if (s.pickup?.point != a || s.dropoff?.point != b) return@launch
                _state.update { it.copy(route = RouteUi.Ready(r)) }
                fitCamera(r.points)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                onFailure(e.failure) { _state.update { it.copy(route = RouteUi.Failed(e.failure)) } }
            }
        }
    }

    // ---- search ------------------------------------------------------------------------------------
    fun onSearchQuery(q: String) {
        val results = if (q.trim().length >= 2) places.search(q, 20, _state.value.city?.center) else emptyList()
        _state.update { it.copy(searchQuery = q, searchResults = results) }
    }

    fun onPlaceChosen(place: Place) {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        moveCamera(place.point, 15.5)
    }

    // ---- GPS (optional) ----------------------------------------------------------------------------
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) { _state.update { it.copy(locate = LocateUi.Denied) }; return }
        locate()
    }

    fun locate() {
        if (_state.value.locate == LocateUi.Locating) return
        if (!location.hasPermission()) { _state.update { it.copy(locate = LocateUi.Denied) }; return }
        viewModelScope.launch {
            _state.update { it.copy(locate = LocateUi.Locating) }
            val fix = location.currentLocation()
            if (fix == null) { _state.update { it.copy(locate = LocateUi.Unavailable) }; return@launch }
            if (_state.value.stage == Stage.Review) editPickup()
            moveCamera(fix.point, 16.0)
            val acc = fix.accuracyM?.toInt()
            _state.update { it.copy(locate = if (acc != null && acc > 75) LocateUi.Approximate(acc) else LocateUi.Idle) }
        }
    }

    fun dismissLocateMessage() = _state.update { it.copy(locate = LocateUi.Idle) }

    // ---- pricing -----------------------------------------------------------------------------------
    /** Explicit user action: each quote is billable (routing budget) and creates a server-side quote row. */
    fun requestQuote() {
        val s = _state.value
        if (s.isDemo || s.route !is RouteUi.Ready || s.quote is QuoteUi.Loading) return
        val city = s.city ?: return
        val a = s.pickup ?: return
        val b = s.dropoff ?: return
        if (!s.online) { _state.update { it.copy(quote = QuoteUi.Failed(ApiFailure.offline)) }; return }
        quoteJob?.cancel(); quoteExpiryJob?.cancel()
        quoteJob = viewModelScope.launch {
            _state.update { it.copy(quote = QuoteUi.Loading, booking = BookingUi.Idle) }
            try {
                val q = repo.quote(LabeledPoint(a.point, a.serverLabel()), LabeledPoint(b.point, b.serverLabel()), city.id)
                val now = _state.value
                if (now.pickup != a || now.dropoff != b) return@launch
                _state.update { it.copy(quote = QuoteUi.Ready(q)) }
                scheduleExpiry(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                onFailure(e.failure) { _state.update { it.copy(quote = QuoteUi.Failed(e.failure)) } }
            }
        }
    }

    private fun scheduleExpiry(q: FareQuote) {
        quoteExpiryJob?.cancel()
        quoteExpiryJob = viewModelScope.launch {
            delay((q.expiresAtLocalMs - nowMs()).coerceAtLeast(0))
            _state.update { s -> if ((s.quote as? QuoteUi.Ready)?.quote?.id == q.id) s.copy(quote = QuoteUi.Expired(q)) else s }
        }
    }

    // ---- booking -----------------------------------------------------------------------------------
    fun requestTrip() {
        val s = _state.value
        if (!s.canRequestTrip) return // also the double-submit guard: Submitting/Reconciling/Unresolved block
        val q = (s.quote as QuoteUi.Ready).quote
        if (nowMs() >= q.expiresAtLocalMs - 2_000) { _state.update { it.copy(quote = QuoteUi.Expired(q)) }; return }
        if (!s.online) { _state.update { it.copy(booking = BookingUi.Failed(ApiFailure.offline)) }; return }
        _state.update { it.copy(booking = BookingUi.Submitting) }
        bookingJob = viewModelScope.launch {
            try {
                val tripId = repo.requestTrip(q.id, q.option?.vehicleClass ?: "taxi", 1)
                booked(tripId)
            } catch (e: ApiException) {
                val f = e.failure
                when {
                    f.kind == ApiFailure.Kind.NoAnswer -> reconcile()
                    f.code == "PASSENGER_HAS_ACTIVE_TRIP" || f.code == "FARE_QUOTE_ALREADY_USED" -> reconcile(fallback = f)
                    f.code == "FARE_QUOTE_EXPIRED" || f.code == "FARE_QUOTE_NOT_FOUND" || f.code == "PRICE_CHANGED" ->
                        _state.update { it.copy(booking = BookingUi.Failed(f), quote = QuoteUi.Expired(q)) }
                    else -> onFailure(f) { _state.update { it.copy(booking = BookingUi.Failed(f)) } }
                }
            }
        }
    }

    /** A timeout is not proof of failure: ask the server whether the trip exists before allowing another try. */
    fun reconcile(fallback: ApiFailure? = null) {
        val b = _state.value.booking
        if (b is BookingUi.Reconciling) return
        _state.update { it.copy(booking = BookingUi.Reconciling) }
        viewModelScope.launch {
            try {
                val trip = repo.activeTrip()
                if (trip != null && trip.status.isActive) booked(trip.id)
                else _state.update { it.copy(booking = BookingUi.Failed(fallback ?: ApiFailure.noAnswer)) }
            } catch (e: ApiException) {
                onFailure(e.failure) { _state.update { it.copy(booking = BookingUi.Unresolved) } }
            }
        }
    }

    private fun booked(tripId: String) {
        quoteExpiryJob?.cancel()
        _state.update { it.copy(booking = BookingUi.Booked(tripId), openTripId = tripId) }
    }

    /** After the trip screen opened: reset the planner for the next ride. */
    fun resetAfterBooking() {
        invalidateRouteAndQuote()
        _state.update { it.copy(stage = Stage.Pickup, pickup = null, dropoff = null, booking = BookingUi.Idle, openTripId = null) }
    }

    // ---- helpers -----------------------------------------------------------------------------------
    private inline fun onFailure(f: ApiFailure, otherwise: () -> Unit) {
        if (f.isSessionProblem) _state.update { it.copy(sessionExpired = true) }
        otherwise()
    }

    private fun moveCamera(p: GeoPoint, zoom: Double?) {
        _state.update { it.copy(camera = CameraCommand.MoveTo(++cameraSeq, p, zoom)) }
    }

    private fun fitCamera(points: List<GeoPoint>) {
        _state.update { it.copy(camera = CameraCommand.Fit(++cameraSeq, points)) }
    }
}
