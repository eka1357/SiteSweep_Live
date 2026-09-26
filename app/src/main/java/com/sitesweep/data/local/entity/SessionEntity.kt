package com.sitesweep.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Inspection session entity matching AGENTS.md data model:
 * Session(id, startedAt, endedAt, label)
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val label: String
)
