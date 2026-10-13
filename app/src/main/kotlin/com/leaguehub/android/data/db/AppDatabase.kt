package com.leaguehub.android.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Base locale : classements, calendrier et résultats (single source of truth, mode hors ligne).
// Les colonnes indexées servent aux requêtes ; `payload` porte le modèle du domaine en JSON.

@Entity(tableName = "standings", primaryKeys = ["league", "teamId"])
data class StandingEntity(
    val league: String,
    val teamId: String,
    val rank: Int,
    val payload: String,
)

@Entity(tableName = "matches", indices = [Index("competition", "kickoff")])
data class MatchEntity(
    @androidx.room.PrimaryKey val id: String,
    val competition: String,
    val kickoff: Long,
    val state: String,
    val payload: String,
)

/** Date et métadonnées de la dernière synchronisation réussie d'une ressource. */
@Entity(tableName = "sync")
data class SyncEntity(
    @androidx.room.PrimaryKey val key: String,
    val updatedAt: Long,
    val extra: String = "",
)

/** Réponses ponctuelles (buteurs, actualités), gardées pour le hors-ligne. */
@Entity(tableName = "blobs")
data class BlobEntity(
    @androidx.room.PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface StandingDao {
    @Query("SELECT * FROM standings WHERE league = :league ORDER BY rank")
    fun observe(league: String): Flow<List<StandingEntity>>

    @Query("SELECT * FROM standings")
    suspend fun all(): List<StandingEntity>

    @Query("DELETE FROM standings WHERE league = :league")
    suspend fun clear(league: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rows: List<StandingEntity>)

    @Transaction
    suspend fun replace(league: String, rows: List<StandingEntity>) {
        clear(league)
        insert(rows)
    }
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE competition = :competition AND kickoff BETWEEN :from AND :to ORDER BY kickoff")
    fun observe(competition: String, from: Long, to: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE competition = :competition AND kickoff BETWEEN :from AND :to ORDER BY kickoff")
    suspend fun between(competition: String, from: Long, to: Long): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(matches: List<MatchEntity>)

    /** Purge des matchs d'une période qu'ESPN ne renvoie plus (déplacés ou annulés). */
    @Query("DELETE FROM matches WHERE competition = :competition AND kickoff BETWEEN :from AND :to AND id NOT IN (:keep)")
    suspend fun deleteMissing(competition: String, from: Long, to: Long, keep: List<String>)

    @Transaction
    suspend fun replaceWindow(competition: String, from: Long, to: Long, matches: List<MatchEntity>) {
        if (matches.isNotEmpty()) deleteMissing(competition, from, to, matches.map { it.id })
        upsert(matches)
    }
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync WHERE `key` = :key")
    fun observe(key: String): Flow<SyncEntity?>

    @Query("SELECT * FROM sync WHERE `key` = :key")
    suspend fun get(key: String): SyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncEntity)
}

@Dao
interface BlobDao {
    @Query("SELECT * FROM blobs WHERE `key` = :key")
    suspend fun get(key: String): BlobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: BlobEntity)
}

@Database(
    entities = [StandingEntity::class, MatchEntity::class, SyncEntity::class, BlobEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun standings(): StandingDao
    abstract fun matches(): MatchDao
    abstract fun sync(): SyncDao
    abstract fun blobs(): BlobDao
}
