package com.monolith

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryHelper(private val memoryDao: MemoryDao) {

    suspend fun savePreference(content: String) {
        withContext(Dispatchers.IO) {
            memoryDao.insertMemory(MemoryEntity(type = "PREFERENCE", content = content, confidence = 0.8f))
        }
    }

    suspend fun getRecentMemories(): List<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            memoryDao.getRecentMemories()
        }
    }
}
