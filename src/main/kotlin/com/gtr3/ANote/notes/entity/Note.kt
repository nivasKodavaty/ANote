package com.gtr3.ANote.notes.entity

import com.gtr3.ANote.auth.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notes")
data class  Note(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_pinned", nullable = false, columnDefinition = "boolean default false")
    var isPinned: Boolean = false,

    @Column(name = "folder_name", length = 200)
    var folderName: String? = null
)
