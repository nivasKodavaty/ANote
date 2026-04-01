package com.gtr3.ANote.auth.service

import com.gtr3.ANote.auth.dto.AuthResponse
import com.gtr3.ANote.auth.dto.LoginRequest
import com.gtr3.ANote.auth.dto.RefreshRequest
import com.gtr3.ANote.auth.dto.RegisterRequest
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
    private val passwordEncoder: PasswordEncoder
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found: $email") }
        return org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.passwordHash)
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
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
        return AuthResponse(
            token = jwtService.generateToken(user.email),
            refreshToken = jwtService.generateRefreshToken(user.email),
            email = user.email
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
}
