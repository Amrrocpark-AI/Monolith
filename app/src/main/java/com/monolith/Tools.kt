package com.monolith

class Tools {
    fun trimVideo(input: String, start: Long, end: Long): String {
        return "تم قص الفيديو: $input"
    }

    fun trimAudio(input: String, start: Long, end: Long): String {
        return "تم قص الصوت: $input"
    }

    fun readExcel(path: String): String {
        return "تم قراءة Excel: $path"
    }

    fun readPdf(path: String): String {
        return "تم قراءة PDF: $path"
    }
}
