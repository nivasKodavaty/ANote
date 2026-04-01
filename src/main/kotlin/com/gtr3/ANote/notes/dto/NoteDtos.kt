package com.gtr3.ANote.notes.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

data class CreateNoteRequest(
    @field:NotBlank val title: String
)

data class UpdateNoteRequest(
    val noteId: Long,
    val title: String?,
    val content: String?
)

data class ChatRequest(
    @field:NotBlank val message: String
)

data class MessageResponse(
    val id: Long,
    val role: String,
    val message: String,
    val createdAt: LocalDateTime
)

data class NoteResponse(
    val id: Long,
    val title: String,
    val content: String?,
    val updatedAt: LocalDateTime,
    val messages: List<MessageResponse> = emptyList()
)
