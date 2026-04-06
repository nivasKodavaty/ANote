package com.gtr3.ANote.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash")
    val passwordHash: String? = null,

    @Column(name = "auth_provider", nullable = false)
    val authProvider: String = "LOCAL",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "display_name")
    val displayName: String? = null,

    @Column(name = "date_of_birth")
    val dateOfBirth: String? = null,   // stored as "YYYY-MM-DD"

    @Column(name = "sex")
    val sex: String? = null
)
