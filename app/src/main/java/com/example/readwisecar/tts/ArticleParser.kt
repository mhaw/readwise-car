package com.example.readwisecar.tts

import org.jsoup.Jsoup

object ArticleParser {
    fun toSpeechChunks(html: String, title: String, author: String): List<String> {
        val chunks = mutableListOf("$title, by $author.")
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, footer, aside").remove()

        val blocks = doc.select("h1, h2, h3, h4, p, li, blockquote")
        val rawBlocks = if (blocks.isNotEmpty()) blocks.map { it.text() } else listOf(doc.body().text())

        for (raw in rawBlocks) {
            val text = raw.replace(Regex("\\s+"), " ").trim()
            if (text.length < 3) continue
            if (text.length <= 650) {
                chunks.add(text)
            } else {
                val buf = StringBuilder()
                for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
                    if (buf.length + sentence.length > 650 && buf.isNotEmpty()) {
                        chunks.add(buf.toString().trim())
                        buf.clear()
                    }
                    buf.append(sentence).append(' ')
                }
                if (buf.isNotEmpty()) chunks.add(buf.toString().trim())
            }
        }
        return chunks
    }
}
