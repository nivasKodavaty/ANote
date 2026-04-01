package com.gtr3.ANote.notes.controller

import com.gtr3.ANote.notes.dto.CreateNoteRequest
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

    @DeleteMapping("/{id}")
    fun deleteNote(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<Void> {
        noteService.deleteNote(id, user.username)
        return ResponseEntity.noContent().build()
    }
}
