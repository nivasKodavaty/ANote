package com.gtr3.ANote.common

import com.gtr3.ANote.common.exception.AiRateLimitException
import com.gtr3.ANote.common.exception.InvalidPurchaseTokenException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    data class ErrorResponse(
        val status: Int,
        val error: String,
        val message: String,
        val path: String,
        val timestamp: String = LocalDateTime.now().toString()
    )

    data class RateLimitResponse(
        val status: Int = 429,
        val error: String = "rate_limit_exceeded",
        val message: String,
        val remaining: Int,
        val resetAt: String,
        val timestamp: String = LocalDateTime.now().toString()
    )

    data class ValidationErrorResponse(
        val status: Int = 400,
        val error: String = "validation_failed",
        val message: String = "Validation failed",
        val fields: Map<String, String>,
        val timestamp: String = LocalDateTime.now().toString()
    )

    @ExceptionHandler(AiRateLimitException::class)
    fun handleRateLimit(ex: AiRateLimitException, req: HttpServletRequest) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
            RateLimitResponse(
                message   = ex.message ?: "Daily AI limit reached",
                remaining = ex.remaining,
                resetAt   = ex.resetAt
            )
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, req: HttpServletRequest) =
        ResponseEntity.badRequest().body(
            ValidationErrorResponse(
                fields = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid") }
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, req: HttpServletRequest) =
        ResponseEntity.badRequest().body(
            ErrorResponse(400, "bad_request", ex.message ?: "Bad request", req.requestURI)
        )

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, req: HttpServletRequest) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(404, "not_found", ex.message ?: "Resource not found", req.requestURI)
        )

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException, req: HttpServletRequest) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(401, "unauthorized", ex.message ?: "Invalid credentials", req.requestURI)
        )

    @ExceptionHandler(UsernameNotFoundException::class)
    fun handleUsernameNotFound(ex: UsernameNotFoundException, req: HttpServletRequest) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(401, "unauthorized", ex.message ?: "User not found", req.requestURI)
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, req: HttpServletRequest) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(403, "forbidden", ex.message ?: "Access denied", req.requestURI)
        )

    @ExceptionHandler(InvalidPurchaseTokenException::class)
    fun handleInvalidPurchase(ex: InvalidPurchaseTokenException, req: HttpServletRequest) =
        ResponseEntity.badRequest().body(
            ErrorResponse(400, "invalid_purchase", ex.message ?: "Invalid purchase", req.requestURI)
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, req: HttpServletRequest): ResponseEntity<ErrorResponse> {
        // Don't expose internal errors to clients
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(500, "internal_error", "An unexpected error occurred", req.requestURI)
        )
    }
}
