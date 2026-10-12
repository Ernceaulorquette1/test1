package com.pronoscore360.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Caché local (Room) de respuestas JSON para mostrar datos recientes sin conexión. */
@Entity(tableName = "cached_json") data class CachedJson(@PrimaryKey val key: String, val json: String, val updatedAt: Long)

@Dao interface CacheDao {
    @Query("SELECT * FROM cached_json WHERE `key` = :key") suspend fun get(key: String): CachedJson?
    @Upsert suspend fun put(row: CachedJson)
    @Query("DELETE FROM cached_json") suspend fun clear()
}

@Database(entities = [CachedJson::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun cache(): CacheDao }
