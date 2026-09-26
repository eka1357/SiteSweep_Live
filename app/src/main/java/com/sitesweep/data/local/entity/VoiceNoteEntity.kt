package com.sitesweep.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * VoiceNote entity matching AGENTS.md data model:
 * VoiceNote(id, sessionId, captureId?, transcript, timestamp)
 */
@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("sessionId"),
        Index("captureId")
    ]
)
data class VoiceNoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val captureId: String? = null,
    val transcript: String,
    val timestamp: Long = System.currentTimeMillis()
)
