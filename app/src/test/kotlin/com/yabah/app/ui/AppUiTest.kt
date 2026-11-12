package com.yabah.app.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.yabah.app.AppContainer
import com.yabah.app.FakeLocation
import com.yabah.app.FakeRideRepository
import com.yabah.app.Fixtures
import com.yabah.app.YabahRoot
import com.yabah.app.core.auth.AuthRepository
import com.yabah.app.core.auth.Session
import com.yabah.app.core.auth.SessionStore
import com.yabah.app.core.config.AppPrefs
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.net.FixedConnectivity
import com.yabah.app.core.net.SupabaseHttp
import com.yabah.app.data.repo.RideRepository
import com.yabah.app.ui.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Real Compose UI driven on the JVM by Robolectric (real Android framework + native graphics): taps, drags, typing
 * and screenshots. Screens marked "fixture" render TEST data through a fake repository to show how the live UI
 * presents server answers; they are not server evidence.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class AppUiTest {
    @get:Rule val compose = createComposeRule()

    private fun container(demo: Boolean, lang: String? = "ar", repo: RideRepository = FakeRideRepository(), signedIn: Boolean = false, onboarded: Boolean = true): AppContainer {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPrefs(ctx.getSharedPreferences("t${System.nanoTime()}", 0)).apply {
            onboardingSeen = onboarded; demoMode = demo; setLanguage(lang)
        }
        val store = SessionStore(null, null)
        if (signedIn) store.save(Session("test-access", "test-refresh", Long.MAX_VALUE / 4, "11111111-1111-4111-8111-111111111111", "9647701234567"))
        val auth = AuthRepository(SupabaseHttp("http://127.0.0.1:1", "k"), store)
        return AppContainer(prefs, auth, FixedConnectivity(true), FakeLocation(), { repo }, { Fixtures.places }, { Fixtures.outlines },
            AppContainer.BuildInfo("0.1.0-test", phoneOtpEnabled = false, mapboxNative = false))
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")

    /** Let camera animations, the 350 ms idle detection and view-model delays run. */
    private fun settle(ms: Long = 1_200) {
        compose.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun sheetIs(value: String) = compose.onNodeWithTag("trip_sheet")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

    @Test fun demoHome_sheetReallyDragsBetweenThreeStates() {
        compose.setContent { YabahRoot(container(demo = true)) }
        settle()
        compose.onNodeWithTag("demo_chip").assertIsDisplayed()
        compose.onNodeWithTag("demo_notice").assertExists()
        sheetIs("Half")
        shot("01_home_demo_half_ar")

        compose.onNodeWithTag("sheet_handle").performTouchInput { swipeUp(startY = centerY, endY = centerY - 900f, durationMillis = 150) }
        settle(); sheetIs("Expanded")
        shot("02_home_demo_expanded_ar")

        compose.onNodeWithTag("sheet_handle").performTouchInput { swipeDown(startY = centerY, endY = centerY + 2400f, durationMillis = 200) }
        settle(); sheetIs("Collapsed")
        shot("03_home_demo_collapsed_ar")

        compose.onNodeWithTag("sheet_handle").performClick() // the grabber is a real control too
        settle(); sheetIs("Half")

        // Slow drag that ends near the Expanded anchor settles there (position-based).
        compose.onNodeWithTag("sheet_handle").performTouchInput { swipe(center, center - Offset(0f, 760f), durationMillis = 1500) }
        settle(); sheetIs("Expanded")
    }

    @Test fun demoFlow_tapAndPanToChoosePoints_neverShowsAFareOrBooks() {
        val repo = FakeRideRepository(isDemo = true) // not used in demo mode; recorded to prove no call
        compose.setContent { YabahRoot(container(demo = true, repo = repo)) }
        settle()
        // Tap the map: camera moves to the tapped point and the pin's label follows (offline GeoNames "near X").
        compose.onNodeWithTag("demo_map").performTouchInput { click(Offset(width * 0.42f, height * 0.25f)) }
        settle()
        compose.onNodeWithTag("pickup_row").assertTextContains("قرب", substring = true)
        compose.onNodeWithTag("confirm_button").assertIsEnabled().performClick()
        settle()
        // Destination: pan the map with a real drag.
        compose.onNodeWithTag("demo_map").performTouchInput { swipe(Offset(width * 0.5f, height * 0.3f), Offset(width * 0.2f, height * 0.36f), 400) }
        settle()
        compose.onNodeWithTag("dropoff_row").assertTextContains("قرب", substring = true)
        shot("04_home_demo_pick_dropoff_ar")
        compose.onNodeWithTag("confirm_button").performClick()
        settle()
        compose.onNodeWithTag("demo_route_notice").assertExists()
        compose.onNodeWithTag("request_button").assertIsNotEnabled()
        compose.onAllNodesWithTag("fare_amount").assertCountEquals(0)
        compose.onAllNodesWithTag("quote_button").assertCountEquals(0)
        shot("05_home_demo_review_ar")
        assertEquals(emptyList<String>(), repo.calls) // demo never touched a repository that could reach the server
    }

    @Test fun offlineSearch_choosingAPlaceMovesThePin() {
        compose.setContent { YabahRoot(container(demo = true)) }
        settle()
        compose.onNodeWithTag("search_field").performClick()
        compose.onNodeWithTag("search_field").performTextInput("منصور")
        settle()
        compose.onAllNodesWithTag("search_result").onFirst().assertTextContains("المنصور", substring = true)
        shot("06_home_demo_search_ar")
        compose.onAllNodesWithTag("search_result").onFirst().performClick()
        settle(1_800)
        compose.onNodeWithTag("pickup_row").assertTextContains("قرب المنصور", substring = true)
    }

    @Test fun signIn_defaultBuildSendsNothingAndOffersDemo() {
        compose.setContent { YabahRoot(container(demo = false)) }
        settle()
        compose.onNodeWithTag("otp_coming_soon").assertIsDisplayed()
        compose.onNodeWithTag("send_code").assertIsNotEnabled()
        shot("07_signin_default_ar")
        compose.onNodeWithTag("try_demo").performClick()
        settle()
        compose.onNodeWithTag("demo_chip").assertIsDisplayed()
    }

    @Test fun onboarding_ar() {
        compose.setContent { YabahRoot(container(demo = false, onboarded = false)) }
        settle()
        shot("08_onboarding_ar")
        compose.onNodeWithTag("onboarding_next").performClick(); settle()
        compose.onNodeWithTag("onboarding_next").performClick(); settle()
        compose.onNodeWithTag("onboarding_next").performClick(); settle()
        compose.onNodeWithTag("signin_screen").assertExists()
    }

    @Test fun english_isLeftToRight() {
        compose.setContent { YabahRoot(container(demo = true, lang = "en")) }
        settle()
        compose.onNodeWithTag("pickup_row").assertTextContains("Pickup", substring = true)
        shot("09_home_demo_half_en")
    }

    @Test fun darkTheme() {
        RuntimeEnvironment.setQualifiers("+night")
        compose.setContent { YabahRoot(container(demo = true)) }
        settle()
        shot("10_home_demo_dark_ar")
    }

    /** FIXTURE: live-mode UI with a fake server. Shows route → explicit price → request → trip screen. */
    @Test fun fixture_liveFlowRendersServerAnswersAndNavigatesToTrip() {
        val repo = FakeRideRepository()
        repo.quoteFn = { Fixtures.quote(expiresAt = System.currentTimeMillis() + 180_000) }
        compose.setContent { YabahRoot(container(demo = false, repo = repo, signedIn = true)) }
        settle()
        compose.onAllNodesWithTag("demo_chip").assertCountEquals(0)
        compose.onNodeWithTag("demo_map").performTouchInput { click(Offset(width * 0.45f, height * 0.25f)) }
        settle(); compose.onNodeWithTag("confirm_button").performClick(); settle()
        compose.onNodeWithTag("demo_map").performTouchInput { swipe(Offset(width * 0.5f, height * 0.3f), Offset(width * 0.25f, height * 0.38f), 400) }
        settle(); compose.onNodeWithTag("confirm_button").performClick(); settle()
        compose.onNodeWithTag("route_ready").assertExists()
        assertEquals(1, repo.count("route"))
        assertEquals(0, repo.count("quote"))
        compose.onNodeWithTag("request_button").assertIsNotEnabled() // no price yet → no booking
        compose.onNodeWithTag("quote_button").performClick(); settle()
        compose.onNodeWithTag("fare_amount").assertIsDisplayed()
        compose.onNodeWithTag("request_button").assertIsEnabled()
        shot("11_fixture_live_review_with_server_quote_ar")
        compose.onNodeWithTag("request_button").performClick()
        settle()
        compose.onNodeWithTag("trip_screen").assertExists()
        assertEquals(1, repo.count("request_trip"))
        shot("12_fixture_trip_searching_ar")
    }

    @Test fun fixture_tripAcceptedWithStaleCaptainLocation() {
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(TripStatus.Accepted, locationAgeMs = 4 * 60_000) } }
        compose.setContent { YabahRoot(container(demo = false, repo = repo, signedIn = true), start = Routes.trip("3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b")) }
        settle()
        compose.onNodeWithTag("captain_card").assertExists()
        compose.onNodeWithTag("stale_location").assertExists()
        compose.onNodeWithTag("cancel_trip").assertExists()
        shot("13_fixture_trip_accepted_stale_location_ar")
    }

    @Test fun fixture_completedTripRatesOnce() {
        val repo = FakeRideRepository().apply { detailsFn = { Fixtures.trip(TripStatus.Completed) } }
        compose.setContent { YabahRoot(container(demo = false, repo = repo, signedIn = true), start = Routes.trip("3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b")) }
        settle()
        compose.onAllNodesWithTag("cancel_trip").assertCountEquals(0)
        compose.onNodeWithTag("star_5").performClick(); settle()
        shot("14_fixture_trip_completed_rating_ar")
        compose.onNodeWithTag("submit_rating").performClick(); settle()
        assertEquals(listOf("rate:5"), repo.calls.filter { it.startsWith("rate") })
    }
}
