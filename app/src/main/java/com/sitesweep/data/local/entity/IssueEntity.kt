package com.sitesweep.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sitesweep.data.model.IssueStatus
import java.util.UUID

/**
 * Issue entity representing a persistent structural distress problem
 * that tracks multiple Capture observations across its lifecycle:
 * Initial detection -> Issue created -> Repair -> Reinspection -> Resolved
 *
 * Reuses existing Capture records via foreign key references without duplicating image files.
 */
@Entity(
    tableName = "issues",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["originCaptureId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["latestCaptureId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("locationKey"),
        Index("status"),
        Index("originCaptureId"),
        Index("latestCaptureId")
    ]
)
data class IssueEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val originCaptureId: String,
    val latestCaptureId: String? = originCaptureId,
    val locationKey: String,
    val title: String,
    val status: String = IssueStatus.OPEN.name,
    val assignedTo: String? = null,
    val engineerNotes: String? = null,
    val resolutionNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
) {
    val issueStatus: IssueStatus
        get() = IssueStatus.fromString(status)
}
