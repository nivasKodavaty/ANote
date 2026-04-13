package com.gtr3.ANote.collab.entity

import com.gtr3.ANote.auth.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "collab_notes")
data class CollabNote(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** Unique human-shareable code, e.g. "XK7P2Q" */
    @Column(name = "share_code", unique = true, nullable = false, length = 8)
    val shareCode: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    val createdBy: User,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /** Short display name of last editor (email prefix) */
    @Column(name = "last_edited_by", length = 200)
    var lastEditedBy: String? = null,

    /** JPA optimistic locking — incremented on every save */
    @Version
    var version: Long = 0
)
