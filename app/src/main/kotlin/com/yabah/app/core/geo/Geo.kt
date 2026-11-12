package com.yabah.app.core.geo

import com.yabah.app.core.model.City
import com.yabah.app.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Geo {
    /** Same haversine as the server's regions/geometry.ts (`metersBetween`). */
    fun metersBetween(a: GeoPoint, b: GeoPoint): Double {
        val rad = PI / 180
        val s1 = sin((b.lat - a.lat) * rad / 2)
        val s2 = sin((b.lng - a.lng) * rad / 2)
        val h = s1 * s1 + cos(a.lat * rad) * cos(b.lat * rad) * s2 * s2
        return 12_742_000.0 * asin(min(1.0, sqrt(h)))
    }

    // Web-Mercator helpers in "world pixels" for a given zoom (256 * 2^zoom pixels around the world).
    fun worldX(lng: Double, worldSize: Double): Double = (lng + 180.0) / 360.0 * worldSize
    fun worldY(lat: Double, worldSize: Double): Double {
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val r = clamped * PI / 180
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * worldSize
    }
    fun lngAt(x: Double, worldSize: Double): Double = x / worldSize * 360.0 - 180.0
    fun latAt(y: Double, worldSize: Double): Double {
        val n = PI - 2 * PI * y / worldSize
        return 180 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }
}

/** One exterior ring of [lng, lat] pairs, as in the server's boundaries.json. */
typealias Ring = List<DoubleArray>

/**
 * Client-side copy of the server's service-area rule (regions/geometry.ts `inServiceArea`): inside the governorate
 * outline AND within `service_radius_m` of the city centre. This is a UX hint only; the server's answer is final.
 */
class ServiceAreaRule(private val outlines: Map<String, Ring>) {
    fun outline(code: String): Ring? = outlines[code]

    fun inGovernorate(p: GeoPoint, ring: Ring): Boolean {
        var inside = false
        for (i in 1 until ring.size) {
            val x = ring[i - 1][0]; val y = ring[i - 1][1]
            val xx = ring[i][0]; val yy = ring[i][1]
            if ((y > p.lat) != (yy > p.lat) && p.lng < (xx - x) * (p.lat - y) / (yy - y) + x) inside = !inside
        }
        return inside
    }

    fun inServiceArea(p: GeoPoint, city: City): Boolean {
        val ring = outlines[city.code] ?: return false
        return inGovernorate(p, ring) && Geo.metersBetween(p, city.center) <= city.serviceRadiusM
    }
}
