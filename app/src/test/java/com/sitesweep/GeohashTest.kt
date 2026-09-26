package com.sitesweep

import com.sitesweep.capture.Geohash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {

    @Test
    fun encode_producesCorrectLength() {
        val hash = Geohash.encode(17.44858, 78.37582, precision = 8)
        assertEquals("Precision must match requested length", 8, hash.length)
    }

    @Test
    fun encode_consistencyForNearbyCoordinates() {
        // Points ~2 meters apart should share the same 8-char or 7-char prefix
        val hash1 = Geohash.encode(17.448580, 78.375820, precision = 8)
        val hash2 = Geohash.encode(17.448582, 78.375822, precision = 8)

        assertTrue(
            "Points within ~2m should have identical locationKey or match first 7 characters",
            hash1.take(7) == hash2.take(7)
        )
    }

    @Test
    fun encode_distinctLocationsHaveDistinctKeys() {
        val hyderabad = Geohash.encode(17.44858, 78.37582, precision = 8)
        val bengaluru = Geohash.encode(12.9716, 77.5946, precision = 8)

        assertTrue("Distinct cities must have distinct geohashes", hyderabad != bengaluru)
    }
}
