package com.yabah.app.feature

import com.yabah.app.FakeRideRepository
import com.yabah.app.Fixtures
import com.yabah.app.MainDispatcherRule
import com.yabah.app.core.auth.AuthRepository
import com.yabah.app.core.auth.SessionStore
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.SupabaseHttp
import com.yabah.app.feature.auth.SignInStep
import com.yabah.app.feature.auth.SignInViewModel
import com.yabah.app.feature.trip.ActionUi
import com.yabah.app.feature.trip.TripViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripAndSignInViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun pollsWhileActiveAndStopsWhenTerminal() = runTest(main.dispatcher) {
        var status = TripStatus.Requested
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(status) } }
        val vm = TripViewModel("t", repo, baseIntervalMs = 5_000)
        vm.onVisible(); runCurrent()
        assertEquals(1, repo.count("trip_details"))
        advanceTimeBy(5_001); runCurrent()
        assertEquals(2, repo.count("trip_details"))
        status = TripStatus.Completed
        advanceTimeBy(5_001); runCurrent()
        val n = repo.count("trip_details")
        advanceTimeBy(60_000); runCurrent()
        assertEquals(n, repo.count("trip_details")) // terminal → no more polling
        vm.onHidden()
    }

    @Test fun hiddenScreenDoesNotPoll() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(TripStatus.Accepted) } }
        val vm = TripViewModel("t", repo)
        vm.onVisible(); runCurrent(); vm.onHidden()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, repo.count("trip_details"))
    }

    @Test fun staleCaptainLocationUsesServerClock() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(TripStatus.Accepted, locationAgeMs = 3 * 60_000) } }
        val vm = TripViewModel("t", repo)
        vm.refresh(); advanceUntilIdle()
        assertEquals(3, vm.state.value.captainLocationAgeMin)
        assertTrue(vm.state.value.captainLocationStale)
        repo.detailsFn = { Fixtures.trip(TripStatus.Accepted, locationAgeMs = 30_000) }
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.captainLocationStale)
    }

    @Test fun cancelOnlyWhenServerAllowsIt() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(TripStatus.InProgress) } }
        val vm = TripViewModel("t", repo)
        vm.refresh(); advanceUntilIdle()
        vm.cancelTrip(); advanceUntilIdle()
        assertEquals(0, repo.count("cancel"))
        repo.detailsFn = { Fixtures.trip(TripStatus.Requested) }
        vm.refresh(); advanceUntilIdle()
        vm.cancelTrip(); vm.cancelTrip(); advanceUntilIdle()
        assertEquals(1, repo.count("cancel"))
    }

    @Test fun ratingIsSubmittedOnce() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(TripStatus.Completed) } }
        val vm = TripViewModel("t", repo)
        vm.refresh(); advanceUntilIdle()
        vm.setStars(5); vm.submitRating(); vm.submitRating(); advanceUntilIdle()
        vm.submitRating(); advanceUntilIdle()
        assertEquals(listOf("rate:5"), repo.calls.filter { it.startsWith("rate") })
        assertEquals(ActionUi.Done, vm.state.value.rating)
    }

    @Test fun alreadyRatedCountsAsDone() = runTest(main.dispatcher) {
        val repo = FakeRideRepository().apply {
            detailsFn = { Fixtures.trip(TripStatus.Completed) }
            rateFn = { throw ApiException(ApiFailure.server("ALREADY_RATED", 409)) }
        }
        val vm = TripViewModel("t", repo)
        vm.refresh(); advanceUntilIdle()
        vm.setStars(4); vm.submitRating(); advanceUntilIdle()
        assertEquals(ActionUi.Done, vm.state.value.rating)
    }

    @Test fun defaultBuildNeverSendsAnSms() = runTest(main.dispatcher) {
        val server = MockWebServer().also { it.start() }
        val auth = AuthRepository(SupabaseHttp(server.url("/").toString(), "k"), SessionStore(null, null))
        val vm = SignInViewModel(auth, otpEnabled = false)
        vm.onPhone("07701234567"); vm.sendCode(); advanceUntilIdle()
        assertEquals(0, server.requestCount)
        assertEquals(SignInStep.Phone, vm.state.value.step)
        server.shutdown()
    }

    @Test fun otpFlowPhoneCodeNameDone() = runTest(main.dispatcher) {
        val server = MockWebServer().also { it.start() }
        val auth = AuthRepository(SupabaseHttp(server.url("/").toString().trimEnd('/'), "k"), SessionStore(null, null))
        val vm = SignInViewModel(auth, otpEnabled = true, resendSeconds = 60)
        vm.onPhone("0770123"); vm.sendCode(); advanceUntilIdle()
        assertTrue(vm.state.value.phoneInvalid)
        assertEquals(0, server.requestCount) // invalid number never sent

        server.enqueue(MockResponse().setBody("{}"))
        vm.onPhone("0770 123 4567"); vm.sendCode()
        while (vm.state.value.step != SignInStep.Code) { Thread.sleep(10); runCurrent() }
        assertEquals(SignInStep.Code, vm.state.value.step)
        vm.sendCode(); runCurrent() // resend blocked by the 60 s timer
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse().setBody("""{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u-1","phone":"9647701234567"}}"""))
        server.enqueue(MockResponse().setBody("""[{"id":"u-1","full_name":null,"phone_e164":"+9647701234567","phone_verified_at":"2026-11-12T08:00:00Z","account_status":"active"}]"""))
        vm.onCode("١٢٣٤٥٦"); vm.verify()
        while (vm.state.value.busy || vm.state.value.step == SignInStep.Code) { Thread.sleep(10); runCurrent() }
        assertEquals(SignInStep.Name, vm.state.value.step)
        server.shutdown()
    }
}
