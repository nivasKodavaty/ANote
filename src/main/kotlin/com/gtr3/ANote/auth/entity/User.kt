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
    val sex: String? = null,

    @Column(name = "subscription_tier", nullable = false)
    val subscriptionTier: String = "FREE",

    @Column(name = "daily_ai_calls", nullable = false, columnDefinition = "integer default 0")
    var dailyAiCalls: Int = 0,

    @Column(name = "daily_ai_date")
    var dailyAiDate: String? = null,

    @Column(name = "purchase_token")
    var purchaseToken: String? = null,

    // Tracks rewarded-ad watches per day (reset with dailyAiDate)
    @Column(name = "daily_ad_watches", nullable = false, columnDefinition = "integer default 0")
    var dailyAdWatches: Int = 0
)
