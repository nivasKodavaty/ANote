package com.gtr3.ANote.collab.repository

import com.gtr3.ANote.collab.entity.CollabNoteParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface CollabNoteParticipantRepository : JpaRepository<CollabNoteParticipant, Long> {
    fun existsByUserIdAndNoteId(userId: Long, noteId: Long): Boolean
    fun countByNoteId(noteId: Long): Int
    fun deleteByUserIdAndNoteId(userId: Long, noteId: Long)
    fun findAllByNoteId(noteId: Long): List<CollabNoteParticipant>
}
