package com.yabah.app.data

import com.yabah.app.core.auth.AuthRepository
import com.yabah.app.core.auth.PhoneAuthError
import com.yabah.app.core.auth.PhoneAuthException
import com.yabah.app.core.auth.Session
import com.yabah.app.core.auth.SessionStore
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.LabeledPoint
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.SupabaseHttp
import com.yabah.app.core.net.YabahJson
import com.yabah.app.data.repo.ProductionRideRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Verifies request/response shapes against the deployed edge-function contracts (read 12 Nov 2026). */
class ContractTest {
    private lateinit var server: MockWebServer
    private lateinit var http: SupabaseHttp
    private lateinit var store: SessionStore
    private var now = 1_800_000_000L

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        http = SupabaseHttp(server.url("/").toString().trimEnd('/'), "sb_publishable_test", client)
        store = SessionStore(null, null)
    }

    @After fun tearDown() = server.shutdown()

    private fun signedIn(): AuthRepository {
        store.save(Session("access-1", "refresh-1", now + 3600, "11111111-1111-4111-8111-111111111111", "9647701234567"))
        return AuthRepository(http, store) { now }
    }

    private fun json(code: Int, body: String, date: String? = null) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body).apply { if (date != null) setHeader("Date", date) }

    private fun body(): JsonObject = YabahJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test fun routeRequestAndParsing() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        server.enqueue(json(200, """{"ok":true,"route":{"geometry":{"type":"LineString","coordinates":[[44.35,33.32],[44.38,33.31],[44.41,33.29]]},"distance_m":7210,"duration_s":905}}"""))
        val r = repo.route(GeoPoint(33.32, 44.35), GeoPoint(33.29, 44.41), 1)
        val req = server.takeRequest()
        assertEquals("/functions/v1/taxi-passenger-route", req.path)
        assertEquals("Bearer access-1", req.getHeader("Authorization"))
        assertEquals("sb_publishable_test", req.getHeader("apikey"))
        val b = YabahJson.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("33.32", b["pickup"]!!.jsonObject["lat"]!!.jsonPrimitive.content)
        assertEquals("44.41", b["dropoff"]!!.jsonObject["lng"]!!.jsonPrimitive.content)
        assertEquals("1", b["city_id"]!!.jsonPrimitive.content)
        assertEquals(3, r.points.size)
        assertEquals(GeoPoint(33.32, 44.35), r.points.first()) // [lng,lat] → GeoPoint(lat,lng)
        assertEquals(7210, r.distanceM); assertEquals(905, r.durationS)
    }

    @Test fun invalidGeometryIsNeverDrawn() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        server.enqueue(json(200, """{"ok":true,"route":{"geometry":{"type":"Point","coordinates":[[44.35,33.32]]},"distance_m":10,"duration_s":5}}"""))
        val e = runCatching { repo.route(GeoPoint(33.32, 44.35), GeoPoint(33.29, 44.41), 1) }.exceptionOrNull() as ApiException
        assertEquals(ApiFailure.Kind.InvalidResponse, e.failure.kind)
    }

    @Test fun serverErrorCodesAreNormalised() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        for ((status, code) in listOf(422 to "OUTSIDE_SERVICE_AREA", 422 to "DROPOFF_OUTSIDE_SERVICE_AREA", 429 to "QUOTE_RATE_LIMITED",
            503 to "MAPBOX_DAILY_LIMIT_REACHED", 403 to "PASSENGER_ROLE_REQUIRED", 503 to "DYNAMIC_PRICING_NOT_CONFIGURED")) {
            server.enqueue(json(status, """{"ok":false,"error":"$code"}"""))
            val e = runCatching { repo.route(GeoPoint(33.32, 44.35), GeoPoint(33.29, 44.41), 1) }.exceptionOrNull() as ApiException
            assertEquals(code, e.failure.code); assertEquals(status, e.failure.httpStatus)
        }
    }

    @Test fun quoteUsesServerClockForExpiry() = runBlocking {
        var deviceNow = 5_000_000L // device clock is wildly wrong on purpose
        val repo = ProductionRideRepository(http, signedIn()) { deviceNow }
        // Server says: now = 08:00:00Z, expires 08:03:00Z (180 s TTL, as configured live).
        server.enqueue(json(200, """{"ok":true,"quote":{"id":"6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d","estimated_fare_iqd":6500,"currency":"IQD",
            "distance_m":7210,"duration_s":905,"expires_at":"2026-11-12T08:03:00.000Z","city_id":1,
            "options":[{"vehicle_class":"taxi","vehicle_class_id":2,"name_ar":"تكسي","max_passengers":4,"final_fare_iqd":7000,"available_captains":0}]}}""",
            date = "Thu, 12 Nov 2026 08:00:00 GMT"))
        val q = repo.quote(LabeledPoint(GeoPoint(33.32, 44.35), "قرب المنصور"), LabeledPoint(GeoPoint(33.29, 44.41), "قرب الكرادة"), 1)
        val b = body()
        assertEquals("quote_fare", b["action"]!!.jsonPrimitive.content)
        assertEquals("standard_taxi", b["offer"]!!.jsonPrimitive.content)
        assertEquals("قرب المنصور", b["pickup_address"]!!.jsonPrimitive.content)
        assertEquals(deviceNow + 180_000, q.expiresAtLocalMs)
        assertEquals(7000, q.displayFareIqd) // the amount request_trip charges for the offered class
        assertEquals(6500, q.estimatedFareIqd)
    }

    @Test fun requestTripBodyIsCashTaxiWithQuote() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        server.enqueue(json(201, """{"ok":true,"trip":{"id":"3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b","status":"requested"}}"""))
        assertEquals("3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b", repo.requestTrip("6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d", "taxi", 1))
        val b = body()
        assertEquals("request_trip", b["action"]!!.jsonPrimitive.content)
        assertEquals("6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d", b["fare_quote_id"]!!.jsonPrimitive.content)
        assertEquals("cash", b["payment_method"]!!.jsonPrimitive.content)
        assertEquals("taxi", b["vehicle_class"]!!.jsonPrimitive.content)
        assertEquals("1", b["passenger_count"]!!.jsonPrimitive.content)
    }

    @Test fun lostAnswerIsNoAnswerNotFailure() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val e = runCatching { repo.requestTrip("6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d", "taxi", 1) }.exceptionOrNull() as ApiException
        assertEquals(ApiFailure.Kind.NoAnswer, e.failure.kind)
        assertEquals(1, server.requestCount) // never re-sent automatically
    }

    @Test fun unreachableServerIsOffline() = runBlocking {
        val dead = SupabaseHttp("http://127.0.0.1:1", "k")
        val e = runCatching { dead.get("/x", null) }.exceptionOrNull() as ApiException
        assertEquals(ApiFailure.Kind.Offline, e.failure.kind)
    }

    @Test fun invalidSessionRefreshesOnceThenRetries() = runBlocking {
        val auth = signedIn()
        val repo = ProductionRideRepository(http, auth)
        server.enqueue(json(401, """{"ok":false,"error":"INVALID_SESSION"}"""))
        server.enqueue(json(200, """{"access_token":"access-2","refresh_token":"refresh-2","expires_in":3600,"user":{"id":"11111111-1111-4111-8111-111111111111"}}"""))
        server.enqueue(json(200, """{"ok":true,"details":null}"""))
        assertNull(repo.activeTrip())
        assertEquals("/functions/v1/taxi-trips", server.takeRequest().path)
        val refresh = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.path)
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
        assertEquals("access-2", auth.session.value?.accessToken)
    }

    @Test fun deadRefreshTokenSignsOut() = runBlocking {
        now += 10_000 // access token expired
        val auth = signedIn().also { now += 7200 }
        server.enqueue(json(400, """{"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token"}"""))
        val e = runCatching { auth.accessToken() }.exceptionOrNull() as ApiException
        assertTrue(e.failure.isSessionProblem)
        assertNull(auth.session.value)
    }

    @Test fun tripDetailsMapsServerShape() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        server.enqueue(json(200, """{"ok":true,"details":{"trip":{"id":"3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b","status":"accepted",
            "pickup_address":"قرب المنصور","pickup_latitude":33.32,"pickup_longitude":44.35,"dropoff_address":"قرب الكرادة",
            "dropoff_latitude":33.29,"dropoff_longitude":44.41,"estimated_fare_iqd":7000,"payment_method":"cash","fare_quote_id":"6f1c2b9e-1d2a-4c3b-9a8d-0e1f2a3b4c5d"},
            "vehicle_class":{"code":"taxi","name_ar":"تكسي"},
            "captain":{"id":"x","full_name":"علي","avatar_path":null,"rating_avg":4.9,"rating_count":12,"phone_e164":"+9647700000000"},
            "vehicle":{"make":"Kia","model":"Rio","color":"أبيض","plate_number":"12345","vehicle_type":"sedan","manufacture_year":2020},
            "captain_location":{"latitude":33.31,"longitude":44.36,"updated_at":"2026-11-12T07:57:00.123456+00:00"},
            "events":[{"type":"requested","at":"2026-11-12T07:55:00+00:00","note":null}],"my_rating_submitted":false}}""",
            date = "Thu, 12 Nov 2026 08:00:00 GMT"))
        val d = repo.tripDetails("3d1f8a52-7c41-4b8e-9a0b-1c2d3e4f5a6b")
        assertEquals(TripStatus.Accepted, d.status)
        assertEquals("علي", d.captain?.fullName)
        assertEquals("12345", d.vehicle?.plateNumber)
        assertEquals(179_877, d.serverNowMs - d.captainLocation!!.updatedAtMs!!) // server Date − updated_at (µs-precision timestamp)
        assertEquals(1, d.events.size)
    }

    @Test fun onlyPublicMapboxTokensAreAccepted() = runBlocking {
        val repo = ProductionRideRepository(http, signedIn())
        server.enqueue(json(200, """{"ok":true,"token":"sk.eyJ1Ijoic2VjcmV0In0.abc"}"""))
        val e = runCatching { repo.mapPublicToken() }.exceptionOrNull() as ApiException
        assertEquals("MAPBOX_PUBLIC_TOKEN_UNAVAILABLE", e.failure.code)
        server.enqueue(json(200, """{"ok":true,"token":"pk.eyJ1IjoieWFiYWgifQ.abc_DEF-1"}"""))
        assertEquals("pk.eyJ1IjoieWFiYWgifQ.abc_DEF-1", repo.mapPublicToken())
    }

    @Test fun phoneOtpRequestsAndGoTrueErrorMapping() = runBlocking {
        val auth = AuthRepository(http, store) { now }
        server.enqueue(json(200, "{}"))
        auth.sendOtp("+9647701234567")
        val b = body()
        assertEquals("+9647701234567", b["phone"]!!.jsonPrimitive.content)
        assertEquals("sms", b["channel"]!!.jsonPrimitive.content)

        server.enqueue(json(400, """{"code":400,"error_code":"phone_provider_disabled","msg":"Unsupported phone provider"}"""))
        val e = runCatching { auth.sendOtp("+9647701234567") }.exceptionOrNull() as PhoneAuthException
        assertEquals(PhoneAuthError.SignInUnavailable, e.error)

        server.enqueue(json(403, """{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}"""))
        val e2 = runCatching { auth.verifyOtp("+9647701234567", "123456") }.exceptionOrNull() as PhoneAuthException
        assertEquals(PhoneAuthError.OtpInvalidOrExpired, e2.error)

        assertEquals(PhoneAuthError.OtpRateLimited, AuthRepository.mapGoTrue(429, null))
        server.enqueue(json(200, """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u-1","phone":"9647701234567"}}"""))
        server.takeRequest(); server.takeRequest()
        val s = auth.verifyOtp("+9647701234567", "123456")
        assertEquals("u-1", s.userId)
        if (auth.session.value == null) fail("session not stored")
    }
}
