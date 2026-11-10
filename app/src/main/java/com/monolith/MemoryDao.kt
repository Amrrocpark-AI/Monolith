package com.monolith

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY timestamp DESC LIMIT 10")
    suspend fun getMemoriesByType(type: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE timestamp < :timestamp")
    suspend fun deleteOldMemories(timestamp: Long)
}
