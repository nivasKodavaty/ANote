package com.gtr3.ANote.collab.service

import com.gtr3.ANote.ai.service.GeminiService
import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.collab.dto.*
import com.gtr3.ANote.collab.entity.CollabNote
import com.gtr3.ANote.collab.entity.CollabNoteMessage
import com.gtr3.ANote.collab.entity.CollabNoteParticipant
import com.gtr3.ANote.collab.repository.CollabNoteMessageRepository
import com.gtr3.ANote.collab.repository.CollabNoteParticipantRepository
import com.gtr3.ANote.collab.repository.CollabNoteRepository
import com.gtr3.ANote.notes.dto.MessageResponse
import com.gtr3.ANote.notes.dto.RefineSelectionResponse
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime

@Service
class CollabNoteService(
    private val collabNoteRepository: CollabNoteRepository,
    private val collabNoteMessageRepository: CollabNoteMessageRepository,
    private val collabNoteParticipantRepository: CollabNoteParticipantRepository,
    private val userRepository: UserRepository,
    private val geminiService: GeminiService
) {

    fun getAllNotes(email: String): List<CollabNoteResponse> {
        val user = getUser(email)
        return collabNoteRepository.findAllAccessibleByUserId(user.id)
            .map { note ->
                val count = collabNoteParticipantRepository.countByNoteId(note.id)
                note.toResponse(emptyList(), count)
            }
    }

    fun getNoteByShareCode(shareCode: String, email: String): CollabNoteResponse {
        val user = getUser(email)
        val note = getAccessibleNote(shareCode, user.id)
        val messages = collabNoteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        val count = collabNoteParticipantRepository.countByNoteId(note.id)
        return note.toResponse(messages, count)
    }

    @Transactional
    fun createNote(request: CreateCollabNoteRequest, email: String): CollabNoteResponse {
        val user = getUser(email)
        val shareCode = generateUniqueShareCode()

        val note = collabNoteRepository.save(
            CollabNote(shareCode = shareCode, createdBy = user, title = request.title)
        )
        // Creator is also a participant
        collabNoteParticipantRepository.save(CollabNoteParticipant(user = user, note = note))

        if (request.useAi) {
            val generated = geminiService.generateNoteContent(request.title)
            note.content = generated
            note.updatedAt = LocalDateTime.now()
            note.lastEditedBy = user.email.substringBefore("@")
            collabNoteRepository.save(note)
            val msg = collabNoteMessageRepository.save(
                CollabNoteMessage(note = note, role = "assistant", message = generated)
            )
            return note.toResponse(listOf(msg), 1)
        } else {
            request.content?.let {
                note.content = it
                note.updatedAt = LocalDateTime.now()
                note.lastEditedBy = user.email.substringBefore("@")
                collabNoteRepository.save(note)
            }
            return note.toResponse(emptyList(), 1)
        }
    }

    @Transactional
    fun joinNote(request: JoinCollabNoteRequest, email: String): CollabNoteResponse {
        val user = getUser(email)
        val shareCode = request.shareCode.trim().uppercase()
        val note = collabNoteRepository.findByShareCode(shareCode)
            ?: throw NoSuchElementException("No note found with code $shareCode")

        if (!collabNoteParticipantRepository.existsByUserIdAndNoteId(user.id, note.id)) {
            collabNoteParticipantRepository.save(CollabNoteParticipant(user = user, note = note))
        }

        val messages = collabNoteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        val count = collabNoteParticipantRepository.countByNoteId(note.id)
        return note.toResponse(messages, count)
    }

    /**
     * Update note content/title. Returns the saved note on success.
     * Throws [VersionConflictException] if the client's version is stale.
     */
    @Transactional
    fun updateNote(
        shareCode: String,
        request: UpdateCollabNoteRequest,
        email: String
    ): CollabNoteResponse {
        val user = getUser(email)
        val note = getAccessibleNote(shareCode, user.id)

        // Manual version check → gives a clean 409 without relying on JPA flush timing
        if (note.version != request.version) {
            val messages = collabNoteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
            val count = collabNoteParticipantRepository.countByNoteId(note.id)
            throw VersionConflictException(note, messages, count)
        }

        request.title?.let { note.title = it }
        request.content?.let { note.content = it }
        note.updatedAt = LocalDateTime.now()
        note.lastEditedBy = user.email.substringBefore("@")

        // JPA @Version will auto-increment; if a concurrent save slips through, it throws
        // OptimisticLockException which we catch below
        return try {
            val saved = collabNoteRepository.saveAndFlush(note)
            val messages = collabNoteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
            val count = collabNoteParticipantRepository.countByNoteId(note.id)
            saved.toResponse(messages, count)
        } catch (e: Exception) {
            val root = e.cause ?: e
            if (root is jakarta.persistence.OptimisticLockException ||
                root is org.springframework.orm.ObjectOptimisticLockingFailureException
            ) {
                val fresh = collabNoteRepository.findByShareCode(shareCode)!!
                val messages = collabNoteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(fresh.id)
                val count = collabNoteParticipantRepository.countByNoteId(fresh.id)
                throw VersionConflictException(fresh, messages, count)
            }
            throw e
        }
    }

    @Transactional
    fun chatOnNote(shareCode: String, userMessage: String, email: String): CollabNoteResponse {
        val user = getUser(email)
        val note = getAccessibleNote(shareCode, user.id)

        val contextMessages = collabNoteMessageRepository
            .findTop4ByNoteIdOrderByCreatedAtDesc(note.id)
            .reversed()

        val systemPrompt = """
            You are a collaborative note editing assistant for the note titled "${note.title}".
            Current note content:
            ${note.content ?: "No content yet."}
            Respond with ONLY the updated note content using markdown formatting.
            Do not include any explanation — just the updated note content.
        """.trimIndent()

        val history = mutableListOf<Map<String, Any>>(
            mapOf("role" to "user", "parts" to listOf(mapOf("text" to systemPrompt)))
        )
        if (contextMessages.isEmpty() || contextMessages.first().role == "user") {
            history.add(mapOf("role" to "model", "parts" to listOf(mapOf("text" to (note.content ?: "I'll help.")))))
        }
        contextMessages.forEach { msg ->
            val geminiRole = if (msg.role == "assistant") "model" else "user"
            history.add(mapOf("role" to geminiRole, "parts" to listOf(mapOf("text" to msg.message))))
        }
        if ((history.last()["role"] as String) == "user") {
            history.add(mapOf("role" to "model", "parts" to listOf(mapOf("text" to (note.content ?: "Understood.")))))
        }

        val aiResponse = geminiService.chat(history, userMessage)
        collabNoteMessageRepository.save(CollabNoteMessage(note = note, role = "user", message = userMessage))
        collabNoteMessageRepository.save(CollabNoteMessage(note = note, role = "assistant", message = aiResponse))

        val allMessages = collabNoteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        val count = collabNoteParticipantRepository.countByNoteId(note.id)
        return note.toResponse(allMessages, count)
    }

    fun refineSelection(
        shareCode: String,
        request: CollabRefineSelectionRequest,
        email: String
    ): RefineSelectionResponse {
        val user = getUser(email)
        val note = getAccessibleNote(shareCode, user.id)
        val replacement = geminiService.refineSelection(request.selectedText, request.instruction, note.content)
        return RefineSelectionResponse(replacement = replacement)
    }

    @Transactional
    fun leaveNote(shareCode: String, email: String) {
        val user = getUser(email)
        val note = collabNoteRepository.findByShareCode(shareCode)
            ?: throw NoSuchElementException("Note not found")
        collabNoteParticipantRepository.deleteByUserIdAndNoteId(user.id, note.id)
        // If the creator leaves and there are no other participants, delete the note
        if (note.createdBy.id == user.id &&
            collabNoteParticipantRepository.countByNoteId(note.id) == 0
        ) {
            collabNoteMessageRepository.deleteAllByNoteId(note.id)
            collabNoteRepository.delete(note)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getUser(email: String) =
        userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found") }

    private fun getAccessibleNote(shareCode: String, userId: Long): CollabNote {
        val note = collabNoteRepository.findByShareCode(shareCode.uppercase())
            ?: throw NoSuchElementException("Note not found")
        val isCreator = note.createdBy.id == userId
        val isParticipant = collabNoteParticipantRepository.existsByUserIdAndNoteId(userId, note.id)
        if (!isCreator && !isParticipant) throw SecurityException("Access denied")
        return note
    }

    private fun generateUniqueShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // no 0/O/1/I confusion
        val rng = SecureRandom()
        var code: String
        do { code = (1..6).map { chars[rng.nextInt(chars.length)] }.joinToString("") }
        while (collabNoteRepository.existsByShareCode(code))
        return code
    }

    private fun CollabNote.toResponse(
        messages: List<CollabNoteMessage>,
        participantCount: Int
    ) = CollabNoteResponse(
        id             = id,
        shareCode      = shareCode,
        title          = title,
        content        = content,
        updatedAt      = updatedAt,
        lastEditedBy   = lastEditedBy,
        version        = version,
        participantCount = participantCount,
        messages       = messages.map { MessageResponse(it.id, it.role, it.message, it.createdAt) }
    )
}

/** Thrown when the client's version is stale; carries the current state for the 409 body */
class VersionConflictException(
    val note: CollabNote,
    val messages: List<CollabNoteMessage>,
    val participantCount: Int
) : RuntimeException("Version conflict on note ${note.shareCode}")
