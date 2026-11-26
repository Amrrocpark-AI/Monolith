
package com.monolith

data class ExtractedCodeBlock(
    val index: Int,
    val language: String,
    val code: String,
    val isClosed: Boolean = true
)

object CodeAdapter {

    fun extractCodeBlocks(content: String): List<ExtractedCodeBlock> {
        if (content.isEmpty() ||
            (!content.contains("```") && !content.contains("~~~"))
        ) {
            return emptyList()
        }

        val lines = content.split('\n')
        val result = mutableListOf<ExtractedCodeBlock>()

        var inFence = false
        var fenceChar = '`'
        var fenceLength = 0
        var currentLanguage = "Text"
        val currentLines = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r')

            if (!inFence) {
                val openInfo = parseOpeningFence(line)

                if (openInfo != null) {
                    inFence = true
                    fenceChar = openInfo.first
                    fenceLength = openInfo.second
                    currentLanguage = openInfo.third.ifEmpty { "Text" }
                    currentLines.clear()
                }
            } else {
                if (isClosingFence(line, fenceChar, fenceLength)) {
                    result.add(
                        ExtractedCodeBlock(
                            index = result.size + 1,
                            language = currentLanguage,
                            code = currentLines.joinToString("\n"),
                            isClosed = true
                        )
                    )

                    inFence = false
                    fenceLength = 0
                    currentLanguage = "Text"
                    currentLines.clear()
                } else {
                    currentLines.add(line)
                }
            }
        }

        if (inFence) {
            result.add(
                ExtractedCodeBlock(
                    index = result.size + 1,
                    language = currentLanguage,
                    code = currentLines.joinToString("\n"),
                    isClosed = false
                )
            )
        }

        return result
    }

    private fun parseOpeningFence(
        line: String
    ): Triple<Char, Int, String>? {
        var idx = 0
        var spaces = 0

        while (
            idx < line.length &&
            spaces < 3 &&
            (line[idx] == ' ' || line[idx] == '\t')
        ) {
            idx++
            spaces++
        }

        if (idx >= line.length) return null

        val candidateChar = line[idx]

        if (candidateChar != '`' && candidateChar != '~') {
            return null
        }

        var count = 0

        while (idx < line.length && line[idx] == candidateChar) {
            count++
            idx++
        }

        if (count < 3) return null

        val infoString = line.substring(idx).trim()

        if (candidateChar == '`' && infoString.contains('`')) {
            return null
        }

        val language = infoString
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.trim()
            .orEmpty()

        return Triple(candidateChar, count, language)
    }

    private fun isClosingFence(
        line: String,
        fenceChar: Char,
        minLength: Int
    ): Boolean {
        var idx = 0
        var spaces = 0

        while (
            idx < line.length &&
            spaces < 3 &&
            (line[idx] == ' ' || line[idx] == '\t')
        ) {
            idx++
            spaces++
        }

        if (idx >= line.length || line[idx] != fenceChar) {
            return false
        }

        var count = 0

        while (idx < line.length && line[idx] == fenceChar) {
            count++
            idx++
        }

        if (count < minLength) return false

        while (idx < line.length) {
            if (line[idx] != ' ' && line[idx] != '\t') {
                return false
            }
            idx++
        }

        return true
    }
}
