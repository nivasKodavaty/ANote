package com.gtr3.ANote.notes.service

import com.gtr3.ANote.ai.service.GeminiService
import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.notes.dto.CreateNoteRequest
import com.gtr3.ANote.notes.dto.UpdateNoteRequest
import com.gtr3.ANote.notes.dto.MessageResponse
import com.gtr3.ANote.notes.dto.NoteResponse
import com.gtr3.ANote.notes.entity.Note
import com.gtr3.ANote.notes.entity.NoteMessage
import com.gtr3.ANote.notes.repository.NoteMessageRepository
import com.gtr3.ANote.notes.repository.NoteRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val noteMessageRepository: NoteMessageRepository,
    private val userRepository: UserRepository,
    private val geminiService: GeminiService
) {
    fun getAllNotes(email: String): List<NoteResponse> {
        val user = getUser(email)
        return noteRepository.findAllByUserIdOrderByUpdatedAtDesc(user.id)
            .map { it.toResponse() }
    }

    fun getNoteById(id: Long, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(id, user.id)
            ?: throw NoSuchElementException("Note not found")
        val messages = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        return note.toResponse(messages)
    }

    @Transactional
    fun createNote(request: CreateNoteRequest, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.save(
            Note(user = user, title = request.title)
        )
        val generatedContent = geminiService.generateNoteContent(request.title)
        note.content = generatedContent
        note.updatedAt = java.time.LocalDateTime.now()
        noteRepository.save(note)
        val assistantMessage = noteMessageRepository.save(
            com.gtr3.ANote.notes.entity.NoteMessage(
                note = note,
                role = "assistant",
                message = generatedContent
            )
        )
        return note.toResponse(listOf(assistantMessage))
    }

    @Transactional
    fun updateNote(request: UpdateNoteRequest, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(request.noteId, user.id)
            ?: throw NoSuchElementException("Note not found")
        request.title?.let { note.title = it }
        request.content?.let { note.content = it }
        note.updatedAt = java.time.LocalDateTime.now()
        noteRepository.save(note)
        val messages = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        return note.toResponse(messages)
    }

    @Transactional
    fun deleteNote(id: Long, email: String) {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(id, user.id)
            ?: throw NoSuchElementException("Note not found")
        noteRepository.delete(note)
    }

    private fun getUser(email: String) =
        userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found") }

    private fun Note.toResponse(messages: List<NoteMessage> = emptyList()) = NoteResponse(
        id = id,
        title = title,
        content = content,
        updatedAt = updatedAt,
        messages = messages.map { MessageResponse(it.id, it.role, it.message, it.createdAt) }
    )
}
