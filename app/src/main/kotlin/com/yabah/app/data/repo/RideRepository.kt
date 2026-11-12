package com.yabah.app.data.repo

import com.yabah.app.core.model.City
import com.yabah.app.core.model.FareQuote
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.LabeledPoint
import com.yabah.app.core.model.RoutePreview
import com.yabah.app.core.model.TripDetails

/**
 * Passenger ride operations. Two implementations:
 *  - [ProductionRideRepository]: authenticated calls to the existing Supabase edge functions.
 *  - [DemoRideRepository]: fully offline; refuses every operation that would need the server.
 * The server is authoritative for service area, routes, prices, trip state and authorisation.
 */
interface RideRepository {
    val isDemo: Boolean

    /** Cities with rides enabled, as configured on the server (never assumed from local presets in production). */
    suspend fun serviceCities(): List<City>

    /** Mapbox PUBLIC (pk.) token for map rendering, from taxi-map-config. */
    suspend fun mapPublicToken(): String

    /** Road route preview (taxi-passenger-route). Not a fare, not a booking. */
    suspend fun route(pickup: GeoPoint, dropoff: GeoPoint, cityId: Int?): RoutePreview

    /** Standard-taxi fare quote (taxi-pricing quote_fare, offer=standard_taxi). Inserts a quote row server-side. */
    suspend fun quote(pickup: LabeledPoint, dropoff: LabeledPoint, cityId: Int): FareQuote

    /** request_trip. Throws ApiException(NoAnswer) when the outcome is unknown: callers must reconcile, not resend. */
    suspend fun requestTrip(quoteId: String, vehicleClass: String, passengerCount: Int): String

    suspend fun activeTrip(): TripDetails?
    suspend fun tripDetails(tripId: String): TripDetails
    suspend fun cancelTrip(tripId: String, reason: String?)
    suspend fun rateTrip(tripId: String, stars: Int, comment: String?)
}
