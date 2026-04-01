package com.gtr3.ANote.ai.controller

import com.gtr3.ANote.ai.service.GeminiService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ai")
class AiTestController(private val geminiService: GeminiService) {

    @GetMapping("/ping")
    fun ping(): Map<String, String> {
        val response = geminiService.testConnection()
        return mapOf("status" to "connected", "gemini_says" to response)
    }
}
