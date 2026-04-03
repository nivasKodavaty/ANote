package com.gtr3.ANote.notes.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

data class CreateNoteRequest(
    @field:NotBlank val title: String,
    val content: String? = null,
    val useAi: Boolean = false,
    val folderName: String? = null
)

data class UpdateNoteRequest(
    val noteId: Long,
    val title: String? = null,
    val content: String? = null,
    val folderName: String? = null,
    val isPinned: Boolean? = null
)

data class ChatRequest(
    @field:NotBlank val message: String
)

data class RefineSelectionRequest(
    @field:NotBlank val selectedText: String,
    @field:NotBlank val instruction: String
)

data class RefineSelectionResponse(val replacement: String)

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
    val isPinned: Boolean = false,
    val folderName: String? = null,
    val messages: List<MessageResponse> = emptyList()
)
