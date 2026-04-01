package com.gtr3.ANote.ai.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GeminiService(
    @Value("\${gemini.api-key}") private val apiKey: String,
    @Value("\${gemini.model}") private val model: String,
    @Value("\${gemini.base-url}") private val baseUrl: String
) {
    private val client: WebClient by lazy {
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-goog-api-key", apiKey)
            .build()
    }

    fun testConnection(): String {
        val body = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to "Say hello in one word.")))
            )
        )
        return callGemini(body)
    }

    fun generateNoteContent(title: String): String {
        val prompt = """
            You are a smart personal note-taking assistant. Your job is to generate a helpful, well-structured note based on the title given.

            First understand what the title is about, then choose the most appropriate format and sections for it.

            Examples of how to adapt:
            - Recipe or how-to → use: Ingredients/Items, Steps, Tips
            - Movie, book, or show → use: Overview, Key Highlights, Why It's Worth It
            - Concept or topic to learn → use: What It Is, Key Points, Examples, Resources
            - Goal or habit → use: Why It Matters, How To Start, Tips To Stay Consistent
            - Place or travel → use: About, Best Time To Visit, What To Do, What To Pack
            - Person or biography → use: Who They Are, Key Achievements, Why They Matter
            - General life note → use whatever sections make the note most useful

            Rules:
            - Use ## for section headings
            - Be concise and practical
            - Do NOT use a fixed template — adapt the structure to what actually makes sense for this title
            - Write as if you're helping someone understand or act on this topic in real life

            Title: $title
        """.trimIndent()

        val body = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            )
        )
        return callGemini(body)
    }

    fun chat(history: List<Map<String, Any>>, userMessage: String): String {
        val contents = history + listOf(
            mapOf("role" to "user", "parts" to listOf(mapOf("text" to userMessage)))
        )
        val body = mapOf("contents" to contents)
        return callGemini(body)
    }

    private fun callGemini(body: Map<String, Any>): String {
        val response = client.post()
            .uri("/v1beta/models/$model:generateContent")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        @Suppress("UNCHECKED_CAST")
        val candidates = response?.get("candidates") as? List<Map<String, Any>>
        val content = candidates?.firstOrNull()?.get("content") as? Map<String, Any>
        val parts = content?.get("parts") as? List<Map<String, Any>>
        return parts?.firstOrNull()?.get("text") as? String
            ?: throw RuntimeException("Empty response from Gemini")
    }
}
