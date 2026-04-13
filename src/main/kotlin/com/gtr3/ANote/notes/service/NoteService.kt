package com.gtr3.ANote.notes.service

import com.gtr3.ANote.ai.service.AiQuotaService
import com.gtr3.ANote.ai.service.GeminiService
import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.notes.dto.CreateNoteRequest
import com.gtr3.ANote.notes.dto.RefineSelectionRequest
import com.gtr3.ANote.notes.dto.RefineSelectionResponse
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
    private val geminiService: GeminiService,
    private val aiQuotaService: AiQuotaService
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
            Note(user = user, title = request.title, folderName = request.folderName)
        )
        if (request.useAi) {
            aiQuotaService.consumeQuota(user)
            val generatedContent = geminiService.generateNoteContent(request.title)
            note.content = generatedContent
            note.updatedAt = java.time.LocalDateTime.now()
            noteRepository.save(note)
            val assistantMessage = noteMessageRepository.save(
                NoteMessage(note = note, role = "assistant", message = generatedContent)
            )
            return note.toResponse(listOf(assistantMessage))
        } else {
            request.content?.let {
                note.content = it
                note.updatedAt = java.time.LocalDateTime.now()
                noteRepository.save(note)
            }
            return note.toResponse()
        }
    }

    @Transactional
    fun updateNote(request: UpdateNoteRequest, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(request.noteId, user.id)
            ?: throw NoSuchElementException("Note not found")
        request.title?.let { note.title = it }
        request.content?.let { note.content = it }
        request.folderName?.let { note.folderName = it.ifBlank { null } }
        request.isPinned?.let { note.isPinned = it }
        note.updatedAt = java.time.LocalDateTime.now()
        noteRepository.save(note)
        val messages = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        return note.toResponse(messages)
    }

    @Transactional
    fun pinNote(id: Long, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(id, user.id)
            ?: throw NoSuchElementException("Note not found")
        note.isPinned = !note.isPinned
        noteRepository.save(note)
        return note.toResponse()
    }

    @Transactional
    fun chatOnNote(noteId: Long, userMessage: String, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(noteId, user.id)
            ?: throw NoSuchElementException("Note not found")

        // Fetch last 4 messages for context (reversed to chronological order)
        val contextMessages = noteMessageRepository
            .findTop4ByNoteIdOrderByCreatedAtDesc(noteId)
            .reversed()

        // Build Gemini conversation history
        // First turn: system context as a "user" message
        val systemPrompt = """
            You are a note editing assistant for the note titled "${note.title}".

            Current note content:
            ${note.content ?: "No content yet."}

            The user will ask you to modify, expand, or refine this note.
            Respond with ONLY the updated note content using the same markdown formatting (## for headers, - for bullets).
            Do not include any explanation or meta-commentary — just the updated note content.
        """.trimIndent()

        val history = mutableListOf<Map<String, Any>>(
            mapOf("role" to "user", "parts" to listOf(mapOf("text" to systemPrompt)))
        )

        // Bridge: if there are no context messages or first context message is "user",
        // add a model turn to keep proper alternation
        if (contextMessages.isEmpty() || contextMessages.first().role == "user") {
            history.add(
                mapOf(
                    "role" to "model",
                    "parts" to listOf(mapOf("text" to (note.content ?: "I'll help you modify this note.")))
                )
            )
        }

        // Append context messages with proper role mapping
        contextMessages.forEach { msg ->
            val geminiRole = if (msg.role == "assistant") "model" else "user"
            history.add(mapOf("role" to geminiRole, "parts" to listOf(mapOf("text" to msg.message))))
        }

        // Ensure history ends on a "model" turn before the new user message
        if ((history.last()["role"] as String) == "user") {
            history.add(
                mapOf(
                    "role" to "model",
                    "parts" to listOf(mapOf("text" to (note.content ?: "Understood. How can I help?")))
                )
            )
        }

        // Call Gemini with conversation history + new user message
        aiQuotaService.consumeQuota(user)
        val aiResponse = geminiService.chat(history, userMessage)

        // Persist user message and AI response — note content is NOT updated here;
        // the client shows the AI response in the editor for user review/edit,
        // and only persists via the updateNote endpoint once the user confirms.
        noteMessageRepository.save(NoteMessage(note = note, role = "user", message = userMessage))
        noteMessageRepository.save(NoteMessage(note = note, role = "assistant", message = aiResponse))

        val allMessages = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(noteId)
        return note.toResponse(allMessages)
    }

    fun refineSelection(noteId: Long, request: RefineSelectionRequest, email: String): RefineSelectionResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(noteId, user.id)
            ?: throw NoSuchElementException("Note not found")
        aiQuotaService.consumeQuota(user)
        val replacement = geminiService.refineSelection(request.selectedText, request.instruction, note.content)
        return RefineSelectionResponse(replacement = replacement)
    }

    @Transactional
    fun deleteNote(id: Long, email: String) {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(id, user.id)
            ?: throw NoSuchElementException("Note not found")
        noteMessageRepository.deleteAllByNoteId(note.id)
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
        isPinned = isPinned,
        folderName = folderName,
        messages = messages.map { MessageResponse(it.id, it.role, it.message, it.createdAt) }
    )
}
