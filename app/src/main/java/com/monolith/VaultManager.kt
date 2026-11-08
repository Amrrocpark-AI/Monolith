package com.monolith

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object VaultManager {
    private fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "Vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getNamesFile(context: Context): File {
        return File(getVaultDir(context), "names.json")
    }

    private fun getNextSequence(context: Context): Int {
        val namesFile = getNamesFile(context)
        var maxSeq = 0
        val regex = "PNG_([0-9]+)".toRegex()

        if (namesFile.exists()) {
            try {
                val json = JSONObject(namesFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val name = json.optString(key, "")
                    val match = regex.find(name)
                    if (match != null) {
                        val num = match.groupValues[1].toIntOrNull() ?: 0
                        if (num > maxSeq) maxSeq = num
                    }
                }
            } catch (e: Exception) {}
        }

        getVaultDir(context).listFiles()?.forEach { file ->
            val match = regex.find(file.nameWithoutExtension)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 0
                if (num > maxSeq) maxSeq = num
            }
        }

        return maxSeq + 1
    }

    fun saveSticker(context: Context, bitmap: Bitmap, ignoredName: String = ""): File {
        val sequence = getNextSequence(context)
        val displayName = "PNG_$sequence"
        
        val file = File(getVaultDir(context), "sticker_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        renameSticker(context, file.name, displayName)
        return file
    }

    fun getStickers(context: Context): List<File> {
        return getVaultDir(context).listFiles { file -> file.extension == "png" }?.toList() ?: emptyList()
    }

    fun getStickerName(context: Context, fileName: String): String {
        val namesFile = getNamesFile(context)
        if (!namesFile.exists()) return fileName
        return try {
            val json = JSONObject(namesFile.readText())
            json.optString(fileName, fileName)
        } catch (e: Exception) {
            fileName
        }
    }

    fun renameSticker(context: Context, fileName: String, newName: String) {
        val namesFile = getNamesFile(context)
        val json = if (namesFile.exists()) {
            try { JSONObject(namesFile.readText()) } catch (e: Exception) { JSONObject() }
        } else JSONObject()
        
        json.put(fileName, newName)
        namesFile.writeText(json.toString())
    }

    fun deleteSticker(context: Context, file: File) {
        if (file.exists()) file.delete()
        val namesFile = getNamesFile(context)
        if (namesFile.exists()) {
            try {
                val json = JSONObject(namesFile.readText())
                json.remove(file.name)
                namesFile.writeText(json.toString())
            } catch (e: Exception) {}
        }
    }
}
