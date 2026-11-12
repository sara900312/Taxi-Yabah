package com.yabah.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double) {
    val isValid: Boolean get() = lat.isFinite() && lng.isFinite() && kotlin.math.abs(lat) <= 90 && kotlin.math.abs(lng) <= 180
}

/** A service city as configured on the server (`public.cities`). */
data class City(
    val id: Int,
    val code: String,
    val nameAr: String,
    val nameEn: String,
    val center: GeoPoint,
    val serviceRadiusM: Int,
)

/** A point the passenger chose, with the human label sent to pricing as the address text. */
data class LabeledPoint(val point: GeoPoint, val label: String)

data class RoutePreview(
    val points: List<GeoPoint>,
    val distanceM: Int,
    val durationS: Int,
)

data class RideOption(
    val vehicleClass: String,
    val vehicleClassId: Int?,
    val nameAr: String?,
    val maxPassengers: Int?,
    val fareIqd: Long,
    val availableCaptains: Int?,
)

/**
 * A fare quote exactly as issued by `taxi-pricing`. [expiresAtLocalMs] is converted to the device clock using
 * the server's `Date` header, so a wrong phone clock cannot extend or shorten a quote.
 */
data class FareQuote(
    val id: String,
    val estimatedFareIqd: Long,
    val currency: String,
    val distanceM: Int,
    val durationS: Int,
    val expiresAtLocalMs: Long,
    val cityId: Int?,
    val option: RideOption?,
) {
    /** The amount request_trip will charge for the offered class (single-fare contract), else the estimate. */
    val displayFareIqd: Long get() = option?.fareIqd ?: estimatedFareIqd
}

enum class TripStatus(val wire: String) {
    Requested("requested"), Accepted("accepted"), Arrived("arrived"), InProgress("in_progress"),
    Completed("completed"), Cancelled("cancelled"), Unknown("");

    val isActive: Boolean get() = this == Requested || this == Accepted || this == Arrived || this == InProgress
    /** Mirrors private.transition_trip: participants may cancel only in these states. */
    val passengerMayCancel: Boolean get() = this == Requested || this == Accepted || this == Arrived

    companion object {
        fun fromWire(value: String?): TripStatus = entries.firstOrNull { it.wire == value && it != Unknown } ?: Unknown
    }
}

data class CaptainInfo(
    val fullName: String?,
    val ratingAvg: Double?,
    val ratingCount: Int?,
    val phoneE164: String?,
)

data class VehicleInfo(
    val make: String?,
    val model: String?,
    val color: String?,
    val plateNumber: String?,
    val manufactureYear: Int?,
)

data class CaptainLocation(val point: GeoPoint, val updatedAtMs: Long?)

data class TripEvent(val type: String, val atMs: Long?)

data class TripDetails(
    val id: String,
    val status: TripStatus,
    val fareQuoteId: String?,
    val pickupAddress: String,
    val pickup: GeoPoint,
    val dropoffAddress: String,
    val dropoff: GeoPoint,
    val estimatedFareIqd: Long?,
    val finalFareIqd: Long?,
    val passengerPaysIqd: Long?,
    val paymentMethod: String?,
    val cancellationReason: String?,
    val vehicleClassNameAr: String?,
    val captain: CaptainInfo?,
    val vehicle: VehicleInfo?,
    val captainLocation: CaptainLocation?,
    val events: List<TripEvent>,
    val myRatingSubmitted: Boolean,
    /** Server clock at the time of the response (from the `Date` header), for staleness checks. */
    val serverNowMs: Long,
)

data class Profile(
    val id: String,
    val fullName: String?,
    val phoneE164: String?,
    val phoneVerified: Boolean,
    val accountStatus: String?,
)
