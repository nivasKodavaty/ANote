package com.gtr3.ANote.notes.controller

import com.gtr3.ANote.notes.dto.ChatRequest
import com.gtr3.ANote.notes.dto.CreateNoteRequest
import com.gtr3.ANote.notes.dto.RefineSelectionRequest
import com.gtr3.ANote.notes.dto.RefineSelectionResponse
import com.gtr3.ANote.notes.dto.UpdateNoteRequest
import com.gtr3.ANote.notes.dto.NoteResponse
import com.gtr3.ANote.notes.service.NoteService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notes")
class NoteController(private val noteService: NoteService) {

    @GetMapping
    fun getAllNotes(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<NoteResponse>> =
        ResponseEntity.ok(noteService.getAllNotes(user.username))

    @PostMapping
    fun createNote(
        @Valid @RequestBody request: CreateNoteRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.createNote(request, user.username))

    @GetMapping("/{id}")
    fun getNoteById(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.getNoteById(id, user.username))

    @PutMapping
    fun updateNote(
        @RequestBody request: UpdateNoteRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.updateNote(request, user.username))

    @PostMapping("/{id}/chat")
    fun chatOnNote(
        @PathVariable id: Long,
        @Valid @RequestBody request: ChatRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.chatOnNote(id, request.message, user.username))

    @PostMapping("/{id}/refine-selection")
    fun refineSelection(
        @PathVariable id: Long,
        @Valid @RequestBody request: RefineSelectionRequest,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<RefineSelectionResponse> =
        ResponseEntity.ok(noteService.refineSelection(id, request, user.username))

    @PatchMapping("/{id}/pin")
    fun pinNote(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<NoteResponse> =
        ResponseEntity.ok(noteService.pinNote(id, user.username))

    @DeleteMapping("/{id}")
    fun deleteNote(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<Void> {
        noteService.deleteNote(id, user.username)
        return ResponseEntity.noContent().build()
    }
}
