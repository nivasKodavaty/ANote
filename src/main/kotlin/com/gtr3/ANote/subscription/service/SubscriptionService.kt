package com.gtr3.ANote.subscription.service

import com.gtr3.ANote.auth.entity.User
import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.common.exception.InvalidPurchaseTokenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class SubscriptionService(
    private val userRepository: UserRepository,
    @Value("\${ai.free-daily-limit:15}") private val freeDailyLimit: Int,
    @Value("\${ai.reward-calls:5}") private val rewardCalls: Int,
    @Value("\${ai.max-daily-ad-watches:3}") private val maxDailyAdWatches: Int
) {

    data class QuotaResponse(
        val subscriptionTier: String,
        val dailyAiCalls: Int,
        val dailyAiDate: String?,
        val remainingCalls: Int,      // -1 = unlimited
        val freeDailyLimit: Int,
        val dailyAdWatches: Int = 0,
        val maxDailyAdWatches: Int = 3
    )

    /**
     * Activates PRO for a user after receiving a valid Google Play purchase token.
     * In production, validate the token against the Google Play Developer API here.
     * For now, any non-blank token from a signed request is accepted (token is stored
     * to prevent replay attacks).
     */
    fun activatePro(email: String, purchaseToken: String): QuotaResponse {
        if (purchaseToken.isBlank()) throw InvalidPurchaseTokenException()
        val user = getUser(email)
        if (user.purchaseToken == purchaseToken && user.subscriptionTier == "PRO") {
            return buildQuotaResponse(user)
        }
        val updated = user.copy(subscriptionTier = "PRO", purchaseToken = purchaseToken)
        userRepository.save(updated)
        return buildQuotaResponse(updated)
    }

    /** Revoke PRO (called by Play webhook for cancellations). */
    fun revokePro(email: String): QuotaResponse {
        val user = getUser(email)
        val updated = user.copy(subscriptionTier = "FREE")
        userRepository.save(updated)
        return buildQuotaResponse(updated)
    }

    fun getStatus(email: String): QuotaResponse {
        val user = getUser(email)
        return buildQuotaResponse(user)
    }

    /**
     * Called after the Android client verifies a rewarded ad was fully watched.
     * Grants [rewardCalls] extra AI calls for today, capped at [maxDailyAdWatches] per day.
     * PRO users are ignored (unlimited already).
     */
    fun rewardAd(email: String): QuotaResponse {
        val user = getUser(email)
        if (user.subscriptionTier == "PRO") return buildQuotaResponse(user)

        val today = java.time.LocalDate.now().toString()
        // Reset ad watch counter if it's a new day
        val adWatches = if (user.dailyAiDate == today) user.dailyAdWatches else 0
        if (adWatches >= maxDailyAdWatches) {
            // Silently return current status — client should prevent reaching here
            return buildQuotaResponse(user)
        }

        // Grant extra calls by reducing the used-call counter (floor = 0)
        user.dailyAiCalls   = maxOf(0, user.dailyAiCalls - rewardCalls)
        user.dailyAdWatches = adWatches + 1
        user.dailyAiDate    = today
        userRepository.save(user)
        return buildQuotaResponse(user)
    }

    private fun getUser(email: String) =
        userRepository.findByEmail(email).orElseThrow { UsernameNotFoundException("User not found") }

    private fun buildQuotaResponse(user: User): QuotaResponse {
        val today     = java.time.LocalDate.now().toString()
        val remaining = if (user.subscriptionTier == "PRO") -1
                        else {
                            val used = if (user.dailyAiDate == today) user.dailyAiCalls else 0
                            (freeDailyLimit - used).coerceAtLeast(0)
                        }
        val adWatches = if (user.dailyAiDate == today) user.dailyAdWatches else 0
        return QuotaResponse(
            subscriptionTier  = user.subscriptionTier,
            dailyAiCalls      = user.dailyAiCalls,
            dailyAiDate       = user.dailyAiDate,
            remainingCalls    = remaining,
            freeDailyLimit    = freeDailyLimit,
            dailyAdWatches    = adWatches,
            maxDailyAdWatches = maxDailyAdWatches
        )
    }
}
