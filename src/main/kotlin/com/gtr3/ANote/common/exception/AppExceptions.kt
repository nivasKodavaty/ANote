package com.gtr3.ANote.common.exception

class AiRateLimitException(val remaining: Int = 0, val resetAt: String = "") :
    RuntimeException("Daily AI limit reached. Upgrade to Pro for unlimited access.")

class SubscriptionNotFoundException : RuntimeException("Subscription not found")
class InvalidPurchaseTokenException : RuntimeException("Purchase token is invalid or already used")
