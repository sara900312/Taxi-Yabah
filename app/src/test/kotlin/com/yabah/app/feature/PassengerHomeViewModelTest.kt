package com.yabah.app.feature

import com.yabah.app.FakeLocation
import com.yabah.app.FakeRideRepository
import com.yabah.app.Fixtures
import com.yabah.app.MainDispatcherRule
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.FixedConnectivity
import com.yabah.app.feature.home.BookingUi
import com.yabah.app.feature.home.PassengerHomeViewModel
import com.yabah.app.feature.home.QuoteUi
import com.yabah.app.feature.home.RouteUi
import com.yabah.app.feature.home.Stage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PassengerHomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val connectivity = FixedConnectivity(true)

    private fun vm(repo: FakeRideRepository, now: () -> Long = { 1_000_000L }) =
        PassengerHomeViewModel(repo, Fixtures.places, Fixtures.areas, FakeLocation(), connectivity, now)

    /** Advance just enough virtual time for in-flight work, without firing far-future timers. */
    private fun kotlinx.coroutines.test.TestScope.settle() { advanceTimeBy(1_000); runCurrent() }

    private fun PassengerHomeViewModel.pick(a: GeoPoint, b: GeoPoint) {
        onCameraIdle(a); confirmPin(); onCameraIdle(b); confirmPin()
    }

    @Test fun demoNeverCallsTheServerAndCannotBook() = runTest(main.dispatcher) {
        val repo = FakeRideRepository(isDemo = true)
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        val s = vm.state.value
        assertEquals(Stage.Review, s.stage)
        assertTrue(s.route is RouteUi.DemoOnly)
        vm.requestQuote(); vm.requestTrip(); settle()
        assertEquals(listOf("cities"), repo.calls) // no route, quote, booking or active-trip call
        assertTrue(vm.state.value.quote is QuoteUi.Idle)
        assertFalse(vm.state.value.canRequestTrip)
    }

    @Test fun pinSelectionUsesNearestPlaceAndLocalAreaHint() = runTest(main.dispatcher) {
        val vm = vm(FakeRideRepository()); advanceUntilIdle()
        vm.onCameraIdle(GeoPoint(33.3231, 44.3527))
        assertEquals("المنصور", vm.state.value.pin?.near?.nameAr)
        assertEquals("قرب المنصور", vm.state.value.pin?.serverLabel())
        assertFalse(vm.state.value.pinOutsideArea)
        vm.onCameraIdle(GeoPoint(33.70, 44.30))
        assertTrue(vm.state.value.pinOutsideArea)
    }

    @Test fun routeIsRequestedOnceAndOnlyAfterBothPoints() = runTest(main.dispatcher) {
        val repo = FakeRideRepository()
        val vm = vm(repo); advanceUntilIdle()
        vm.onCameraIdle(Fixtures.mansour); vm.confirmPin(); advanceUntilIdle()
        assertEquals(0, repo.count("route"))
        repeat(5) { vm.onCameraIdle(GeoPoint(33.29 + it * 0.001, 44.41)) } // camera noise while picking
        vm.confirmPin(); advanceUntilIdle()
        assertEquals(1, repo.count("route"))
        assertTrue(vm.state.value.route is RouteUi.Ready)
        assertEquals(0, repo.count("quote")) // pricing is an explicit action
    }

    @Test fun editingAPointCancelsRouteAndInvalidatesQuote() = runTest(main.dispatcher) {
        val repo = FakeRideRepository()
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        assertTrue(vm.state.value.quote is QuoteUi.Ready)
        vm.editPickup()
        assertTrue(vm.state.value.quote is QuoteUi.Idle)
        assertTrue(vm.state.value.route is RouteUi.Idle)
        assertEquals(Stage.Pickup, vm.state.value.stage)
    }

    @Test fun lateRouteAnswerForOldPointsIsIgnored() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRideRepository().apply { routeFn = { a, b -> gate.await(); com.yabah.app.core.model.RoutePreview(listOf(a, b), 1, 1) } }
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceTimeBy(300); runCurrent()
        vm.editDropoff() // user changes their mind while the route is in flight
        gate.complete(Unit); advanceUntilIdle()
        assertTrue(vm.state.value.route is RouteUi.Idle)
    }

    @Test fun serverRouteErrorsAreShownNotReplaced() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { routeFn = { _, _ -> throw ApiException(ApiFailure.server("DROPOFF_OUTSIDE_SERVICE_AREA", 422)) } }
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        assertEquals("DROPOFF_OUTSIDE_SERVICE_AREA", (vm.state.value.route as RouteUi.Failed).failure.code)
        vm.requestQuote(); settle()
        assertEquals(0, repo.count("quote"))
    }

    @Test fun quoteExpiresAndBlocksBooking() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { quoteFn = { Fixtures.quote(expiresAt = testScheduler.currentTime + 180_000) } }
        val vm = vm(repo) { testScheduler.currentTime }; advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); runCurrent()
        assertTrue(vm.state.value.quote is QuoteUi.Ready)
        advanceTimeBy(181_000); runCurrent()
        assertTrue(vm.state.value.quote is QuoteUi.Expired)
        vm.requestTrip(); settle()
        assertEquals(0, repo.count("request_trip"))
    }

    @Test fun doubleTapSubmitsExactlyOnce() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<String>()
        val repo = FakeRideRepository().apply { requestFn = { gate.await() } }
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        vm.requestTrip(); vm.requestTrip(); runCurrent(); vm.requestTrip(); runCurrent()
        assertEquals(BookingUi.Submitting, vm.state.value.booking)
        gate.complete("3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b"); settle()
        assertEquals(1, repo.count("request_trip"))
        assertEquals("3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b", vm.state.value.openTripId)
    }

    @Test fun timeoutIsReconciledWithActiveTripNotResent() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply {
            requestFn = { throw ApiException(ApiFailure.noAnswer) }
            activeFn = { Fixtures.trip(TripStatus.Requested, quoteId = "6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d") }
        }
        val vm = vm(repo); advanceUntilIdle()
        repo.calls.clear() // the start-up active-trip check already happened
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        vm.requestTrip(); settle()
        assertEquals(listOf("route", "quote", "request_trip", "active_trip"), repo.calls)
        assertTrue(vm.state.value.booking is BookingUi.Booked)
    }

    @Test fun unresolvedTimeoutBlocksRetryUntilChecked() = runTest(main.dispatcher) {
        var serverReachable = false
        val repo = FakeRideRepository().apply {
            requestFn = { throw ApiException(ApiFailure.noAnswer) }
            activeFn = { if (serverReachable) null else throw ApiException(ApiFailure.offline) }
        }
        serverReachable = true
        val vm = vm(repo); advanceUntilIdle()
        serverReachable = false
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        vm.requestTrip(); settle()
        assertEquals(BookingUi.Unresolved, vm.state.value.booking)
        vm.requestTrip(); settle()
        assertEquals(1, repo.count("request_trip")) // blocked while unknown
        serverReachable = true
        vm.reconcile(); settle()
        assertTrue(vm.state.value.booking is BookingUi.Failed) // confirmed: nothing was created
        assertTrue(vm.state.value.canRequestTrip)
    }

    @Test fun existingActiveTripOpensInsteadOfDuplicating() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply {
            requestFn = { throw ApiException(ApiFailure.server("PASSENGER_HAS_ACTIVE_TRIP", 409)) }
        }
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        repo.activeFn = { Fixtures.trip(TripStatus.Accepted) }
        vm.requestTrip(); settle()
        assertEquals(Fixtures.trip(TripStatus.Accepted).id, vm.state.value.openTripId)
    }

    @Test fun expiredQuoteFromServerAsksForRefresh() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { requestFn = { throw ApiException(ApiFailure.server("FARE_QUOTE_EXPIRED", 409)) } }
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        vm.requestTrip(); settle()
        assertTrue(vm.state.value.quote is QuoteUi.Expired)
        assertEquals("FARE_QUOTE_EXPIRED", (vm.state.value.booking as BookingUi.Failed).failure.code)
    }

    @Test fun activeTripOnStartupOpensIt() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { activeFn = { Fixtures.trip(TripStatus.Arrived) } }
        val vm = vm(repo); advanceUntilIdle()
        assertEquals(Fixtures.trip(TripStatus.Arrived).id, vm.state.value.openTripId)
    }

    @Test fun sessionProblemsSignalSignIn() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { cities = { throw ApiException(ApiFailure.session) } }
        val vm = vm(repo); advanceUntilIdle()
        assertTrue(vm.state.value.sessionExpired)
    }

    @Test fun offlineNeverSendsTheBooking() = runTest(main.dispatcher) {
        val repo = FakeRideRepository()
        val vm = vm(repo); advanceUntilIdle()
        vm.pick(Fixtures.mansour, Fixtures.karrada); advanceUntilIdle()
        vm.requestQuote(); settle()
        connectivity.online.value = false; runCurrent()
        vm.requestTrip(); settle()
        assertEquals(0, repo.count("request_trip"))
        assertEquals(ApiFailure.Kind.Offline, (vm.state.value.booking as BookingUi.Failed).failure.kind)
    }

    @Test fun gpsDeniedFallsBackToManualPin() = runTest(main.dispatcher) {
        val vm = PassengerHomeViewModel(FakeRideRepository(), Fixtures.places, Fixtures.areas, FakeLocation(permitted = false), connectivity)
        advanceUntilIdle()
        vm.onLocationPermissionResult(false)
        assertEquals(com.yabah.app.feature.home.LocateUi.Denied, vm.state.value.locate)
        assertNull(vm.state.value.pickup)
    }
}
