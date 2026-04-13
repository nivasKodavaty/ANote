package com.gtr3.ANote.collab.controller

import com.gtr3.ANote.collab.dto.*
import com.gtr3.ANote.collab.service.CollabNoteService
import com.gtr3.ANote.collab.service.VersionConflictException
import com.gtr3.ANote.notes.dto.RefineSelectionResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/collab")
class CollabNoteController(private val collabNoteService: CollabNoteService) {

    @GetMapping
    fun getAllNotes(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<CollabNoteResponse>> =
        ResponseEntity.ok(collabNoteService.getAllNotes(user.username))

    @GetMapping("/{shareCode}")
    fun getNote(
        @PathVariable shareCode: String,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<CollabNoteResponse> =
        ResponseEntity.ok(collabNoteService.getNoteByShareCode(shareCode, user.username))

    @PostMapping
    fun createNote(
        @Valid @RequestBody request: CreateCollabNoteRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<CollabNoteResponse> =
        ResponseEntity.ok(collabNoteService.createNote(request, user.username))

    @PostMapping("/join")
    fun joinNote(
        @Valid @RequestBody request: JoinCollabNoteRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<CollabNoteResponse> =
        ResponseEntity.ok(collabNoteService.joinNote(request, user.username))

    @PutMapping("/{shareCode}")
    fun updateNote(
        @PathVariable shareCode: String,
        @RequestBody request: UpdateCollabNoteRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(collabNoteService.updateNote(shareCode, request, user.username))
        } catch (e: VersionConflictException) {
            ResponseEntity.status(409).body(
                ConflictResponse(
                    currentTitle   = e.note.title,
                    currentContent = e.note.content,
                    currentVersion = e.note.version
                )
            )
        }
    }

    @PostMapping("/{shareCode}/chat")
    fun chat(
        @PathVariable shareCode: String,
        @Valid @RequestBody request: CollabChatRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<CollabNoteResponse> =
        ResponseEntity.ok(collabNoteService.chatOnNote(shareCode, request.message, user.username))

    @PostMapping("/{shareCode}/refine-selection")
    fun refineSelection(
        @PathVariable shareCode: String,
        @Valid @RequestBody request: CollabRefineSelectionRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<RefineSelectionResponse> =
        ResponseEntity.ok(collabNoteService.refineSelection(shareCode, request, user.username))

    @GetMapping("/{shareCode}/participants")
    fun getParticipants(
        @PathVariable shareCode: String,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<List<ParticipantResponse>> =
        ResponseEntity.ok(collabNoteService.getParticipants(shareCode, user.username))

    @DeleteMapping("/{shareCode}/leave")
    fun leaveNote(
        @PathVariable shareCode: String,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<Void> {
        collabNoteService.leaveNote(shareCode, user.username)
        return ResponseEntity.noContent().build()
    }
}
