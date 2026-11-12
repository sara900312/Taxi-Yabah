package com.yabah.app.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yabah.app.core.model.GeoPoint

/** This build does not include the Mapbox SDK (no MAPBOX_DOWNLOADS_TOKEN on the build machine). */
object NativeMap {
    const val isAvailable: Boolean = false
}

@Suppress("UNUSED_PARAMETER")
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
) = Unit
