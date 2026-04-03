package com.gtr3.ANote.collab.dto

import com.gtr3.ANote.notes.dto.MessageResponse
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

// ── Requests ─────────────────────────────────────────────────────────────────

data class CreateCollabNoteRequest(
    @field:NotBlank val title: String,
    val content: String? = null,
    val useAi: Boolean = false
)

data class JoinCollabNoteRequest(
    @field:NotBlank val shareCode: String
)

data class UpdateCollabNoteRequest(
    val title: String? = null,
    val content: String? = null,
    /** Client must echo the version it last read; mismatch → 409 */
    val version: Long
)

data class CollabChatRequest(
    @field:NotBlank val message: String
)

data class CollabRefineSelectionRequest(
    @field:NotBlank val selectedText: String,
    @field:NotBlank val instruction: String
)

// ── Responses ────────────────────────────────────────────────────────────────

data class CollabNoteResponse(
    val id: Long,
    val shareCode: String,
    val title: String,
    val content: String?,
    val updatedAt: LocalDateTime,
    val lastEditedBy: String?,
    val version: Long,
    val participantCount: Int,
    val messages: List<MessageResponse> = emptyList()
)

/** Returned as HTTP 409 body when a save conflicts with a concurrent edit */
data class ConflictResponse(
    val error: String = "conflict",
    val currentTitle: String,
    val currentContent: String?,
    val currentVersion: Long
)
