@file:Suppress("PropertyName")

package com.yabah.app.data.dto

import kotlinx.serialization.Serializable

// All shapes below were read from the deployed sources (12 Nov 2026):
//   taxi-map-config v1, taxi-passenger-route v2, taxi-pricing v6 (taxi-pricing-v4/index.ts), taxi-trips v5,
//   private.get_trip_details(uuid) and the public.trips row type. Unknown keys are ignored.

@Serializable
data class MapTokenResponse(val ok: Boolean = false, val token: String? = null)

@Serializable
data class LineStringDto(val type: String = "", val coordinates: List<List<Double>> = emptyList())

@Serializable
data class RouteDto(val geometry: LineStringDto? = null, val distance_m: Double? = null, val duration_s: Double? = null)

@Serializable
data class RouteResponse(val ok: Boolean = false, val route: RouteDto? = null)

@Serializable
data class QuoteOptionDto(
    val vehicle_class: String? = null,
    val vehicle_class_id: Int? = null,
    val name_ar: String? = null,
    val max_passengers: Int? = null,
    val final_fare_iqd: Long? = null,
    val available_captains: Int? = null,
)

@Serializable
data class QuoteDto(
    val id: String? = null,
    val estimated_fare_iqd: Long? = null,
    val currency: String? = null,
    val distance_m: Double? = null,
    val duration_s: Double? = null,
    val expires_at: String? = null,
    val city_id: Int? = null,
    val options: List<QuoteOptionDto>? = null,
)

@Serializable
data class QuoteResponse(val ok: Boolean = false, val quote: QuoteDto? = null)

/** Subset of the public.trips row returned by request_trip / transition_trip and inside trip_details. */
@Serializable
data class TripRowDto(
    val id: String? = null,
    val status: String? = null,
    val fare_quote_id: String? = null,
    val pickup_address: String? = null,
    val pickup_latitude: Double? = null,
    val pickup_longitude: Double? = null,
    val dropoff_address: String? = null,
    val dropoff_latitude: Double? = null,
    val dropoff_longitude: Double? = null,
    val estimated_fare_iqd: Long? = null,
    val final_fare_iqd: Long? = null,
    val passenger_pays_iqd: Long? = null,
    val discount_iqd: Long? = null,
    val payment_method: String? = null,
    val cancellation_reason: String? = null,
    val requested_at: String? = null,
)

@Serializable
data class TripResponse(val ok: Boolean = false, val trip: TripRowDto? = null)

@Serializable
data class VehicleClassRefDto(val code: String? = null, val name_ar: String? = null)

@Serializable
data class CaptainDto(
    val id: String? = null,
    val full_name: String? = null,
    val avatar_path: String? = null,
    val rating_avg: Double? = null,
    val rating_count: Int? = null,
    val phone_e164: String? = null,
)

@Serializable
data class VehicleDto(
    val make: String? = null,
    val model: String? = null,
    val color: String? = null,
    val plate_number: String? = null,
    val vehicle_type: String? = null,
    val manufacture_year: Int? = null,
)

@Serializable
data class CaptainLocationDto(val latitude: Double? = null, val longitude: Double? = null, val updated_at: String? = null)

@Serializable
data class TripEventDto(val type: String? = null, val at: String? = null, val note: String? = null)

@Serializable
data class TripDetailsDto(
    val trip: TripRowDto? = null,
    val vehicle_class: VehicleClassRefDto? = null,
    val captain: CaptainDto? = null,
    val vehicle: VehicleDto? = null,
    val captain_location: CaptainLocationDto? = null,
    val events: List<TripEventDto> = emptyList(),
    val my_rating_submitted: Boolean = false,
)

@Serializable
data class TripDetailsResponse(val ok: Boolean = false, val details: TripDetailsDto? = null)

@Serializable
data class CityDto(
    val id: Int,
    val code: String,
    val name_ar: String? = null,
    val name_en: String? = null,
    val center_latitude: Double,
    val center_longitude: Double,
    val service_radius_m: Int,
)
