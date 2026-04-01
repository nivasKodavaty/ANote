package com.gtr3.ANote.notes.repository

import com.gtr3.ANote.notes.entity.NoteMessage
import org.springframework.data.jpa.repository.JpaRepository

interface NoteMessageRepository : JpaRepository<NoteMessage, Long> {
    fun findAllByNoteIdOrderByCreatedAtAsc(noteId: Long): List<NoteMessage>
}
