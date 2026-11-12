package com.yabah.app.data.repo

import com.yabah.app.core.model.City
import com.yabah.app.core.model.FareQuote
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.LabeledPoint
import com.yabah.app.core.model.RoutePreview
import com.yabah.app.core.model.TripDetails
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure

/**
 * DEMO data source. Makes NO network request of any kind. It can show the city outline and local place search,
 * but it refuses routes, prices and bookings so nothing can look like a server answer.
 */
class DemoRideRepository : RideRepository {
    override val isDemo = true

    /** Local snapshot of Baghdad's server config (read 12 Nov 2026), labelled DEMO wherever it is shown. */
    override suspend fun serviceCities(): List<City> = listOf(DEMO_BAGHDAD)

    override suspend fun mapPublicToken(): String = throw ApiException(ApiFailure.demo)
    override suspend fun route(pickup: GeoPoint, dropoff: GeoPoint, cityId: Int?): RoutePreview = throw ApiException(ApiFailure.demo)
    override suspend fun quote(pickup: LabeledPoint, dropoff: LabeledPoint, cityId: Int): FareQuote = throw ApiException(ApiFailure.demo)
    override suspend fun requestTrip(quoteId: String, vehicleClass: String, passengerCount: Int): String = throw ApiException(ApiFailure.demo)
    override suspend fun activeTrip(): TripDetails? = null
    override suspend fun tripDetails(tripId: String): TripDetails = throw ApiException(ApiFailure.demo)
    override suspend fun cancelTrip(tripId: String, reason: String?) = throw ApiException(ApiFailure.demo)
    override suspend fun rateTrip(tripId: String, stars: Int, comment: String?) = throw ApiException(ApiFailure.demo)

    companion object {
        val DEMO_BAGHDAD = City(1, "baghdad", "بغداد", "Baghdad", GeoPoint(33.3152, 44.3661), 30_000)

        /** Catalogue snapshot (public.vehicle_classes, 12 Nov 2026) shown only as labelled demo content, never priced. */
        val DEMO_CLASSES = listOf(
            DemoClass("taxi", "تكسي", "Taxi", 4),
            DemoClass("economy", "أوفر", "Economy", 4),
            DemoClass("comfort", "مريح", "Comfort", 4),
            DemoClass("family", "عائلي", "Family", 7),
            DemoClass("tuktuk", "تكتك", "Tuk-tuk", 2),
        )
    }
}

data class DemoClass(val code: String, val nameAr: String, val nameEn: String, val maxPassengers: Int)
