package com.sitesweep.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sitesweep.data.local.entity.IssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: IssueEntity)

    @Update
    suspend fun updateIssue(issue: IssueEntity)

    @Query("SELECT * FROM issues WHERE id = :id")
    suspend fun getIssueById(id: String): IssueEntity?

    @Query("SELECT * FROM issues WHERE id = :id")
    fun observeIssueById(id: String): Flow<IssueEntity?>

    @Query("SELECT * FROM issues ORDER BY updatedAt DESC")
    fun getAllIssues(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE status = :status ORDER BY updatedAt DESC")
    fun getIssuesByStatus(status: String): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE locationKey = :locationKey AND status != 'RESOLVED' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveIssueByLocationKey(locationKey: String): IssueEntity?

    @Query("SELECT * FROM issues WHERE (locationKey = :locationKey OR locationKey LIKE :parentPrefix || '%') AND status != 'RESOLVED' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveIssueByLocationPrefix(locationKey: String, parentPrefix: String): IssueEntity?

    @Query("UPDATE issues SET status = :status, updatedAt = :updatedAt, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long, resolvedAt: Long? = null)

    @Query("UPDATE issues SET assignedTo = :assignedTo, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAssignment(id: String, assignedTo: String, status: String, updatedAt: Long)

    @Query("UPDATE issues SET engineerNotes = :engineerNotes, resolutionNotes = :resolutionNotes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNotes(id: String, engineerNotes: String?, resolutionNotes: String?, updatedAt: Long)

    @Query("UPDATE issues SET latestCaptureId = :latestCaptureId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun attachLatestCapture(id: String, latestCaptureId: String, updatedAt: Long)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun deleteIssue(id: String)

    @Query("SELECT COUNT(*) FROM issues WHERE status != 'RESOLVED'")
    fun observeOpenIssuesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM issues WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>
}
