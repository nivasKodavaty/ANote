package com.gtr3.ANote.collab.entity

import com.gtr3.ANote.auth.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "collab_note_participants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "note_id"])]
)
data class CollabNoteParticipant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    val note: CollabNote,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now()
)
