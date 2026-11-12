package com.yabah.app.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val YabahJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

data class HttpResult(val status: Int, val body: JsonElement, val serverDateMs: Long?)

/**
 * Minimal Supabase transport: Edge Functions, PostgREST and GoTrue over OkHttp.
 * Only the project URL and the PUBLISHABLE key are known to the client; the user's JWT is attached per call.
 */
class SupabaseHttp(
    private val baseUrl: String,
    private val publishableKey: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun post(path: String, body: JsonElement, bearer: String?, extraHeaders: Map<String, String> = emptyMap()): HttpResult =
        execute(path, "POST", body, bearer, extraHeaders)

    suspend fun get(path: String, bearer: String?): HttpResult = execute(path, "GET", null, bearer, emptyMap())

    suspend fun patch(path: String, body: JsonElement, bearer: String?, extraHeaders: Map<String, String> = emptyMap()): HttpResult =
        execute(path, "PATCH", body, bearer, extraHeaders)

    private suspend fun execute(
        path: String, method: String, body: JsonElement?, bearer: String?, extraHeaders: Map<String, String>,
    ): HttpResult {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
            .header("X-Client-Info", "yabah-android-native")
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        val payload = body?.let { YabahJson.encodeToString(JsonElement.serializer(), it).toRequestBody(jsonMedia) }
        builder.method(method, payload)
        val response = try {
            client.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw ApiException(classify(e))
        }
        return response.use { r ->
            val text = try { r.body?.string().orEmpty() } catch (e: IOException) { throw ApiException(ApiFailure.noAnswer) }
            val parsed = if (text.isBlank()) JsonNull else try {
                YabahJson.parseToJsonElement(text)
            } catch (_: Exception) {
                // Gateway / HTML error pages: keep the status, the payload is not part of any contract.
                if (r.isSuccessful) throw ApiException(ApiFailure.invalidResponse) else JsonNull
            }
            HttpResult(r.code, parsed, r.headers.getDate("Date")?.time)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // never silently re-send writes such as request_trip
            .build()

        /** Failures that prove the request never left the device are Offline; anything else is NoAnswer. */
        fun classify(e: IOException): ApiFailure = when (e) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException, is SSLHandshakeException -> ApiFailure.offline
            else -> ApiFailure.noAnswer
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/**
 * Turns an edge-function answer into either its JSON object (ok=true) or an [ApiException] carrying the
 * server's machine code. Contract (verified in source): `{ ok: false, error: "CODE", message? }`.
 */
fun HttpResult.requireOk(): JsonObject {
    val obj = body as? JsonObject
    val ok = (obj?.get("ok") as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
    if (status in 200..299 && obj != null && ok == true) return obj
    val code = (obj?.get("error") as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    if (code != null) {
        val failure = if (code == "INVALID_SESSION" || code == "AUTH_REQUIRED") ApiFailure.session else ApiFailure.server(code, status)
        throw ApiException(failure)
    }
    // verify_jwt=true gateway rejections arrive without our envelope.
    if (status == 401) throw ApiException(ApiFailure.session)
    if (status in 200..299) throw ApiException(ApiFailure.invalidResponse)
    throw ApiException(ApiFailure.server("HTTP_$status", status))
}

internal fun JsonElement?.stringOrNull(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.str(key: String): String? = this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
