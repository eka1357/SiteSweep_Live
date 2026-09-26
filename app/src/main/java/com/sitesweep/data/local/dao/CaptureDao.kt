package com.sitesweep.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sitesweep.data.local.entity.CaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(capture: CaptureEntity)

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getCaptureById(id: String): CaptureEntity?

    @Query("SELECT * FROM captures WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>>

    /**
     * Revisit lookup is a direct indexed query on locationKey (geohash to ~10m precision),
     * NOT a distance calculation over every row. Matches exact locationKey or immediate parent block.
     */
    @Query("SELECT * FROM captures WHERE locationKey = :locationKey ORDER BY timestamp ASC")
    suspend fun getCapturesByLocationKey(locationKey: String): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE locationKey = :locationKey OR locationKey LIKE :parentPrefix || '%' ORDER BY timestamp ASC")
    fun observeCapturesByLocationKey(locationKey: String, parentPrefix: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures ORDER BY timestamp DESC")
    fun getAllCaptures(): Flow<List<CaptureEntity>>
}
