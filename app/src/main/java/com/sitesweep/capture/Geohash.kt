package com.sitesweep.capture

/**
 * Standard offline Base32 Geohash encoder.
 * Encodes latitude and longitude into a geohash string truncated to roughly 10m precision (~8 chars).
 * Indexed in Room for instant revisit queries without O(N) table scans.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lng: Double, precision: Int = 8): String {
        var minLat = -90.0
        var maxLat = 90.0
        var minLng = -180.0
        var maxLng = 180.0

        val hash = StringBuilder(precision)
        var isEven = true
        var bit = 0
        var ch = 0

        while (hash.length < precision) {
            if (isEven) {
                val mid = (minLng + maxLng) / 2.0
                if (lng >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    minLng = mid
                } else {
                    maxLng = mid
                }
            } else {
                val mid = (minLat + maxLat) / 2.0
                if (lat >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    minLat = mid
                } else {
                    maxLat = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                hash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return hash.toString()
    }
}
