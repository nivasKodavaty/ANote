package com.gtr3.ANote.auth.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service

@Service
class GoogleTokenVerifier(
    @Value("\${google.client-id}") private val clientId: String
) {
    private val verifier: GoogleIdTokenVerifier by lazy {
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(clientId))
            .build()
    }

    fun verify(idToken: String): GoogleIdToken.Payload {
        val token = runCatching { verifier.verify(idToken) }
            .getOrElse { throw BadCredentialsException("Invalid Google ID token: ${it.message}") }
            ?: throw BadCredentialsException("Google ID token verification failed")
        return token.payload
    }
}
