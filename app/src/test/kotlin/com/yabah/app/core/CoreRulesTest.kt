package com.yabah.app.core

import com.yabah.app.Fixtures
import com.yabah.app.R
import com.yabah.app.core.auth.PhoneNumbers
import com.yabah.app.core.geo.Geo
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.model.TripStatus
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.core.net.message
import com.yabah.app.core.util.UiText
import com.yabah.app.data.places.PlaceIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRulesTest {
    @Test fun iraqiNumbersNormaliseToE164() {
        assertEquals("+9647701234567", PhoneNumbers.toE164("0770 123 4567"))
        assertEquals("+9647701234567", PhoneNumbers.toE164("٠٧٧٠١٢٣٤٥٦٧")) // Arabic-Indic digits
        assertEquals("+9647701234567", PhoneNumbers.toE164("+964 770 123 4567"))
        assertEquals("+9647701234567", PhoneNumbers.toE164("00964-770-123-4567"))
        assertEquals("+9647701234567", PhoneNumbers.toE164("7701234567"))
        assertNull(PhoneNumbers.toE164("0612345678"))
        assertNull(PhoneNumbers.toE164("077012345"))
        assertNull(PhoneNumbers.toE164("+9661234567890"))
        assertEquals("0770 123 4567", PhoneNumbers.display("+9647701234567"))
        assertEquals("123456", PhoneNumbers.normalizeOtp("١٢٣ ٤٥٦"))
        assertNull(PhoneNumbers.normalizeOtp("12345"))
    }

    @Test fun serviceAreaMatchesServerRule() {
        val city = Fixtures.baghdad
        assertTrue(Fixtures.areas.inServiceArea(city.center, city))
        assertTrue(Fixtures.areas.inServiceArea(Fixtures.mansour, city))
        // Inside the 30 km circle is not enough: the governorate outline must also contain the point.
        val basra = GeoPoint(30.5085, 47.7804)
        assertFalse(Fixtures.areas.inServiceArea(basra, city))
        // Inside the governorate but beyond the service radius → outside.
        val farNorth = GeoPoint(33.70, 44.30)
        assertTrue(Fixtures.areas.inGovernorate(farNorth, Fixtures.areas.outline("baghdad")!!))
        assertTrue(Geo.metersBetween(farNorth, city.center) > 30_000)
        assertFalse(Fixtures.areas.inServiceArea(farNorth, city))
    }

    @Test fun haversineMatchesServerFormula() {
        val d = Geo.metersBetween(Fixtures.mansour, Fixtures.karrada)
        assertEquals(6_480.0, d, 150.0)
    }

    @Test fun mercatorRoundTrips() {
        val ws = 256.0 * Math.pow(2.0, 13.0)
        val p = Fixtures.karrada
        assertEquals(p.lat, Geo.latAt(Geo.worldY(p.lat, ws), ws), 1e-9)
        assertEquals(p.lng, Geo.lngAt(Geo.worldX(p.lng, ws), ws), 1e-9)
    }

    @Test fun offlineSearchFoldsArabicVariants() {
        val idx = Fixtures.places
        assertTrue(idx.places.size > 4000)
        assertEquals("المنصور", idx.search("منصور").first().nameAr)
        assertEquals("الكرادة الشرقية", idx.search("الكراده").first().nameAr) // ة/ه folding
        assertEquals("الكاظمية", idx.search("kazimiyah").first().nameAr)
        assertTrue(idx.search("x").isEmpty())
        assertEquals("المنصور", idx.nearest(GeoPoint(33.3231, 44.3527))?.nameAr)
        assertNull(idx.nearest(GeoPoint(35.0, 40.0)))
        assertEquals("الكراده", PlaceIndex.normalize("الكرادة"))
    }

    @Test fun everyVerifiedServerCodeHasAHumanMessage() {
        val codes = listOf(
            "INVALID_SESSION", "ACCOUNT_NOT_ACTIVE", "PASSENGER_ROLE_REQUIRED", "OUTSIDE_SERVICE_AREA", "DROPOFF_OUTSIDE_SERVICE_AREA",
            "ROUTE_NOT_FOUND", "ROUTE_TOO_SHORT", "MAPBOX_ROUTE_UNAVAILABLE", "QUOTE_RATE_LIMITED", "MAPBOX_DAILY_LIMIT_REACHED",
            "DYNAMIC_PRICING_NOT_CONFIGURED", "DYNAMIC_PRICING_DISABLED", "FARE_QUOTE_EXPIRED", "FARE_QUOTE_ALREADY_USED", "PRICE_CHANGED",
            "PASSENGER_HAS_ACTIVE_TRIP", "PAYMENT_METHOD_DISABLED", "MAPBOX_PUBLIC_TOKEN_UNAVAILABLE", "TRIP_CANNOT_BE_CANCELLED",
            "ALREADY_RATED", "RATING_WINDOW_CLOSED", "CROSS_CITY_NOT_SUPPORTED", "VEHICLE_CLASS_NOT_AVAILABLE",
        )
        codes.forEach { c ->
            val m = ApiFailure.server(c, 400).message() as UiText.Res
            assertTrue("$c fell through to the generic message", m.id != R.string.err_server)
        }
        assertEquals(R.string.err_pricing_not_configured, (ApiFailure.server("DYNAMIC_PRICING_NOT_CONFIGURED", 503).message() as UiText.Res).id)
        assertEquals(R.string.err_offline, (ApiFailure.offline.message() as UiText.Res).id)
        assertEquals(R.string.err_server, (ApiFailure.server("SOMETHING_NEW", 500).message() as UiText.Res).id)
    }

    @Test fun cancellationMirrorsServerRule() {
        assertEquals(setOf(TripStatus.Requested, TripStatus.Accepted, TripStatus.Arrived),
            TripStatus.entries.filter { it.passengerMayCancel }.toSet())
        assertEquals(TripStatus.InProgress, TripStatus.fromWire("in_progress"))
        assertEquals(TripStatus.Unknown, TripStatus.fromWire("bogus"))
    }
}
