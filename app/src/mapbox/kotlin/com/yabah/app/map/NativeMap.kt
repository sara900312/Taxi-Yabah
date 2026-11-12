package com.yabah.app.map

// =====================================================================================================
//  UNVERIFIED IN CI: this source set is compiled ONLY when the build machine has MAPBOX_DOWNLOADS_TOKEN
//  (a Mapbox secret token with DOWNLOADS:READ). It was written against the Mapbox Maps SDK v11 Compose
//  extension API (MapboxMap / MapViewportState / PolylineAnnotation / CircleAnnotation) but has NOT been
//  compiled or run yet. See docs/MAPBOX.md for the exact verification steps on a real device.
// =====================================================================================================

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapIdleCallback
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.android.gestures.MoveGestureDetector
import com.yabah.app.core.model.GeoPoint

object NativeMap {
    const val isAvailable: Boolean = true
}

@Composable
fun NativeMapSurface(
    camera: MapCameraState,
    markers: List<MapMarker>,
    route: MapRoute?,
    overlay: ServiceOverlay?,
    contentPadding: PaddingValues,
    onTap: (GeoPoint) -> Unit,
    publicToken: String,
    modifier: Modifier,
) {
    // Only a server-issued PUBLIC token reaches this point (checked in ProductionRideRepository).
    remember(publicToken) { MapboxOptions.accessToken = publicToken; publicToken }
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val padding = with(density) {
        EdgeInsets(
            contentPadding.calculateTopPadding().toPx().toDouble(),
            contentPadding.calculateLeftPadding(dir).toPx().toDouble(),
            contentPadding.calculateBottomPadding().toPx().toDouble(),
            contentPadding.calculateRightPadding(dir).toPx().toDouble(),
        )
    }
    val viewport = rememberMapViewportState {
        setCameraOptions { center(Point.fromLngLat(camera.center.lng, camera.center.lat)); zoom(camera.zoom); padding(padding) }
    }
    DisposableEffect(camera) { camera.attachNative(true); onDispose { camera.attachNative(false) } }

    // App-driven camera moves (GPS, search, fit route) → native animations, honouring the sheet padding.
    LaunchedEffect(camera, padding) {
        camera.nativeRequests.collect { req ->
            when (req) {
                is NativeCameraRequest.Move -> viewport.easeTo(
                    cameraOptions { center(Point.fromLngLat(req.target.lng, req.target.lat)); zoom(req.zoom); padding(padding) },
                    MapAnimationOptions.mapAnimationOptions { duration(450) },
                )
                is NativeCameraRequest.Fit -> {
                    val fit = viewport.cameraForCoordinates(req.points.map { Point.fromLngLat(it.lng, it.lat) }, padding, maxZoom = 16.5)
                    viewport.easeTo(fit, MapAnimationOptions.mapAnimationOptions { duration(600) })
                }
            }
        }
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = viewport,
        onMapClickListener = { p -> onTap(GeoPoint(p.latitude(), p.longitude())); true },
    ) {
        MapEffect(Unit) { mapView ->
            mapView.gestures.addOnMoveListener(object : OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) = camera.setNativeGesturing(true)
                override fun onMove(detector: MoveGestureDetector): Boolean = false
                override fun onMoveEnd(detector: MoveGestureDetector) = camera.setNativeGesturing(false)
            })
            mapView.mapboxMap.subscribeMapIdle(MapIdleCallback {
                val c = mapView.mapboxMap.cameraState
                camera.syncFromNative(GeoPoint(c.center.latitude(), c.center.longitude()), c.zoom, gesturing = false)
            })
        }
        route?.takeIf { it.style == RouteStyle.ServerRoad && it.points.size >= 2 }?.let { r ->
            PolylineAnnotation(points = r.points.map { Point.fromLngLat(it.lng, it.lat) }) {
                lineColor = Color(0xFF0DAA9B)
                lineWidth = 6.0
            }
        }
        markers.forEach { m ->
            CircleAnnotation(point = Point.fromLngLat(m.point.lng, m.point.lat)) {
                circleRadius = if (m.kind == MarkerKind.Captain) 10.0 else 8.0
                circleColor = when (m.kind) { MarkerKind.Pickup -> Color(0xFF0DAA9B); MarkerKind.Dropoff -> Color(0xFFE8B65A); MarkerKind.Captain -> Color(0xFF101B2C) }
                circleStrokeColor = Color.White
                circleStrokeWidth = 3.0
            }
        }
    }
}
