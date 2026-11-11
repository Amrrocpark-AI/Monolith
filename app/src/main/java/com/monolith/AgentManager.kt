package com.monolith

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentManager {

    suspend fun searchWeb(query: String): String {
        return withContext(Dispatchers.IO) {
            "نتائج البحث عن: $query"
        }
    }

    suspend fun openApp(packageName: String): String {
        return "تم فتح التطبيق: $packageName"
    }

    suspend fun readFile(path: String): String {
        return "محتوى الملف: $path"
    }
}
