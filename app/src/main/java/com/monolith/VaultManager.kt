package com.monolith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    // حفظ صورة جديدة في الخزنة
    fun saveSticker(context: Context, bitmap: Bitmap, name: String): File {
        val file = File(getVaultDir(context), "sticker_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        renameSticker(context, file.name, name)
        return file
    }

    // جلب كل الصور
    fun getStickers(context: Context): List<File> {
        return getVaultDir(context).listFiles { file -> file.extension == "png" }?.toList() ?: emptyList()
    }

    // جلب الاسم المخصص لصورة
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

    // إعادة تسمية صورة
    fun renameSticker(context: Context, fileName: String, newName: String) {
        val namesFile = getNamesFile(context)
        val json = if (namesFile.exists()) {
            try { JSONObject(namesFile.readText()) } catch (e: Exception) { JSONObject() }
        } else JSONObject()
        
        json.put(fileName, newName)
        namesFile.writeText(json.toString())
    }

    // حذف صورة
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
