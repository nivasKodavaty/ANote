package com.gtr3.ANote.notes.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "note_messages")
data class NoteMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    val note: Note,

    @Column(nullable = false)
    val role: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    val message: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
