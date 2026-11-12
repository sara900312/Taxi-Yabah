package com.yabah.app.data.places

import com.yabah.app.core.geo.Geo
import com.yabah.app.core.model.GeoPoint
import com.yabah.app.core.net.YabahJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

data class Place(val nameAr: String?, val nameEn: String, val point: GeoPoint) {
    fun displayName(arabic: Boolean): String = if (arabic && !nameAr.isNullOrBlank()) nameAr else nameEn
}

/**
 * Offline place search over the GeoNames export (CC BY 4.0) bundled in assets/places. No geocoding request is
 * ever made; exact street addresses are therefore unavailable and pins are labelled "near X".
 */
class PlaceIndex(val places: List<Place>) {
    private val keysAr = places.map { normalize(it.nameAr.orEmpty()) }
    private val keysEn = places.map { normalize(it.nameEn) }

    fun search(query: String, limit: Int = 20, near: GeoPoint? = null): List<Place> {
        val q = normalize(query)
        if (q.length < 2) return emptyList()
        data class Hit(val place: Place, val score: Int, val dist: Double)
        val hits = ArrayList<Hit>()
        val qBare = stripArticle(q)
        for (i in places.indices) {
            val ar = keysAr[i]; val en = keysEn[i]
            val arWords = ar.split(' ').map(::stripArticle)
            val enWords = en.split(' ').map(::stripArticle)
            val score = when {
                ar == q || en == q || stripArticle(ar) == qBare || stripArticle(en) == qBare -> 0
                ar.startsWith(q) || en.startsWith(q) || arWords.firstOrNull()?.startsWith(qBare) == true ||
                    enWords.firstOrNull()?.startsWith(qBare) == true -> 1
                arWords.any { it.startsWith(qBare) } || enWords.any { it.startsWith(qBare) } -> 2
                ar.contains(qBare) || en.contains(qBare) -> 3
                else -> continue
            }
            hits += Hit(places[i], score, near?.let { Geo.metersBetween(it, places[i].point) } ?: 0.0)
        }
        val out = ArrayList<Place>(limit)
        for (h in hits.sortedWith(compareBy<Hit> { it.score }.thenBy { it.dist }.thenBy { it.place.nameEn.length })) {
            // GeoNames often lists one place several times (district, locality, admin area): keep the first.
            val dup = out.any { o ->
                (o.nameAr ?: o.nameEn) == (h.place.nameAr ?: h.place.nameEn) && Geo.metersBetween(o.point, h.place.point) < 3_000
            }
            if (!dup) out += h.place
            if (out.size == limit) break
        }
        return out
    }

    fun nearest(p: GeoPoint, maxMeters: Double = 1500.0): Place? {
        var best: Place? = null
        var bestD = maxMeters
        // Cheap bounding filter (~0.02 deg) before haversine.
        for (pl in places) {
            if (kotlin.math.abs(pl.point.lat - p.lat) > 0.02 || kotlin.math.abs(pl.point.lng - p.lng) > 0.025) continue
            val d = Geo.metersBetween(p, pl.point)
            if (d < bestD) { bestD = d; best = pl }
        }
        return best
    }

    companion object {
        fun parse(json: String): PlaceIndex {
            val arr = YabahJson.parseToJsonElement(json) as JsonArray
            val list = arr.mapNotNull { row ->
                runCatching {
                    val a = row.jsonArray
                    Place(
                        nameAr = a[0].jsonPrimitive.content.takeIf { it.isNotBlank() },
                        nameEn = a[1].jsonPrimitive.content,
                        point = GeoPoint(a[2].jsonPrimitive.double, a[3].jsonPrimitive.double),
                    )
                }.getOrNull()
            }
            return PlaceIndex(list)
        }

        /** Drops the Arabic definite article and common Latin transliterations of it ("al ", "al-", "hayy al"). */
        fun stripArticle(word: String): String = when {
            word.length > 3 && word.startsWith("ال") -> word.substring(2)
            word.startsWith("al ") -> word.substring(3)
            word.startsWith("hayy al ") -> word.substring(8)
            else -> word
        }

        /** Folds Arabic letter variants, strips diacritics/tatweel and Latin accents/apostrophes. */
        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s.lowercase()) {
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                    'ة' -> sb.append('ه')
                    'ى' -> sb.append('ي')
                    'ؤ' -> sb.append('و')
                    'ئ' -> sb.append('ي')
                    'گ' -> sb.append('ك')
                    'ڤ' -> sb.append('ف')
                    'ـ', '\'', '’', '`', '-' -> if (ch == '-') sb.append(' ')
                    in 'ً'..'ٟ', 'ٰ' -> Unit
                    else -> sb.append(ch)
                }
            }
            val noAccents = Normalizer.normalize(sb, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            return noAccents.replace(Regex("\\s+"), " ").trim()
        }
    }
}
