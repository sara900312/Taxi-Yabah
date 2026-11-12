package com.yabah.app.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.data.places.PlaceIndex

/**
 * Passenger map entry point. Uses the native Mapbox surface only when (1) this build includes the Mapbox SDK and
 * (2) the server issued a PUBLIC token for the signed-in user. Otherwise the clearly labelled demo canvas is used —
 * the app never pretends the demo canvas is a street map.
 */
@Composable
fun YabahMap(
    camera: MapCameraState,
    markers: List<MapMarker>,
    route: MapRoute?,
    overlay: ServiceOverlay?,
    places: PlaceIndex?,
    contentPadding: PaddingValues,
    onTap: (GeoPoint) -> Unit,
    publicMapToken: String?,
    modifier: Modifier = Modifier,
) {
    if (NativeMap.isAvailable && publicMapToken != null) {
        NativeMapSurface(camera, markers, route, overlay, contentPadding, onTap, publicMapToken, modifier)
    } else {
        DemoMapCanvas(camera, markers, route, overlay, places, contentPadding, onTap, modifier)
    }
}
