package com.yabah.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yabah.app.R
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.data.places.PlaceIndex
import com.yabah.app.ui.theme.LocalYabahExtras
import com.yabah.app.ui.theme.PlexArabic
import com.yabah.app.ui.theme.YabahColors
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * DEMO map surface: a simplified, locally rendered map made only from bundled open data (the server's governorate
 * outline, the service radius, and GeoNames places). It has no streets and is always watermarked "DEMO".
 */
@Composable
fun DemoMapCanvas(
    camera: MapCameraState,
    markers: List<MapMarker>,
    route: MapRoute?,
    overlay: ServiceOverlay?,
    places: PlaceIndex?,
    contentPadding: PaddingValues,
    onTap: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val x = LocalYabahExtras.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val labelStyle = TextStyle(fontFamily = PlexArabic, fontSize = 11.sp, color = x.mapLabel, fontWeight = FontWeight.Medium)
    val watermarkStyle = TextStyle(fontFamily = PlexArabic, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = x.mapLabel.copy(alpha = 0.06f))
    val currentOnTap by rememberUpdatedState(onTap)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val mapDesc = stringResource(R.string.map_desc)
    val outlinePath = remember { Path() }
    val scratch = remember { Path() }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(x.mapLand)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val left = with(density) { contentPadding.calculateLeftPadding(dir).toPx() }
        val right = with(density) { contentPadding.calculateRightPadding(dir).toPx() }
        val top = with(density) { contentPadding.calculateTopPadding().toPx() }
        val bottom = with(density) { contentPadding.calculateBottomPadding().toPx() }
        SideEffect {
            camera.density = density.density
            camera.viewport = Rect(0f, 0f, w, h)
            camera.visible = Rect(left, top, (w - right).coerceAtLeast(left + 1), (h - bottom).coerceAtLeast(top + 1))
        }

        Canvas(
            Modifier.fillMaxSize()
                .testTag("demo_map")
                .semantics { contentDescription = mapDesc }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { currentOnTap(camera.unproject(it)) },
                        onDoubleTap = { camera.zoomBy(2f, it) },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        camera.isGesturing = true
                        try {
                            var pastSlop = false
                            var zoomAcc = 1f
                            var panAcc = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (!pastSlop) {
                                        zoomAcc *= zoomChange; panAcc += panChange
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        if (abs(1 - zoomAcc) * centroidSize > touchSlop || panAcc.getDistance() > touchSlop) pastSlop = true
                                    }
                                    if (pastSlop) {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        if (zoomChange != 1f) camera.zoomBy(zoomChange, centroid)
                                        if (panChange != Offset.Zero) camera.panBy(panChange)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (!canceled && event.changes.any { it.pressed })
                        } finally {
                            camera.isGesturing = false
                        }
                    }
                },
        ) {
            val zoom = camera.zoom
            val center = camera.center
            val vis = camera.visible.takeIf { it.width > 0 } ?: Rect(left, top, w - right, h - bottom)
            val proj = Projection(center, zoom, density.density, vis.center)
            val nw = proj.unproject(Offset(0f, 0f)); val se = proj.unproject(Offset(size.width, size.height))

            drawGrid(proj, nw, se, x.mapGrid)

            overlay?.outline?.let { ring ->
                outlinePath.reset()
                ring.forEachIndexed { i, p ->
                    val o = proj.project(GeoPoint(p[1], p[0]))
                    if (i == 0) outlinePath.moveTo(o.x, o.y) else outlinePath.lineTo(o.x, o.y)
                }
                outlinePath.close()
                drawPath(outlinePath, x.mapOutline.copy(alpha = 0.05f))
                drawPath(outlinePath, x.mapOutline.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
            }
            overlay?.let { o ->
                scratch.reset()
                for (i in 0..72) {
                    val pt = proj.project(destination(o.center, o.radiusM.toDouble(), i * 5.0))
                    if (i == 0) scratch.moveTo(pt.x, pt.y) else scratch.lineTo(pt.x, pt.y)
                }
                drawPath(scratch, x.mapOutline.copy(alpha = 0.06f))
                drawPath(scratch, x.mapOutline.copy(alpha = 0.8f),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))))
            }

            if (places != null && zoom >= 11.5) drawPlaces(proj, places, nw, se, zoom, arabic, measurer, labelStyle, x.mapPlace)

            route?.let { drawRoute(proj, it) }
            markers.forEach { drawMarker(proj.project(it.point), it.kind) }

            // DEMO watermark, repeated diagonally.
            val wm = measurer.measure("DEMO", watermarkStyle)
            rotate(-24f) {
                var yy = -size.height * 0.2f
                while (yy < size.height * 1.2f) {
                    var xx = -size.width * 0.3f + (yy / 3f) % (wm.size.width * 1.8f)
                    while (xx < size.width * 1.3f) {
                        drawText(wm, topLeft = Offset(xx, yy)); xx += wm.size.width * 1.8f
                    }
                    yy += wm.size.height * 2.6f
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = contentPadding.calculateBottomPadding() + 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(stringResource(R.string.demo_map_attribution), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Immutable per-frame projection (same maths as [MapCameraState]). */
private class Projection(val center: GeoPoint, val zoom: Double, density: Float, val c: Offset) {
    private val ws = 256.0 * density * Math.pow(2.0, zoom)
    private val cx = com.yabah.app.core.geo.Geo.worldX(center.lng, ws)
    private val cy = com.yabah.app.core.geo.Geo.worldY(center.lat, ws)
    fun project(p: GeoPoint) = Offset(
        (c.x + com.yabah.app.core.geo.Geo.worldX(p.lng, ws) - cx).toFloat(),
        (c.y + com.yabah.app.core.geo.Geo.worldY(p.lat, ws) - cy).toFloat(),
    )
    fun unproject(s: Offset) = GeoPoint(
        com.yabah.app.core.geo.Geo.latAt(cy + (s.y - c.y), ws),
        com.yabah.app.core.geo.Geo.lngAt(cx + (s.x - c.x), ws),
    )
}

private fun DrawScope.drawGrid(proj: Projection, nw: GeoPoint, se: GeoPoint, color: Color) {
    val step = when { proj.zoom < 9 -> 0.5; proj.zoom < 11 -> 0.1; proj.zoom < 13 -> 0.05; proj.zoom < 15 -> 0.01; else -> 0.002 }
    var lng = floor(nw.lng / step) * step
    while (lng <= se.lng) {
        val xx = proj.project(GeoPoint(nw.lat, lng)).x
        drawLine(color, Offset(xx, 0f), Offset(xx, size.height), 1f); lng += step
    }
    var lat = ceil(nw.lat / step) * step
    while (lat >= se.lat) {
        val yy = proj.project(GeoPoint(lat, nw.lng)).y
        drawLine(color, Offset(0f, yy), Offset(size.width, yy), 1f); lat -= step
    }
}

private fun DrawScope.drawPlaces(
    proj: Projection, places: PlaceIndex, nw: GeoPoint, se: GeoPoint, zoom: Double, arabic: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, dot: Color,
) {
    val r = 2.2.dp.toPx()
    val taken = ArrayList<Rect>(80)
    val gap = 4.dp.toPx()
    var labels = 0
    val maxW = 150.dp.toPx().toInt()
    for (pl in places.places) {
        val p = pl.point
        if (p.lat > nw.lat || p.lat < se.lat || p.lng < nw.lng || p.lng > se.lng) continue
        val o = proj.project(p)
        drawCircle(dot, r, o)
        if (zoom >= 12.5 && labels < 70) {
            val layout = measurer.measure(pl.displayName(arabic), style, overflow = TextOverflow.Ellipsis, maxLines = 1,
                constraints = Constraints(maxWidth = maxW))
            val topLeft = Offset(o.x - layout.size.width / 2f, o.y + r + 1f)
            val box = Rect(topLeft.x - gap, topLeft.y - gap, topLeft.x + layout.size.width + gap, topLeft.y + layout.size.height + gap)
            if (taken.none { it.overlaps(box) }) {
                drawText(layout, topLeft = topLeft)
                taken += box
                labels++
            }
        }
    }
}

private fun DrawScope.drawRoute(proj: Projection, route: MapRoute) {
    if (route.points.size < 2) return
    val path = Path()
    route.points.forEachIndexed { i, p -> val o = proj.project(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    when (route.style) {
        RouteStyle.ServerRoad -> {
            drawPath(path, YabahColors.Navy.copy(alpha = 0.85f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, YabahColors.Jade, style = Stroke(5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        RouteStyle.DemoStraight -> drawPath(path, YabahColors.Slate, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
    }
}

private fun DrawScope.drawMarker(o: Offset, kind: MarkerKind) {
    when (kind) {
        MarkerKind.Pickup -> {
            drawCircle(Color.Black.copy(alpha = 0.18f), 13.dp.toPx(), o + Offset(0f, 2.dp.toPx()))
            drawCircle(Color.White, 12.dp.toPx(), o)
            drawCircle(YabahColors.Jade, 8.5.dp.toPx(), o)
            drawCircle(Color.White, 3.dp.toPx(), o)
        }
        MarkerKind.Dropoff -> {
            drawCircle(Color.Black.copy(alpha = 0.18f), 13.dp.toPx(), o + Offset(0f, 2.dp.toPx()))
            drawCircle(Color.White, 12.dp.toPx(), o)
            val s = 13.dp.toPx()
            drawRoundRect(YabahColors.Gold, topLeft = Offset(o.x - s / 2, o.y - s / 2), size = Size(s, s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawCircle(YabahColors.Navy, 2.5.dp.toPx(), o)
        }
        MarkerKind.Captain -> {
            drawCircle(YabahColors.Navy, 13.dp.toPx(), o)
            drawCircle(YabahColors.Gold, 6.dp.toPx(), o)
        }
    }
}

/** Point at [meters] from [c] on bearing [bearingDeg] (spherical earth). */
internal fun destination(c: GeoPoint, meters: Double, bearingDeg: Double): GeoPoint {
    val r = 6_371_000.0
    val d = meters / r
    val b = Math.toRadians(bearingDeg)
    val la1 = Math.toRadians(c.lat); val lo1 = Math.toRadians(c.lng)
    val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
    val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
    return GeoPoint(Math.toDegrees(la2), Math.toDegrees(lo2))
}
