package com.yabah.app.core.net

import com.yabah.app.R
import com.yabah.app.core.util.UiText

/**
 * Every failure the app can show, normalised. [code] is the server's machine code when there is one
 * (e.g. OUTSIDE_SERVICE_AREA); raw server `message` text is never shown to people.
 */
data class ApiFailure(
    val kind: Kind,
    val code: String? = null,
    val httpStatus: Int? = null,
) {
    enum class Kind {
        /** Request never reached the server (no network, DNS, connect refused). Definitely not applied. */
        Offline,
        /** Request may have reached the server but no answer arrived. Outcome UNKNOWN for writes. */
        NoAnswer,
        /** Server answered with an error code. */
        Server,
        /** Server answered but the payload did not match the contract. */
        InvalidResponse,
        /** No usable session (missing / refresh failed / INVALID_SESSION). */
        Session,
        /** The operation is not available in DEMO mode (no server contact is allowed). */
        DemoUnsupported,
    }

    val isSessionProblem: Boolean
        get() = kind == Kind.Session || code == "INVALID_SESSION" || code == "AUTH_REQUIRED"

    companion object {
        fun server(code: String, status: Int?) = ApiFailure(Kind.Server, code, status)
        val offline = ApiFailure(Kind.Offline)
        val noAnswer = ApiFailure(Kind.NoAnswer)
        val invalidResponse = ApiFailure(Kind.InvalidResponse)
        val session = ApiFailure(Kind.Session, "INVALID_SESSION")
        val demo = ApiFailure(Kind.DemoUnsupported)
    }
}

class ApiException(val failure: ApiFailure) : Exception(failure.code ?: failure.kind.name)

/** Arabic-first human message for a failure. Codes come from the verified edge-function / RPC sources. */
fun ApiFailure.message(): UiText {
    when (kind) {
        ApiFailure.Kind.Offline -> return UiText.Res(R.string.err_offline)
        ApiFailure.Kind.NoAnswer -> return UiText.Res(R.string.err_timeout)
        ApiFailure.Kind.InvalidResponse -> return UiText.Res(R.string.err_invalid_response)
        ApiFailure.Kind.Session -> return UiText.Res(R.string.err_invalid_session)
        ApiFailure.Kind.DemoUnsupported -> return UiText.Res(R.string.demo_cannot_book)
        ApiFailure.Kind.Server -> Unit
    }
    val res = when (code) {
        "AUTH_REQUIRED", "INVALID_SESSION" -> R.string.err_invalid_session
        "ACCOUNT_NOT_ACTIVE" -> R.string.err_account_not_active
        "PASSENGER_ROLE_REQUIRED" -> R.string.err_passenger_role_required
        "OUTSIDE_SERVICE_AREA" -> R.string.err_outside_service_area
        "DROPOFF_OUTSIDE_SERVICE_AREA" -> R.string.err_dropoff_outside
        "CROSS_CITY_NOT_SUPPORTED" -> R.string.err_cross_city
        "ROUTE_NOT_FOUND", "MAPBOX_NO_ROUTE" -> R.string.err_route_not_found
        "ROUTE_TOO_SHORT" -> R.string.err_route_too_short
        "QUOTE_RATE_LIMITED" -> R.string.err_quote_rate_limited
        "MAPBOX_DAILY_LIMIT_REACHED" -> R.string.err_daily_limit
        "MAPBOX_ROUTE_UNAVAILABLE", "MAPBOX_NOT_CONFIGURED", "MAPBOX_ACCESS_TOKEN_NOT_CONFIGURED",
        "MAPBOX_BUDGET_CHECK_UNAVAILABLE", "MAPBOX_RATE_LIMITED", "MAPBOX_ROUTE_FAILED",
        "MAPBOX_ROUTE_INVALID_RESPONSE", "MAPBOX_ROUTE_INVALID_VALUES", "MAPBOX_TOKEN_NOT_CONFIGURED",
        -> R.string.err_route_unavailable
        "DYNAMIC_PRICING_NOT_CONFIGURED", "DYNAMIC_PRICING_DISABLED" -> R.string.err_pricing_not_configured
        "VEHICLE_CLASS_NOT_AVAILABLE", "VEHICLE_CLASS_NOT_IN_QUOTE", "INVALID_VEHICLE_CLASS" -> R.string.err_vehicle_class_unavailable
        "FARE_QUOTE_EXPIRED", "FARE_QUOTE_NOT_FOUND", "VALID_FARE_QUOTE_REQUIRED" -> R.string.err_quote_expired
        "FARE_QUOTE_ALREADY_USED" -> R.string.err_quote_used
        "PRICE_CHANGED" -> R.string.err_price_changed
        "PASSENGER_HAS_ACTIVE_TRIP" -> R.string.err_has_active_trip
        "PAYMENT_METHOD_DISABLED", "INVALID_PAYMENT_METHOD" -> R.string.err_payment_disabled
        "MAPBOX_PUBLIC_TOKEN_UNAVAILABLE", "MAP_BOOTSTRAP_UNAVAILABLE" -> R.string.err_map_token_missing
        "TRIP_CANNOT_BE_CANCELLED", "INVALID_TRIP_TRANSITION" -> R.string.err_cancel_not_allowed
        "ALREADY_RATED" -> R.string.err_already_rated
        "RATING_WINDOW_CLOSED" -> R.string.err_rating_window_closed
        "TRIP_NOT_FOUND", "NOT_TRIP_PARTICIPANT" -> R.string.err_trip_not_found
        else -> null
    }
    return if (res != null) UiText.Res(res) else UiText.Res(R.string.err_server, listOf(code ?: "HTTP ${httpStatus ?: "?"}"))
}
