package com.gtr3.ANote.auth.service

import com.gtr3.ANote.auth.dto.AuthResponse
import com.gtr3.ANote.auth.dto.GoogleSignInRequest
import com.gtr3.ANote.auth.dto.LoginRequest
import com.gtr3.ANote.auth.dto.ProfileResponse
import com.gtr3.ANote.auth.dto.RefreshRequest
import com.gtr3.ANote.auth.dto.RegisterRequest
import com.gtr3.ANote.auth.dto.UpdateProfileRequest
import com.gtr3.ANote.auth.entity.User
import com.gtr3.ANote.auth.repository.UserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val googleTokenVerifier: GoogleTokenVerifier
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found: $email") }
        // Google-only users have no password — return a placeholder that cannot match any input
        return org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.passwordHash ?: "{noop}GOOGLE_USER_NO_PASSWORD")
            .authorities(emptyList())
            .build()
    }

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already registered")
        }
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
        )
        userRepository.save(user)
        return AuthResponse(
            token = jwtService.generateToken(user.email),
            refreshToken = jwtService.generateRefreshToken(user.email),
            email = user.email
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { BadCredentialsException("Invalid credentials") }
        if (user.passwordHash == null) {
            throw BadCredentialsException("This account uses Google Sign-In. Please use 'Continue with Google'.")
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
        return AuthResponse(
            token        = jwtService.generateToken(user.email),
            refreshToken = jwtService.generateRefreshToken(user.email),
            email        = user.email
        )
    }

    fun loginWithGoogle(request: GoogleSignInRequest): AuthResponse {
        val payload     = googleTokenVerifier.verify(request.idToken)
        val email       = payload.email
        val displayName = payload["name"] as? String

        val user = userRepository.findByEmail(email).orElseGet {
            // Auto-register: first time Google sign-in
            userRepository.save(
                User(
                    email        = email,
                    passwordHash = null,
                    authProvider = "GOOGLE",
                    displayName  = displayName
                )
            )
        }

        return AuthResponse(
            token        = jwtService.generateToken(user.email),
            refreshToken = jwtService.generateRefreshToken(user.email),
            email        = user.email
        )
    }

    fun refresh(request: RefreshRequest): AuthResponse {
        if (!jwtService.isTokenValid(request.refreshToken)) {
            throw BadCredentialsException("Invalid or expired refresh token")
        }
        val email = jwtService.extractEmail(request.refreshToken)
        return AuthResponse(
            token = jwtService.generateToken(email),
            refreshToken = jwtService.generateRefreshToken(email),
            email = email
        )
    }

    fun getProfile(email: String): ProfileResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found") }
        return ProfileResponse(
            email       = user.email,
            displayName = user.displayName,
            dateOfBirth = user.dateOfBirth,
            sex         = user.sex
        )
    }

    fun updateProfile(email: String, request: UpdateProfileRequest): ProfileResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found") }
        val updated = user.copy(
            displayName = request.displayName,
            dateOfBirth = request.dateOfBirth,
            sex         = request.sex
        )
        userRepository.save(updated)
        return ProfileResponse(
            email       = updated.email,
            displayName = updated.displayName,
            dateOfBirth = updated.dateOfBirth,
            sex         = updated.sex
        )
    }
}
