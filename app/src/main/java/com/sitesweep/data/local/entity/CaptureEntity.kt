package com.sitesweep.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Capture entity matching AGENTS.md data model:
 * Capture(id, sessionId, imagePath, lat, lng, timestamp, severity, confidence, locationKey)
 *
 * locationKey is a geohash truncated to ~10m precision.
 * Fast index on locationKey enables instant revisit lookups without table scans.
 */
@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("locationKey")
    ]
)
data class CaptureEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val imagePath: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: String,
    val confidence: Float,
    val locationKey: String
)
