package com.yabah.app.data.repo

import com.yabah.app.core.auth.AuthRepository
import com.yabah.app.core.model.CaptainInfo
import com.yabah.app.core.model.CaptainLocation
import com.yabah.app.core.model.City
import com.yabah.app.core.model.FareQuote
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.LabeledPoint
import com.yabah.app.core.model.RideOption
import com.yabah.app.core.model.RoutePreview
import com.yabah.app.core.model.TripDetails
import com.yabah.app.core.model.TripEvent
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.model.VehicleInfo
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.HttpResult
import com.yabah.app.core.net.SupabaseHttp
import com.yabah.app.core.net.YabahJson
import com.yabah.app.core.net.requireOk
import com.yabah.app.data.dto.CityDto
import com.yabah.app.data.dto.MapTokenResponse
import com.yabah.app.data.dto.QuoteResponse
import com.yabah.app.data.dto.RouteResponse
import com.yabah.app.data.dto.TripDetailsDto
import com.yabah.app.data.dto.TripDetailsResponse
import com.yabah.app.data.dto.TripResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class ProductionRideRepository(
    private val http: SupabaseHttp,
    private val auth: AuthRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : RideRepository {
    override val isDemo = false

    /** Calls an edge function as the signed-in user; refreshes once if the server rejects the token. */
    private suspend fun invoke(function: String, body: JsonObject): Pair<JsonObject, HttpResult> {
        var token = auth.accessToken()
        repeat(2) { attempt ->
            val result = http.post("/functions/v1/$function", body, token)
            try {
                return result.requireOk() to result
            } catch (e: ApiException) {
                // INVALID_SESSION is decided before any write, so one retry with a refreshed token is safe.
                if (attempt == 0 && e.failure.isSessionProblem) token = auth.refreshAfterRejection(token) else throw e
            }
        }
        throw ApiException(ApiFailure.session)
    }

    private fun <T> decode(serializer: KSerializer<T>, obj: JsonObject): T = try {
        YabahJson.decodeFromJsonElement(serializer, obj)
    } catch (_: Exception) {
        throw ApiException(ApiFailure.invalidResponse)
    }

    override suspend fun serviceCities(): List<City> {
        val r = http.get(
            "/rest/v1/cities?select=id,code,name_ar,name_en,center_latitude,center_longitude,service_radius_m" +
                "&is_active=eq.true&rides_enabled=eq.true&order=id",
            auth.accessToken(),
        )
        if (r.status == 401) throw ApiException(ApiFailure.session)
        if (r.status !in 200..299 || r.body !is JsonArray) throw ApiException(ApiFailure.server("CITIES_UNAVAILABLE", r.status))
        val rows = try { YabahJson.decodeFromJsonElement(ListSerializer(CityDto.serializer()), r.body) } catch (_: Exception) {
            throw ApiException(ApiFailure.invalidResponse)
        }
        return rows.filter { it.service_radius_m > 0 }.map {
            City(it.id, it.code, it.name_ar ?: it.code, it.name_en ?: it.code, GeoPoint(it.center_latitude, it.center_longitude), it.service_radius_m)
        }
    }

    override suspend fun mapPublicToken(): String {
        val (obj, _) = invoke("taxi-map-config", buildJsonObject { put("action", "public_map_token") })
        val token = decode(MapTokenResponse.serializer(), obj).token
        // Defence in depth: only ever accept a PUBLIC Mapbox token.
        if (token == null || !PUBLIC_TOKEN.matches(token)) throw ApiException(ApiFailure.server("MAPBOX_PUBLIC_TOKEN_UNAVAILABLE", null))
        return token
    }

    override suspend fun route(pickup: GeoPoint, dropoff: GeoPoint, cityId: Int?): RoutePreview {
        val body = buildJsonObject {
            put("pickup", buildJsonObject { put("lat", pickup.lat); put("lng", pickup.lng) })
            put("dropoff", buildJsonObject { put("lat", dropoff.lat); put("lng", dropoff.lng) })
            if (cityId != null) put("city_id", cityId)
        }
        val (obj, _) = invoke("taxi-passenger-route", body)
        val route = decode(RouteResponse.serializer(), obj).route ?: throw ApiException(ApiFailure.invalidResponse)
        val geometry = route.geometry
        val points = geometry?.coordinates?.mapNotNull { c -> if (c.size >= 2) GeoPoint(c[1], c[0]) else null }.orEmpty()
        val distance = route.distance_m; val duration = route.duration_s
        // Never draw anything that is not a valid server LineString (no straight-line fallback).
        if (geometry?.type != "LineString" || points.size < 2 || points.any { !it.isValid } ||
            distance == null || distance <= 0 || duration == null || duration <= 0
        ) throw ApiException(ApiFailure.invalidResponse)
        return RoutePreview(points, distance.toInt(), duration.toInt())
    }

    override suspend fun quote(pickup: LabeledPoint, dropoff: LabeledPoint, cityId: Int): FareQuote {
        val body = buildJsonObject {
            put("action", "quote_fare")
            put("offer", "standard_taxi")
            put("city_id", cityId)
            put("pickup_address", pickup.label.take(500))
            put("dropoff_address", dropoff.label.take(500))
            put("pickup_latitude", pickup.point.lat)
            put("pickup_longitude", pickup.point.lng)
            put("dropoff_latitude", dropoff.point.lat)
            put("dropoff_longitude", dropoff.point.lng)
        }
        val (obj, result) = invoke("taxi-pricing", body)
        val q = decode(QuoteResponse.serializer(), obj).quote ?: throw ApiException(ApiFailure.invalidResponse)
        val id = q.id; val fare = q.estimated_fare_iqd; val expires = q.expires_at
        if (id == null || !UUID.matches(id) || fare == null || fare <= 0 || expires == null) throw ApiException(ApiFailure.invalidResponse)
        val expiresServerMs = runCatching { Instant.parse(expires).toEpochMilli() }.getOrElse { throw ApiException(ApiFailure.invalidResponse) }
        val receivedAt = nowMs()
        // Convert server-clock expiry into device-clock expiry using the server's Date header.
        val ttl = expiresServerMs - (result.serverDateMs ?: receivedAt)
        val option = q.options?.firstOrNull { it.vehicle_class == "taxi" }?.let {
            val optionFare = it.final_fare_iqd ?: throw ApiException(ApiFailure.invalidResponse)
            RideOption(it.vehicle_class ?: "taxi", it.vehicle_class_id, it.name_ar, it.max_passengers, optionFare, it.available_captains)
        }
        return FareQuote(id, fare, q.currency ?: "IQD", q.distance_m?.toInt() ?: 0, q.duration_s?.toInt() ?: 0,
            receivedAt + ttl.coerceAtLeast(0), q.city_id, option)
    }

    override suspend fun requestTrip(quoteId: String, vehicleClass: String, passengerCount: Int): String {
        val body = buildJsonObject {
            put("action", "request_trip")
            put("fare_quote_id", quoteId)
            put("vehicle_class", vehicleClass)
            put("payment_method", "cash") // only cash is enabled for this flow
            put("passenger_count", passengerCount)
        }
        val (obj, _) = invoke("taxi-trips", body)
        return decode(TripResponse.serializer(), obj).trip?.id ?: throw ApiException(ApiFailure.invalidResponse)
    }

    override suspend fun activeTrip(): TripDetails? {
        val (obj, result) = invoke("taxi-trips", buildJsonObject { put("action", "active_trip") })
        if (obj["details"] == null || obj["details"] is JsonNull) return null
        val dto = decode(TripDetailsResponse.serializer(), obj).details ?: return null
        return dto.toModel(result.serverDateMs ?: nowMs())
    }

    override suspend fun tripDetails(tripId: String): TripDetails {
        val (obj, result) = invoke("taxi-trips", buildJsonObject { put("action", "trip_details"); put("trip_id", tripId) })
        val dto = decode(TripDetailsResponse.serializer(), obj).details ?: throw ApiException(ApiFailure.invalidResponse)
        return dto.toModel(result.serverDateMs ?: nowMs())
    }

    override suspend fun cancelTrip(tripId: String, reason: String?) {
        val body = buildJsonObject {
            put("action", "transition_trip"); put("trip_id", tripId); put("trip_action", "cancel")
            if (!reason.isNullOrBlank()) put("reason", reason)
        }
        decode(TripResponse.serializer(), invoke("taxi-trips", body).first)
    }

    override suspend fun rateTrip(tripId: String, stars: Int, comment: String?) {
        val body = buildJsonObject {
            put("action", "rate_trip"); put("trip_id", tripId); put("stars", stars)
            if (!comment.isNullOrBlank()) put("comment", comment)
        }
        invoke("taxi-trips", body)
    }

    companion object {
        val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        val PUBLIC_TOKEN = Regex("^pk\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+$")

        fun parseTime(value: String?): Long? = value?.let { runCatching { Instant.parse(normalizeTs(it)).toEpochMilli() }.getOrNull() }

        /** Postgres jsonb timestamps look like 2026-11-12T08:00:00.123456+00:00. */
        private fun normalizeTs(v: String): String = if (v.endsWith("+00:00")) v.dropLast(6) + "Z" else v
    }
}

internal fun TripDetailsDto.toModel(serverNowMs: Long): TripDetails {
    val t = trip ?: throw ApiException(ApiFailure.invalidResponse)
    val id = t.id ?: throw ApiException(ApiFailure.invalidResponse)
    val loc = captain_location?.let { l ->
        if (l.latitude != null && l.longitude != null) CaptainLocation(GeoPoint(l.latitude, l.longitude), ProductionRideRepository.parseTime(l.updated_at)) else null
    }
    return TripDetails(
        id = id,
        status = TripStatus.fromWire(t.status),
        fareQuoteId = t.fare_quote_id,
        pickupAddress = t.pickup_address.orEmpty(),
        pickup = GeoPoint(t.pickup_latitude ?: 0.0, t.pickup_longitude ?: 0.0),
        dropoffAddress = t.dropoff_address.orEmpty(),
        dropoff = GeoPoint(t.dropoff_latitude ?: 0.0, t.dropoff_longitude ?: 0.0),
        estimatedFareIqd = t.estimated_fare_iqd,
        finalFareIqd = t.final_fare_iqd,
        passengerPaysIqd = t.passenger_pays_iqd,
        paymentMethod = t.payment_method,
        cancellationReason = t.cancellation_reason,
        vehicleClassNameAr = vehicle_class?.name_ar,
        captain = captain?.let { CaptainInfo(it.full_name, it.rating_avg, it.rating_count, it.phone_e164) },
        vehicle = vehicle?.let { VehicleInfo(it.make, it.model, it.color, it.plate_number, it.manufacture_year) },
        captainLocation = loc,
        events = events.map { TripEvent(it.type.orEmpty(), ProductionRideRepository.parseTime(it.at)) },
        myRatingSubmitted = my_rating_submitted,
        serverNowMs = serverNowMs,
    )
}
