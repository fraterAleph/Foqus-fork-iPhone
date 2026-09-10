package app.foqos.android.data.repo

import app.foqos.android.data.db.ProfileDao
import app.foqos.android.data.db.ProfileEntity
import app.foqos.android.data.db.SessionDao
import app.foqos.android.data.db.SessionEntity
import app.foqos.android.data.db.SessionWithProfile
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {

    fun observeAll(): Flow<List<ProfileEntity>> = dao.observeAll()

    fun observeById(id: String): Flow<ProfileEntity?> = dao.observeById(id)

    suspend fun getAll(): List<ProfileEntity> = dao.getAll()

    suspend fun get(id: String): ProfileEntity? = dao.getById(id)

    suspend fun save(profile: ProfileEntity): ProfileEntity {
        val existing = dao.getById(profile.id)
        val prepared = if (existing == null) {
            profile.copy(sortOrder = dao.nextSortOrder())
        } else {
            profile.copy(createdAt = existing.createdAt, updatedAt = System.currentTimeMillis())
        }
        dao.upsert(prepared)
        return prepared
    }

    suspend fun delete(profile: ProfileEntity) = dao.delete(profile)

    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            dao.getById(id)?.let { dao.upsert(it.copy(sortOrder = index)) }
        }
    }
}

class SessionRepository(private val dao: SessionDao) {

    fun observeActive(): Flow<SessionWithProfile?> = dao.observeActive()

    fun observeRecent(limit: Int = 200): Flow<List<SessionWithProfile>> = dao.observeRecent(limit)

    fun observeBetween(from: Long, to: Long): Flow<List<SessionWithProfile>> =
        dao.observeBetween(from, to)

    suspend fun getBetween(from: Long, to: Long): List<SessionWithProfile> = dao.getBetween(from, to)

    suspend fun getActive(): SessionWithProfile? = dao.getActive()

    suspend fun save(session: SessionEntity) = dao.upsert(session)

    suspend fun closeAllUnfinished(at: Long = System.currentTimeMillis()) {
        dao.getAllUnfinished().forEach { dao.upsert(it.copy(endTime = at)) }
    }

    suspend fun deleteForProfile(profileId: String) = dao.deleteForProfile(profileId)
}
