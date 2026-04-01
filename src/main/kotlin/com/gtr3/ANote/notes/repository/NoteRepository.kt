package com.gtr3.ANote.notes.repository

import com.gtr3.ANote.notes.entity.Note
import org.springframework.data.jpa.repository.JpaRepository

interface NoteRepository : JpaRepository<Note, Long> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<Note>
    fun findByIdAndUserId(id: Long, userId: Long): Note?
}
