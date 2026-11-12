package com.yabah.app.map

import com.yabah.app.core.geo.Ring
import com.yabah.app.core.model.GeoPoint

enum class MarkerKind { Pickup, Dropoff, Captain }

data class MapMarker(val point: GeoPoint, val kind: MarkerKind)

enum class RouteStyle {
    /** Verified road geometry from the server. */
    ServerRoad,
    /** Demo-only illustration between two points. Never presented as a road. */
    DemoStraight,
}

data class MapRoute(val points: List<GeoPoint>, val style: RouteStyle)

data class ServiceOverlay(val outline: Ring?, val center: GeoPoint, val radiusM: Int)
