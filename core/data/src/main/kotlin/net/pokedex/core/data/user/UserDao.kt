package net.pokedex.core.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatchRecordDao {

    /**
     * Every record, as a stream.
     *
     * Returning all of them is deliberate rather than lazy: the ceiling is ~1400 rows
     * of small scalars, the whole active preset projection is a few hundred KB, and
     * holding it lets progress, search and filtering happen in memory instead of
     * across two database files. See docs/architecture.md.
     */
    @Query("SELECT * FROM catch_record")
    fun observeAll(): Flow<List<CatchRecordEntity>>

    @Query("SELECT * FROM catch_record WHERE variantId = :variantId AND copyIndex = :copyIndex")
    suspend fun find(variantId: String, copyIndex: Int): CatchRecordEntity?

    @Query("SELECT * FROM catch_record")
    suspend fun all(): List<CatchRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CatchRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<CatchRecordEntity>)

    @Query("DELETE FROM catch_record WHERE variantId = :variantId AND copyIndex = :copyIndex")
    suspend fun delete(variantId: String, copyIndex: Int)

    @Query("SELECT COUNT(*) FROM catch_record WHERE caught = 1")
    suspend fun caughtCount(): Int

    /**
     * Replace-mode import. One transaction so a failure part-way leaves the previous
     * records intact rather than half of each.
     */
    @Transaction
    suspend fun replaceAll(records: List<CatchRecordEntity>) {
        deleteAll()
        upsertAll(records)
    }

    @Query("DELETE FROM catch_record")
    suspend fun deleteAll()
}

@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)
}

@Dao
interface MyGameDao {

    @Query("SELECT gameId FROM my_game ORDER BY gameId")
    fun observe(): Flow<List<String>>

    @Query("SELECT gameId FROM my_game ORDER BY gameId")
    suspend fun all(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: MyGameEntity)

    @Query("DELETE FROM my_game WHERE gameId = :gameId")
    suspend fun delete(gameId: String)

    /** A restore that brings a set of games: one transaction, so no half-applied set. */
    @Transaction
    suspend fun replaceAll(gameIds: Collection<String>) {
        deleteAll()
        gameIds.forEach { insert(MyGameEntity(it)) }
    }

    @Query("DELETE FROM my_game")
    suspend fun deleteAll()
}

@Dao
interface BackupLogDao {

    @Query("SELECT * FROM backup_log ORDER BY createdAt DESC")
    fun observe(): Flow<List<BackupLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BackupLogEntity)

    @Query("DELETE FROM backup_log WHERE fileName = :fileName")
    suspend fun delete(fileName: String)

    @Query(
        "DELETE FROM backup_log WHERE fileName NOT IN " +
            "(SELECT fileName FROM backup_log ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
