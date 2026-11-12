package com.yabah.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.yabah.app.core.auth.AuthRepository
import com.yabah.app.core.auth.SessionStore
import com.yabah.app.core.config.AppPrefs
import com.yabah.app.core.geo.Ring
import com.yabah.app.core.geo.ServiceAreaRule
import com.yabah.app.core.location.AndroidLocationClient
import com.yabah.app.core.location.LocationClient
import com.yabah.app.core.net.AndroidConnectivityObserver
import com.yabah.app.core.net.ConnectivityObserver
import com.yabah.app.core.net.SupabaseHttp
import com.yabah.app.core.net.YabahJson
import com.yabah.app.data.places.PlaceIndex
import com.yabah.app.data.repo.DemoRideRepository
import com.yabah.app.data.repo.ProductionRideRepository
import com.yabah.app.data.repo.RideRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Lightweight manual DI. Tests build their own instance with fakes. */
open class AppContainer(
    val prefs: AppPrefs,
    val auth: AuthRepository,
    val connectivity: ConnectivityObserver,
    val location: LocationClient,
    private val productionRepo: () -> RideRepository,
    private val placesLoader: () -> PlaceIndex,
    private val outlinesLoader: () -> Map<String, Ring>,
    val config: BuildInfo,
) {
    val places: PlaceIndex by lazy { placesLoader() }
    val serviceAreas: ServiceAreaRule by lazy { ServiceAreaRule(outlinesLoader()) }
    private val demoRepo = DemoRideRepository()
    private val liveRepo by lazy { productionRepo() }

    /** Demo when explicitly chosen and no real session exists; a real session always wins. */
    fun rideRepository(): RideRepository = if (auth.session.value == null && prefs.demoMode) demoRepo else liveRepo

    data class BuildInfo(val versionName: String, val phoneOtpEnabled: Boolean, val mapboxNative: Boolean)

    companion object {
        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val http = SupabaseHttp(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            val auth = AuthRepository(http, SessionStore.create(app))
            return AppContainer(
                prefs = AppPrefs.create(app),
                auth = auth,
                connectivity = AndroidConnectivityObserver(app),
                location = AndroidLocationClient(app),
                productionRepo = { ProductionRideRepository(http, auth) },
                placesLoader = { PlaceIndex.parse(app.assets.open("places/baghdad.json").bufferedReader().use { it.readText() }) },
                outlinesLoader = { parseOutlines(app.assets.open("geo/service_outlines.json").bufferedReader().use { it.readText() }) },
                config = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.PHONE_OTP_ENABLED, BuildConfig.MAPBOX_NATIVE),
            )
        }

        fun parseOutlines(json: String): Map<String, Ring> {
            val root = YabahJson.parseToJsonElement(json) as JsonObject
            return root.mapValues { (_, v) ->
                (v as JsonArray).map { p -> p.jsonArray.let { doubleArrayOf(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) } }
            }
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
