package com.gtr3.ANote.auth

import com.gtr3.ANote.auth.dto.GoogleSignInRequest
import com.gtr3.ANote.auth.entity.User
import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.auth.service.GoogleTokenVerifier
import com.gtr3.ANote.auth.service.JwtService
import com.gtr3.ANote.auth.service.UserService
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class GoogleAuthServiceTest {

    private val userRepository     = mock<UserRepository>()
    private val jwtService         = mock<JwtService>()
    private val passwordEncoder    = mock<PasswordEncoder>()
    private val googleTokenVerifier = mock<GoogleTokenVerifier>()

    private val userService = UserService(
        userRepository, jwtService, passwordEncoder, googleTokenVerifier
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mockPayload(
        email: String = "user@gmail.com",
        name: String  = "Test User"
    ): GoogleIdToken.Payload {
        val payload = mock<GoogleIdToken.Payload>()
        whenever(payload.email).thenReturn(email)
        whenever(payload["name"]).thenReturn(name)
        return payload
    }

    private fun makeGoogleUser(email: String = "user@gmail.com") = User(
        id           = 1L,
        email        = email,
        passwordHash = null,
        authProvider = "GOOGLE",
        displayName  = "Test User"
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `loginWithGoogle - new user - auto-registers and returns tokens`() {
        val payload = mockPayload()
        whenever(googleTokenVerifier.verify(any())).thenReturn(payload)
        whenever(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty())
        whenever(userRepository.save(any<User>())).thenReturn(makeGoogleUser())
        whenever(jwtService.generateToken("user@gmail.com")).thenReturn("jwt-token")
        whenever(jwtService.generateRefreshToken("user@gmail.com")).thenReturn("refresh-token")

        val result = userService.loginWithGoogle(GoogleSignInRequest("valid-id-token"))

        assertEquals("jwt-token", result.token)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals("user@gmail.com", result.email)
        verify(userRepository).save(any<User>())
    }

    @Test
    fun `loginWithGoogle - existing user - issues tokens without creating new user`() {
        val payload    = mockPayload()
        val existingUser = makeGoogleUser()
        whenever(googleTokenVerifier.verify(any())).thenReturn(payload)
        whenever(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(existingUser))
        whenever(jwtService.generateToken("user@gmail.com")).thenReturn("jwt-token")
        whenever(jwtService.generateRefreshToken("user@gmail.com")).thenReturn("refresh-token")

        val result = userService.loginWithGoogle(GoogleSignInRequest("valid-id-token"))

        assertEquals("jwt-token", result.token)
        // No new user saved — existing account reused
        verify(userRepository, org.mockito.kotlin.never()).save(any<User>())
    }

    @Test
    fun `loginWithGoogle - merges with existing email+password account`() {
        val payload = mockPayload(email = "existing@email.com")
        val emailPasswordUser = User(
            id           = 2L,
            email        = "existing@email.com",
            passwordHash = "hashed-password",
            authProvider = "LOCAL"
        )
        whenever(googleTokenVerifier.verify(any())).thenReturn(payload)
        whenever(userRepository.findByEmail("existing@email.com"))
            .thenReturn(Optional.of(emailPasswordUser))
        whenever(jwtService.generateToken("existing@email.com")).thenReturn("merged-jwt")
        whenever(jwtService.generateRefreshToken("existing@email.com")).thenReturn("merged-refresh")

        val result = userService.loginWithGoogle(GoogleSignInRequest("google-token"))

        // Same account returned, no duplicate created
        assertEquals("merged-jwt", result.token)
        assertEquals("existing@email.com", result.email)
        verify(userRepository, org.mockito.kotlin.never()).save(any<User>())
    }

    @Test
    fun `loginWithGoogle - invalid token - throws BadCredentialsException`() {
        whenever(googleTokenVerifier.verify(any()))
            .thenThrow(BadCredentialsException("Invalid Google ID token"))

        assertThrows<BadCredentialsException> {
            userService.loginWithGoogle(GoogleSignInRequest("bad-token"))
        }
    }

    @Test
    fun `login with password - Google-only account - throws meaningful error`() {
        val googleUser = makeGoogleUser()
        whenever(userRepository.findByEmail("user@gmail.com"))
            .thenReturn(Optional.of(googleUser))

        val exception = assertThrows<BadCredentialsException> {
            userService.login(
                com.gtr3.ANote.auth.dto.LoginRequest("user@gmail.com", "any-password")
            )
        }
        assert(exception.message!!.contains("Google Sign-In"))
    }

    @Test
    fun `loadUserByUsername - Google user - returns placeholder password`() {
        val googleUser = makeGoogleUser()
        whenever(userRepository.findByEmail("user@gmail.com"))
            .thenReturn(Optional.of(googleUser))

        val userDetails = userService.loadUserByUsername("user@gmail.com")

        assertNotNull(userDetails.password)
        assert(userDetails.password.startsWith("{noop}"))
        assertEquals("user@gmail.com", userDetails.username)
    }
}
