package com.yabah.app

import com.yabah.app.core.geo.ServiceAreaRule
import com.yabah.app.core.location.LocationClient
import com.yabah.app.core.location.LocationFix
import com.yabah.app.core.model.CaptainInfo
import com.yabah.app.core.model.CaptainLocation
import com.yabah.app.core.model.City
import com.yabah.app.core.model.FareQuote
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.LabeledPoint
import com.yabah.app.core.model.RideOption
import com.yabah.app.core.model.RoutePreview
import com.yabah.app.core.model.TripDetails
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.model.VehicleInfo
import com.yabah.app.data.places.PlaceIndex
import com.yabah.app.data.repo.DemoRideRepository
import com.yabah.app.data.repo.RideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/**
 * TEST FIXTURES ONLY. Values here are invented for tests and screenshots of how server data is rendered;
 * they are never compiled into the app.
 */
object Fixtures {
    private fun asset(path: String) = File("src/main/assets/$path").readText()
    val places: PlaceIndex by lazy { PlaceIndex.parse(asset("places/baghdad.json")) }
    val outlines by lazy { AppContainer.parseOutlines(asset("geo/service_outlines.json")) }
    val areas by lazy { ServiceAreaRule(outlines) }
    val baghdad: City = DemoRideRepository.DEMO_BAGHDAD

    val mansour = GeoPoint(33.32297, 44.35253)
    val karrada = GeoPoint(33.29376, 44.41288)

    fun quote(id: String = "6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d", expiresAt: Long, fare: Long = 7000) =
        FareQuote(id, fare, "IQD", 7200, 960, expiresAt, 1, RideOption("taxi", 2, "تكسي", 4, fare, 3))

    fun trip(
        status: TripStatus,
        serverNow: Long = 1_800_000_000_000,
        locationAgeMs: Long? = 20_000,
        rated: Boolean = false,
        quoteId: String? = null,
    ) = TripDetails(
        id = "3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b", status = status, fareQuoteId = quoteId,
        pickupAddress = "قرب المنصور", pickup = mansour, dropoffAddress = "قرب الكرادة الشرقية", dropoff = karrada,
        estimatedFareIqd = 7000, finalFareIqd = if (status == TripStatus.Completed) 7000 else null,
        passengerPaysIqd = if (status == TripStatus.Completed) 7000 else null, paymentMethod = "cash", cancellationReason = null,
        vehicleClassNameAr = "تكسي",
        captain = if (status == TripStatus.Requested) null else CaptainInfo("كابتن الاختبار", 4.8, 120, "+9647700000000"),
        vehicle = if (status == TripStatus.Requested) null else VehicleInfo("Toyota", "Corolla", "أبيض", "بغداد 12345", 2019),
        captainLocation = if (status == TripStatus.Requested || locationAgeMs == null) null
            else CaptainLocation(GeoPoint(33.31, 44.37), serverNow - locationAgeMs),
        events = emptyList(), myRatingSubmitted = rated, serverNowMs = serverNow,
    )
}

/** Records every call; each behaviour can be swapped per test. */
class FakeRideRepository(override val isDemo: Boolean = false) : RideRepository {
    val calls = mutableListOf<String>()
    var cities: suspend () -> List<City> = { listOf(Fixtures.baghdad) }
    var routeFn: suspend (GeoPoint, GeoPoint) -> RoutePreview = { a, b ->
        RoutePreview(listOf(a, GeoPoint((a.lat + b.lat) / 2 + 0.004, (a.lng + b.lng) / 2), b), 7200, 960)
    }
    var quoteFn: suspend () -> FareQuote = { Fixtures.quote(expiresAt = Long.MAX_VALUE / 2) }
    var requestFn: suspend () -> String = { "3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b" }
    var activeFn: suspend () -> TripDetails? = { null }
    var detailsFn: suspend () -> TripDetails = { Fixtures.trip(TripStatus.Requested) }
    var cancelFn: suspend () -> Unit = {}
    var rateFn: suspend () -> Unit = {}

    override suspend fun serviceCities() = cities().also { calls += "cities" }
    override suspend fun mapPublicToken(): String { calls += "token"; return "pk.test.test" }
    override suspend fun route(pickup: GeoPoint, dropoff: GeoPoint, cityId: Int?): RoutePreview { calls += "route"; return routeFn(pickup, dropoff) }
    override suspend fun quote(pickup: LabeledPoint, dropoff: LabeledPoint, cityId: Int): FareQuote { calls += "quote"; return quoteFn() }
    override suspend fun requestTrip(quoteId: String, vehicleClass: String, passengerCount: Int): String { calls += "request_trip"; return requestFn() }
    override suspend fun activeTrip(): TripDetails? { calls += "active_trip"; return activeFn() }
    override suspend fun tripDetails(tripId: String): TripDetails { calls += "trip_details"; return detailsFn() }
    override suspend fun cancelTrip(tripId: String, reason: String?) { calls += "cancel"; cancelFn() }
    override suspend fun rateTrip(tripId: String, stars: Int, comment: String?) { calls += "rate:$stars"; rateFn() }

    fun count(name: String) = calls.count { it == name }
}

class FakeLocation(var permitted: Boolean = true, var fix: LocationFix? = LocationFix(Fixtures.mansour, 12f)) : LocationClient {
    override fun hasPermission() = permitted
    override suspend fun currentLocation() = fix
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
