package app.foqos.android.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM profiles")
    suspend fun nextSortOrder(): Int
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActive(): Flow<SessionWithProfile?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActive(): SessionWithProfile?

    @Query("SELECT * FROM sessions WHERE endTime IS NULL")
    suspend fun getAllUnfinished(): List<SessionEntity>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionWithProfile>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<SessionWithProfile>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    suspend fun getBetween(from: Long, to: Long): List<SessionWithProfile>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
