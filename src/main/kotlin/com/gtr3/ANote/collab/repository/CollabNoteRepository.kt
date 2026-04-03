package com.gtr3.ANote.collab.repository

import com.gtr3.ANote.collab.entity.CollabNote
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CollabNoteRepository : JpaRepository<CollabNote, Long> {
    fun findByShareCode(shareCode: String): CollabNote?

    /**
     * Only return notes where the user has an active participant record.
     * Using createdBy was a bug — creator leaving still matched the OR clause.
     * Now access is solely determined by the participants table.
     */
    @Query("""
        SELECT cn FROM CollabNote cn
        WHERE cn.id IN (
            SELECT p.note.id FROM CollabNoteParticipant p WHERE p.user.id = :userId
        )
        ORDER BY cn.updatedAt DESC
    """)
    fun findAllAccessibleByUserId(userId: Long): List<CollabNote>

    fun existsByShareCode(shareCode: String): Boolean
}
