package com.gtr3.ANote.collab.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "collab_note_messages")
data class CollabNoteMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    val note: CollabNote,

    @Column(nullable = false)
    val role: String,          // "user" | "assistant"

    @Column(columnDefinition = "TEXT", nullable = false)
    val message: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
