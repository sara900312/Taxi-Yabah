package com.yabah.app.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.yabah.app.core.geo.Geo
import com.yabah.app.core.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Camera for the passenger map. The camera "center" is the point under the centre of the VISIBLE map area
 * (map size minus header and bottom-sheet padding), which is where the selection pin sits.
 */
/** Camera commands forwarded to a native map engine (Mapbox) when one is attached. */
sealed interface NativeCameraRequest {
    data class Move(val target: GeoPoint, val zoom: Double) : NativeCameraRequest
    data class Fit(val points: List<GeoPoint>) : NativeCameraRequest
}

@Stable
class MapCameraState(initialCenter: GeoPoint, initialZoom: Double = 12.0) {
    var center by mutableStateOf(initialCenter)
        internal set
    var zoom by mutableDoubleStateOf(initialZoom)
        internal set
    /** True while a finger is on the map; idle detection waits for this to clear. */
    var isGesturing by mutableStateOf(false)
        internal set

    // Viewport, written by the map surface on layout (pixels).
    internal var viewport by mutableStateOf(Rect.Zero)   // full map bounds
    internal var visible by mutableStateOf(Rect.Zero)    // padded visible area
    internal var density = 1f
    private var animationToken = 0

    /** Set by a native map surface; the demo canvas leaves it false and is driven by this class directly. */
    var nativeAttached = false
        internal set
    val nativeRequests = MutableSharedFlow<NativeCameraRequest>(extraBufferCapacity = 8)

    /** Native engines report their idle camera here (center = padded visible centre). */
    fun syncFromNative(center: GeoPoint, zoom: Double, gesturing: Boolean) {
        this.center = center; this.zoom = zoom; this.isGesturing = gesturing
    }
    fun attachNative(attached: Boolean) { nativeAttached = attached }
    fun setNativeGesturing(value: Boolean) { isGesturing = value }

    val minZoom = 4.0
    val maxZoom = 18.0

    internal fun worldSize(z: Double = zoom) = 256.0 * density * 2.0.pow(z)

    fun project(p: GeoPoint): Offset {
        val ws = worldSize()
        val c = visible.center
        return Offset(
            (c.x + Geo.worldX(p.lng, ws) - Geo.worldX(center.lng, ws)).toFloat(),
            (c.y + Geo.worldY(p.lat, ws) - Geo.worldY(center.lat, ws)).toFloat(),
        )
    }

    fun unproject(screen: Offset): GeoPoint {
        val ws = worldSize()
        val c = visible.center
        return GeoPoint(
            Geo.latAt(Geo.worldY(center.lat, ws) + (screen.y - c.y), ws),
            Geo.lngAt(Geo.worldX(center.lng, ws) + (screen.x - c.x), ws),
        )
    }

    internal fun panBy(delta: Offset) {
        stopAnimation()
        val c = visible.center
        center = unproject(Offset(c.x - delta.x, c.y - delta.y)).clamped()
    }

    internal fun zoomBy(factor: Float, focus: Offset) {
        stopAnimation()
        val before = unproject(focus)
        zoom = (zoom + ln(factor.toDouble()) / ln(2.0)).coerceIn(minZoom, maxZoom)
        val after = unproject(focus)
        center = GeoPoint(center.lat + (before.lat - after.lat), center.lng + (before.lng - after.lng)).clamped()
    }

    fun stopAnimation() { animationToken++ }

    fun snapTo(target: GeoPoint, targetZoom: Double = zoom) {
        stopAnimation(); center = target; zoom = targetZoom.coerceIn(minZoom, maxZoom)
    }

    suspend fun animateTo(target: GeoPoint, targetZoom: Double = zoom, durationMs: Int = 480) {
        if (nativeAttached) { nativeRequests.tryEmit(NativeCameraRequest.Move(target, targetZoom.coerceIn(minZoom, maxZoom))); return }
        val token = ++animationToken
        val from = center; val z0 = zoom; val z1 = targetZoom.coerceIn(minZoom, maxZoom)
        try {
            animate(0f, 1f, animationSpec = tween(durationMs, easing = FastOutSlowInEasing)) { t, _ ->
                if (token != animationToken) throw CancellationException("camera interrupted")
                center = GeoPoint(from.lat + (target.lat - from.lat) * t, from.lng + (target.lng - from.lng) * t)
                zoom = z0 + (z1 - z0) * t
            }
        } catch (_: CancellationException) { /* user took over */ }
    }

    /** Zoom/centre so all [points] fit inside the visible area with a margin. */
    suspend fun fitBounds(points: List<GeoPoint>, marginPx: Float = 76f * density) {
        if (points.isEmpty()) return
        if (nativeAttached) { nativeRequests.tryEmit(NativeCameraRequest.Fit(points)); return }
        if (visible.width <= 0f || visible.height <= 0f) { snapTo(points.first()); return }
        val ws0 = worldSize(0.0)
        val xs = points.map { Geo.worldX(it.lng, ws0) }; val ys = points.map { Geo.worldY(it.lat, ws0) }
        val spanX = max(xs.max() - xs.min(), 1e-9); val spanY = max(ys.max() - ys.min(), 1e-9)
        val availW = (visible.width - 2 * marginPx).coerceAtLeast(1f); val availH = (visible.height - 2 * marginPx).coerceAtLeast(1f)
        val z = min(ln(availW / spanX) / ln(2.0), ln(availH / spanY) / ln(2.0)).coerceIn(minZoom, 16.5)
        val mid = GeoPoint(Geo.latAt((ys.max() + ys.min()) / 2, ws0), Geo.lngAt((xs.max() + xs.min()) / 2, ws0))
        animateTo(mid, z)
    }

    private fun GeoPoint.clamped() = GeoPoint(lat.coerceIn(-80.0, 80.0), lng.coerceIn(-179.0, 179.0))
}
