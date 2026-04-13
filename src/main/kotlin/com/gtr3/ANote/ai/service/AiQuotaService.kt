package com.gtr3.ANote.ai.service

import com.gtr3.ANote.auth.entity.User
import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.common.exception.AiRateLimitException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AiQuotaService(
    private val userRepository: UserRepository,
    @Value("\${ai.free-daily-limit:15}") private val freeDailyLimit: Int
) {
    /**
     * Checks that the user has remaining quota, then increments the call count.
     * PRO users are always allowed through.
     * Throws [AiRateLimitException] if the free quota is exhausted.
     */
    fun consumeQuota(user: User) {
        if (user.subscriptionTier == "PRO") return   // unlimited

        val today = LocalDate.now().toString()        // "YYYY-MM-DD"

        // Reset counter if it's a new day
        if (user.dailyAiDate != today) {
            user.dailyAiCalls = 0
            user.dailyAiDate  = today
        }

        if (user.dailyAiCalls >= freeDailyLimit) {
            val resetAt = LocalDate.now().plusDays(1).toString() + "T00:00:00"
            throw AiRateLimitException(remaining = 0, resetAt = resetAt)
        }

        user.dailyAiCalls++
        user.dailyAiDate = today
        userRepository.save(user)
    }

    /** Returns remaining quota for the day (PRO → -1 = unlimited). */
    fun getRemainingQuota(user: User): Int {
        if (user.subscriptionTier == "PRO") return -1
        val today = LocalDate.now().toString()
        val used  = if (user.dailyAiDate == today) user.dailyAiCalls else 0
        return (freeDailyLimit - used).coerceAtLeast(0)
    }
}
