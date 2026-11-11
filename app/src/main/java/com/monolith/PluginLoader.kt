package com.monolith

import dalvik.system.DexClassLoader
import java.io.File

class PluginLoader(private val pluginDir: File, private val optimizedDir: File) {
    fun loadPlugin(pluginName: String): Class<*>? {
        val pluginFile = File(pluginDir, "$pluginName.dex")
        if (!pluginFile.exists()) return null
        return try {
            val classLoader = DexClassLoader(pluginFile.absolutePath, optimizedDir.absolutePath, null, javaClass.classLoader)
            classLoader.loadClass("com.monolith.plugin.MainPlugin")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
