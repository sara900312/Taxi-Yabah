package com.yabah.app.core.auth

import com.yabah.app.core.model.Profile
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.SupabaseHttp
import com.yabah.app.core.net.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Phone sign-in failures, mapped from GoTrue; raw GoTrue text is never shown. */
enum class PhoneAuthError { InvalidPhone, OtpInvalidOrExpired, OtpRateLimited, SignInUnavailable, AccountConflict, Offline, Unknown }

class PhoneAuthException(val error: PhoneAuthError) : Exception(error.name)

/**
 * Supabase Auth (GoTrue) over REST: phone OTP sign-in, refresh, sign-out.
 * Google OAuth is intentionally absent (owner decision, 11 Nov 2026 — see docs/AUTH.md).
 */
class AuthRepository(
    private val http: SupabaseHttp,
    private val store: SessionStore,
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<Session?> = _session.asStateFlow()
    private val refreshLock = Mutex()

    suspend fun sendOtp(e164: String) {
        val body = buildJsonObject { put("phone", e164); put("create_user", true); put("channel", "sms") }
        val r = try { http.post("/auth/v1/otp", body, bearer = null) } catch (e: ApiException) { throw e.toPhone() }
        if (r.status !in 200..299) throw PhoneAuthException(mapGoTrue(r.status, r.body as? JsonObject))
    }

    suspend fun verifyOtp(e164: String, code: String): Session {
        val body = buildJsonObject { put("type", "sms"); put("phone", e164); put("token", code) }
        val r = try { http.post("/auth/v1/verify", body, bearer = null) } catch (e: ApiException) { throw e.toPhone() }
        if (r.status !in 200..299) throw PhoneAuthException(mapGoTrue(r.status, r.body as? JsonObject))
        val s = parseSession(r.body as? JsonObject) ?: throw PhoneAuthException(PhoneAuthError.Unknown)
        store.save(s); _session.value = s
        return s
    }

    /** A valid access token, refreshing first when it is about to expire. */
    suspend fun accessToken(): String {
        val current = _session.value ?: throw ApiException(ApiFailure.session)
        if (!current.expiresSoon(nowEpochSec())) return current.accessToken
        return refresh(current).accessToken
    }

    /** Called after the server answered INVALID_SESSION for a token we believed valid. */
    suspend fun refreshAfterRejection(rejectedToken: String): String {
        val current = _session.value ?: throw ApiException(ApiFailure.session)
        if (current.accessToken != rejectedToken) return current.accessToken // someone already refreshed
        return refresh(current).accessToken
    }

    private suspend fun refresh(stale: Session): Session = refreshLock.withLock {
        val now = _session.value ?: throw ApiException(ApiFailure.session)
        if (now.accessToken != stale.accessToken) return now // single-flight
        val body = buildJsonObject { put("refresh_token", now.refreshToken) }
        val r = http.post("/auth/v1/token?grant_type=refresh_token", body, bearer = null) // Offline propagates, session kept
        if (r.status in 400..499) { clearLocal(); throw ApiException(ApiFailure.session) }
        val s = parseSession(r.body as? JsonObject) ?: throw ApiException(ApiFailure.invalidResponse)
        store.save(s); _session.value = s
        s
    }

    suspend fun signOut() {
        val token = _session.value?.accessToken
        clearLocal()
        if (token != null) runCatching { http.post("/auth/v1/logout", JsonObject(emptyMap()), bearer = token) }
    }

    fun clearLocal() { store.clear(); _session.value = null }

    // ---- profile (RLS: own row) ------------------------------------------------------------------------
    suspend fun loadProfile(): Profile? {
        val s = _session.value ?: throw ApiException(ApiFailure.session)
        val r = http.get("/rest/v1/profiles?select=id,full_name,phone_e164,phone_verified_at,account_status&id=eq.${s.userId}", accessToken())
        if (r.status == 401) throw ApiException(ApiFailure.session)
        if (r.status !in 200..299) throw ApiException(ApiFailure.server("PROFILE_UNAVAILABLE", r.status))
        val row = (r.body as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        return Profile(
            id = row["id"].stringOrNull() ?: s.userId,
            fullName = row["full_name"].stringOrNull()?.takeIf { it.isNotBlank() },
            phoneE164 = row["phone_e164"].stringOrNull(),
            phoneVerified = row["phone_verified_at"].stringOrNull() != null,
            accountStatus = row["account_status"].stringOrNull(),
        )
    }

    suspend fun saveName(name: String) {
        val s = _session.value ?: throw ApiException(ApiFailure.session)
        val body = buildJsonObject { put("full_name", name.trim()) }
        val r = http.patch("/rest/v1/profiles?id=eq.${s.userId}", body, accessToken(), mapOf("Prefer" to "return=minimal"))
        if (r.status !in 200..299) throw ApiException(ApiFailure.server("PROFILE_UPDATE_FAILED", r.status))
    }

    private fun parseSession(o: JsonObject?): Session? {
        o ?: return null
        val access = o["access_token"].stringOrNull() ?: return null
        val refresh = o["refresh_token"].stringOrNull() ?: return null
        val user = o["user"] as? JsonObject ?: return null
        val expiresAt = (o["expires_at"] as? JsonPrimitive)?.longOrNull
            ?: ((o["expires_in"] as? JsonPrimitive)?.longOrNull?.let { nowEpochSec() + it })
            ?: return null
        return Session(access, refresh, expiresAt, user["id"].stringOrNull() ?: return null, user["phone"].stringOrNull())
    }

    private fun ApiException.toPhone() =
        PhoneAuthException(if (failure.kind == ApiFailure.Kind.Offline) PhoneAuthError.Offline else PhoneAuthError.Unknown)

    companion object {
        fun mapGoTrue(status: Int, body: JsonObject?): PhoneAuthError {
            val code = (body?.get("error_code").stringOrNull() ?: body?.get("error").stringOrNull() ?: "").lowercase()
            val msg = (body?.get("msg").stringOrNull() ?: body?.get("message").stringOrNull()
                ?: body?.get("error_description").stringOrNull() ?: "")
            return when {
                status == 429 || code.contains("rate_limit") -> PhoneAuthError.OtpRateLimited
                code.contains("otp_expired") || code.contains("invalid_grant") ||
                    msg.contains("expired", true) || (msg.contains("invalid", true) && msg.contains("token", true)) -> PhoneAuthError.OtpInvalidOrExpired
                code.contains("phone_provider_disabled") || code.contains("sms_send_failed") ||
                    code.contains("provider_disabled") || msg.contains("Unsupported phone provider", true) -> PhoneAuthError.SignInUnavailable
                msg.contains("PHONE_ALREADY_REGISTERED") || msg.contains("Database error saving new user", true) -> PhoneAuthError.AccountConflict
                code.contains("validation_failed") || msg.contains("phone", true) && msg.contains("invalid", true) -> PhoneAuthError.InvalidPhone
                else -> PhoneAuthError.Unknown
            }
        }
    }
}
