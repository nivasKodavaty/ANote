package com.gtr3.ANote.collab.repository

import com.gtr3.ANote.collab.entity.CollabNoteMessage
import org.springframework.data.jpa.repository.JpaRepository

interface CollabNoteMessageRepository : JpaRepository<CollabNoteMessage, Long> {
    fun findAllByNoteIdOrderByCreatedAtAsc(noteId: Long): List<CollabNoteMessage>
    fun findTop4ByNoteIdOrderByCreatedAtDesc(noteId: Long): List<CollabNoteMessage>
    fun deleteAllByNoteId(noteId: Long)
}
