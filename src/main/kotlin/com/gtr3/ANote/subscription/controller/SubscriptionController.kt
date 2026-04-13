package com.gtr3.ANote.subscription.controller

import com.gtr3.ANote.subscription.service.SubscriptionService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/subscription")
class SubscriptionController(private val subscriptionService: SubscriptionService) {

    data class ActivateRequest(val purchaseToken: String)

    @GetMapping("/status")
    fun getStatus(auth: Authentication): ResponseEntity<SubscriptionService.QuotaResponse> =
        ResponseEntity.ok(subscriptionService.getStatus(auth.name))

    @PostMapping("/activate")
    fun activate(
        @RequestBody request: ActivateRequest,
        auth: Authentication
    ): ResponseEntity<SubscriptionService.QuotaResponse> =
        ResponseEntity.ok(subscriptionService.activatePro(auth.name, request.purchaseToken))

    @PostMapping("/revoke")
    fun revoke(auth: Authentication): ResponseEntity<SubscriptionService.QuotaResponse> =
        ResponseEntity.ok(subscriptionService.revokePro(auth.name))

    /** Called by Android after a rewarded ad is fully watched. Grants extra AI calls. */
    @PostMapping("/reward-ad")
    fun rewardAd(auth: Authentication): ResponseEntity<SubscriptionService.QuotaResponse> =
        ResponseEntity.ok(subscriptionService.rewardAd(auth.name))
}
