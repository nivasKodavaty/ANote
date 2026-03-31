# ANote1 — Backend Build Plan

**Package:** `com.gtr3.ANote`
**Spring Boot:** 4.1.0-SNAPSHOT | **Kotlin:** 2.3.20 | **Java:** 21
**Build Tool:** Gradle (Kotlin DSL)

> Work through each phase in order. Each phase has a clear "done when" checkpoint.
> Do not move to the next phase until the checkpoint passes.

---

## Phase 1 — Add Dependencies & Project Structure

### 1.1 Update `build.gradle.kts`

Add these dependencies to the existing file:

```kotlin
dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Reactive HTTP client (for Gemini)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

### 1.2 Create Package Structure

Create these empty packages inside `src/main/kotlin/com/gtr3/ANote/`:

```
com.gtr3.ANote
├── auth/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   └── security/
├── notes/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── ai/
│   └── service/
└── common/
    └── exception/
```

### 1.3 Replace `application.properties` with `application.yml`

Delete `src/main/resources/application.properties` and create these files:

**`application.yml`** (shared):
```yaml
spring:
  application:
    name: anote1
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=24h

gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-1.5-flash
  base-url: https://generativelanguage.googleapis.com

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: 86400000
  refresh-expiration-ms: 604800000

server:
  port: ${APP_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health
```

**`application-local.yml`** (local dev):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ainotes
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
  jpa:
    show-sql: true

logging:
  level:
    com.gtr3.ANote: DEBUG
    org.hibernate.SQL: DEBUG
```

**`application-prod.yml`** (Render + Neon):
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 3
      connection-timeout: 30000
  flyway:
    enabled: true
    locations: classpath:db/migration
  jpa:
    show-sql: false

logging:
  level:
    com.gtr3.ANote: INFO
```

### 1.4 Create `.env` at Project Root

```bash
SPRING_PROFILES_ACTIVE=local
DB_USERNAME=postgres
DB_PASSWORD=your_local_postgres_password
GEMINI_API_KEY=your_key_here
JWT_SECRET=local-dev-secret-at-least-32-characters-long
APP_PORT=8080
```

Add to `.gitignore`:
```
.env
build/
.gradle/
.idea/
*.iml
.DS_Store
```

### Phase 1 Checkpoint

```bash
# Export env and run
export $(cat .env | xargs) && ./gradlew bootRun --args='--spring.profiles.active=local'
```

**Done when:** App starts without errors and this returns `{"status":"UP"}`:
```
GET http://localhost:8080/actuator/health
```

---

## Phase 2 — Database Migrations (Flyway)

Create folder: `src/main/resources/db/migration/`

### `V1__create_users.sql`
```sql
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### `V2__create_notes.sql`
```sql
CREATE TABLE notes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(500) NOT NULL,
    content    TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notes_user_id ON notes(user_id);
```

### `V3__create_note_messages.sql`
```sql
CREATE TABLE note_messages (
    id         BIGSERIAL PRIMARY KEY,
    note_id    BIGINT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    message    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_note_messages_note_id ON note_messages(note_id);
```

### Phase 2 Checkpoint

```bash
export $(cat .env | xargs) && ./gradlew bootRun --args='--spring.profiles.active=local'
```

**Done when:** Flyway logs show:
```
Successfully applied 3 migrations to schema "public"
```
And in psql:
```sql
\c ainotes
\dt
-- should list: users, notes, note_messages, flyway_schema_history
```

---

## Phase 3 — Auth Module

### Files to create (in order):

#### `auth/entity/User.kt`
```kotlin
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

    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

#### `auth/repository/UserRepository.kt`
```kotlin
package com.gtr3.ANote.auth.repository

import com.gtr3.ANote.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
}
```

#### `auth/dto/AuthDtos.kt`
```kotlin
package com.gtr3.ANote.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min = 6) val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val email: String
)

data class RefreshRequest(
    val refreshToken: String
)
```

#### `auth/service/JwtService.kt`
```kotlin
package com.gtr3.ANote.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms}") private val expirationMs: Long,
    @Value("\${jwt.refresh-expiration-ms}") private val refreshExpirationMs: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(email: String): String = buildToken(email, expirationMs)

    fun generateRefreshToken(email: String): String = buildToken(email, refreshExpirationMs)

    private fun buildToken(email: String, expiry: Long): String =
        Jwts.builder()
            .subject(email)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiry))
            .signWith(key)
            .compact()

    fun extractEmail(token: String): String = extractClaims(token).subject

    fun isTokenValid(token: String): Boolean = runCatching {
        extractClaims(token).expiration.after(Date())
    }.getOrDefault(false)

    private fun extractClaims(token: String): Claims =
        Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload
}
```

#### `auth/security/JwtAuthFilter.kt`
```kotlin
package com.gtr3.ANote.auth.security

import com.gtr3.ANote.auth.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)

        if (jwtService.isTokenValid(token) &&
            SecurityContextHolder.getContext().authentication == null) {

            val email = jwtService.extractEmail(token)
            val userDetails = userDetailsService.loadUserByUsername(email)

            val authToken = UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.authorities
            )
            authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authToken
        }

        filterChain.doFilter(request, response)
    }
}
```

#### `auth/service/UserService.kt`
```kotlin
package com.gtr3.ANote.auth.service

import com.gtr3.ANote.auth.dto.*
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
```

#### `auth/security/SecurityConfig.kt`
```kotlin
package com.gtr3.ANote.auth.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

#### `auth/controller/AuthController.kt`
```kotlin
package com.gtr3.ANote.auth.controller

import com.gtr3.ANote.auth.dto.*
import com.gtr3.ANote.auth.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val userService: UserService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(userService.register(request))

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(userService.login(request))

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(userService.refresh(request))
}
```

### Phase 3 Checkpoint — Test with Postman/Bruno

```
# 1. Register
POST http://localhost:8080/api/auth/register
Content-Type: application/json
{ "email": "test@test.com", "password": "password123" }
→ 200: { "token": "eyJ...", "refreshToken": "eyJ...", "email": "test@test.com" }

# 2. Duplicate register
POST http://localhost:8080/api/auth/register
{ "email": "test@test.com", "password": "password123" }
→ 400: Email already registered

# 3. Login
POST http://localhost:8080/api/auth/login
{ "email": "test@test.com", "password": "password123" }
→ 200: { "token": "eyJ...", ... }

# 4. Wrong password
POST http://localhost:8080/api/auth/login
{ "email": "test@test.com", "password": "wrongpass" }
→ 401

# 5. Hit protected route with no token
GET http://localhost:8080/api/notes
→ 403

# 6. Hit protected route with valid token
GET http://localhost:8080/api/notes
Authorization: Bearer eyJ...
→ 200 (even if empty — proves token is accepted)
```

**Done when:** All 6 tests pass.

---

## Phase 4 — Notes Module (CRUD)

### Files to create:

#### `notes/entity/Note.kt`
```kotlin
package com.gtr3.ANote.notes.entity

import com.gtr3.ANote.auth.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notes")
data class Note(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

#### `notes/entity/NoteMessage.kt`
```kotlin
package com.gtr3.ANote.notes.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "note_messages")
data class NoteMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    val note: Note,

    @Column(nullable = false)
    val role: String,   // "user" or "assistant"

    @Column(columnDefinition = "TEXT", nullable = false)
    val message: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

#### `notes/repository/NoteRepository.kt`
```kotlin
package com.gtr3.ANote.notes.repository

import com.gtr3.ANote.notes.entity.Note
import org.springframework.data.jpa.repository.JpaRepository

interface NoteRepository : JpaRepository<Note, Long> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<Note>
    fun findByIdAndUserId(id: Long, userId: Long): Note?
}
```

#### `notes/repository/NoteMessageRepository.kt`
```kotlin
package com.gtr3.ANote.notes.repository

import com.gtr3.ANote.notes.entity.NoteMessage
import org.springframework.data.jpa.repository.JpaRepository

interface NoteMessageRepository : JpaRepository<NoteMessage, Long> {
    fun findAllByNoteIdOrderByCreatedAtAsc(noteId: Long): List<NoteMessage>
}
```

#### `notes/dto/NoteDtos.kt`
```kotlin
package com.gtr3.ANote.notes.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

data class CreateNoteRequest(
    @field:NotBlank val title: String
)

data class ChatRequest(
    @field:NotBlank val message: String
)

data class MessageResponse(
    val id: Long,
    val role: String,
    val message: String,
    val createdAt: LocalDateTime
)

data class NoteResponse(
    val id: Long,
    val title: String,
    val content: String?,
    val updatedAt: LocalDateTime,
    val messages: List<MessageResponse> = emptyList()
)
```

#### `notes/service/NoteService.kt`
```kotlin
package com.gtr3.ANote.notes.service

import com.gtr3.ANote.auth.repository.UserRepository
import com.gtr3.ANote.notes.dto.*
import com.gtr3.ANote.notes.entity.Note
import com.gtr3.ANote.notes.entity.NoteMessage
import com.gtr3.ANote.notes.repository.NoteMessageRepository
import com.gtr3.ANote.notes.repository.NoteRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val noteMessageRepository: NoteMessageRepository,
    private val userRepository: UserRepository
) {
    fun getAllNotes(email: String): List<NoteResponse> {
        val user = getUser(email)
        return noteRepository.findAllByUserIdOrderByUpdatedAtDesc(user.id)
            .map { it.toResponse() }
    }

    fun getNoteById(id: Long, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(id, user.id)
            ?: throw NoSuchElementException("Note not found")
        val messages = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
        return note.toResponse(messages)
    }

    @Transactional
    fun createNote(request: CreateNoteRequest, email: String): NoteResponse {
        val user = getUser(email)
        val note = noteRepository.save(
            Note(user = user, title = request.title)
        )
        // AI generation will be wired here in Phase 5
        return note.toResponse()
    }

    @Transactional
    fun deleteNote(id: Long, email: String) {
        val user = getUser(email)
        val note = noteRepository.findByIdAndUserId(id, user.id)
            ?: throw NoSuchElementException("Note not found")
        noteRepository.delete(note)
    }

    private fun getUser(email: String) =
        userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found") }

    private fun Note.toResponse(messages: List<NoteMessage> = emptyList()) = NoteResponse(
        id = id,
        title = title,
        content = content,
        updatedAt = updatedAt,
        messages = messages.map {
            MessageResponse(it.id, it.role, it.message, it.createdAt)
        }
    )
}
```

#### `notes/controller/NoteController.kt`
```kotlin
package com.gtr3.ANote.notes.controller

import com.gtr3.ANote.notes.dto.*
import com.gtr3.ANote.notes.service.NoteService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notes")
class NoteController(private val noteService: NoteService) {

    @GetMapping
    fun getAllNotes(@AuthenticationPrincipal user: UserDetails) =
        ResponseEntity.ok(noteService.getAllNotes(user.username))

    @PostMapping
    fun createNote(
        @Valid @RequestBody request: CreateNoteRequest,
        @AuthenticationPrincipal user: UserDetails
    ) = ResponseEntity.ok(noteService.createNote(request, user.username))

    @GetMapping("/{id}")
    fun getNoteById(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ) = ResponseEntity.ok(noteService.getNoteById(id, user.username))

    @DeleteMapping("/{id}")
    fun deleteNote(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<Void> {
        noteService.deleteNote(id, user.username)
        return ResponseEntity.noContent().build()
    }
}
```

### Phase 4 Checkpoint — Test with Postman/Bruno

```
# Use token from Phase 3 login for all requests below
# Header: Authorization: Bearer <token>

# 1. List notes (empty)
GET http://localhost:8080/api/notes
→ 200: []

# 2. Create note
POST http://localhost:8080/api/notes
{ "title": "Benefits of Morning Exercise" }
→ 200: { "id": 1, "title": "...", "content": null, "updatedAt": "...", "messages": [] }

# 3. Get note by ID
GET http://localhost:8080/api/notes/1
→ 200: note object with empty messages list

# 4. Try to access another user's note
# Register a second user, get their token, try GET /api/notes/1
→ 404: Note not found

# 5. Delete note
DELETE http://localhost:8080/api/notes/1
→ 204 No Content

# 6. Confirm deleted
GET http://localhost:8080/api/notes/1
→ 404
```

**Done when:** All 6 tests pass.

---

## Phase 5 — AI Module (Gemini Integration)

### Files to create:

#### `ai/service/GeminiService.kt`
```kotlin
package com.gtr3.ANote.ai.service

import com.gtr3.ANote.notes.entity.NoteMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GeminiService(
    @Value("\${gemini.api-key}") private val apiKey: String,
    @Value("\${gemini.model}") private val model: String,
    @Value("\${gemini.base-url}") private val baseUrl: String
) {
    private val client = WebClient.builder()
        .baseUrl(baseUrl)
        .build()

    fun generateNoteContent(title: String): String {
        val prompt = "You are a note-writing assistant. Generate a well-structured, informative note for the following title. Use clear headings and concise points.\n\nTitle: $title"
        return callGemini(listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to prompt)))))
    }

    fun chat(history: List<NoteMessage>, userMessage: String): String {
        val contents = history.map { msg ->
            mapOf("role" to msg.role, "parts" to listOf(mapOf("text" to msg.message)))
        } + listOf(
            mapOf("role" to "user", "parts" to listOf(mapOf("text" to userMessage)))
        )
        return callGemini(contents)
    }

    private fun callGemini(contents: List<Map<String, Any>>): String {
        val body = mapOf("contents" to contents)
        val response = client.post()
            .uri("/v1beta/models/$model:generateContent?key=$apiKey")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        @Suppress("UNCHECKED_CAST")
        val candidates = response?.get("candidates") as? List<Map<String, Any>>
        val content = candidates?.firstOrNull()?.get("content") as? Map<String, Any>
        val parts = content?.get("parts") as? List<Map<String, Any>>
        return parts?.firstOrNull()?.get("text") as? String
            ?: throw RuntimeException("Empty response from Gemini")
    }
}
```

### Wire AI into `NoteService.kt`

Update `NoteService` to inject `GeminiService` and update `createNote()` and add `chat()`:

```kotlin
// Add to constructor:
private val geminiService: GeminiService,

// Replace createNote() body:
@Transactional
fun createNote(request: CreateNoteRequest, email: String): NoteResponse {
    val user = getUser(email)
    val note = noteRepository.save(Note(user = user, title = request.title))

    val generatedContent = geminiService.generateNoteContent(request.title)

    note.content = generatedContent
    note.updatedAt = LocalDateTime.now()
    noteRepository.save(note)

    val assistantMsg = noteMessageRepository.save(
        NoteMessage(note = note, role = "assistant", message = generatedContent)
    )
    return note.toResponse(listOf(assistantMsg))
}

// Add new method:
@Transactional
fun chat(noteId: Long, request: ChatRequest, email: String): NoteResponse {
    val user = getUser(email)
    val note = noteRepository.findByIdAndUserId(noteId, user.id)
        ?: throw NoSuchElementException("Note not found")

    val history = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)

    noteMessageRepository.save(NoteMessage(note = note, role = "user", message = request.message))

    val aiReply = geminiService.chat(history, request.message)

    val assistantMsg = noteMessageRepository.save(
        NoteMessage(note = note, role = "assistant", message = aiReply)
    )

    note.content = aiReply
    note.updatedAt = LocalDateTime.now()
    noteRepository.save(note)

    val allMessages = noteMessageRepository.findAllByNoteIdOrderByCreatedAtAsc(note.id)
    return note.toResponse(allMessages)
}
```

### Wire chat endpoint into `NoteController.kt`

```kotlin
// Add to NoteController:
@PostMapping("/{id}/chat")
fun chat(
    @PathVariable id: Long,
    @Valid @RequestBody request: ChatRequest,
    @AuthenticationPrincipal user: UserDetails
) = ResponseEntity.ok(noteService.chat(id, request, user.username))
```

### Phase 5 Checkpoint — Test with Postman/Bruno

```
# 1. Create note (should now have AI-generated content)
POST http://localhost:8080/api/notes
{ "title": "Benefits of Morning Exercise" }
→ 200: { "id": 1, "content": "<AI generated text>", "messages": [{"role":"assistant", ...}] }

# 2. Get note — confirm content and message history
GET http://localhost:8080/api/notes/1
→ messages array has the first assistant message

# 3. Follow-up chat
POST http://localhost:8080/api/notes/1/chat
{ "message": "Make it shorter and add a bullet list" }
→ 200: updated note with new content, messages array now has 2 entries

# 4. Another follow-up
POST http://localhost:8080/api/notes/1/chat
{ "message": "Add a conclusion paragraph" }
→ 200: messages array now has 3 entries, content is updated again

# 5. Verify full history is preserved
GET http://localhost:8080/api/notes/1
→ messages array shows all exchanges in order
```

**Done when:** All 5 tests pass with real AI-generated content.

---

## Phase 6 — Docker & Deployment Prep

### `Dockerfile` at project root
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src
RUN chmod +x gradlew && ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
```

### `.dockerignore`
```
.gradle
build
.idea
.env
*.iml
.DS_Store
```

### Test Docker locally
```bash
# Build image
docker build -t anote1 .

# Run with env vars
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/ainotes \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=your_password \
  -e GEMINI_API_KEY=your_key \
  -e JWT_SECRET=your_secret \
  anote1
```

### Phase 6 Checkpoint
```
GET http://localhost:8080/actuator/health
→ {"status":"UP"}
```

**Done when:** Full Postman flow (register → login → create note → chat) works against the Docker container.

---

## Render Deployment (After Phase 6)

1. Push code to GitHub (`main` branch)
2. Create new **Web Service** on Render, connect the GitHub repo
3. Set:
   - **Build command:** `./gradlew build -x test`
   - **Start command:** `java -Xmx384m -jar build/libs/anote1-0.0.1-SNAPSHOT.jar`
4. Add environment variables in Render dashboard:
   - `SPRING_PROFILES_ACTIVE` = `prod`
   - `DATABASE_URL` = Neon connection string
   - `DB_USERNAME` = Neon username
   - `DB_PASSWORD` = Neon password
   - `GEMINI_API_KEY` = your key
   - `JWT_SECRET` = new strong secret
   - `APP_PORT` = `8080`
5. First deploy — watch logs for Flyway running migrations on Neon
6. Set up UptimeRobot to ping `/actuator/health` every 14 minutes

---

## Progress Tracker

- [ ] Phase 1 — Dependencies & Config
- [ ] Phase 2 — Flyway Migrations
- [ ] Phase 3 — Auth Module (JWT)
- [ ] Phase 4 — Notes CRUD
- [ ] Phase 5 — AI Integration (Gemini)
- [ ] Phase 6 — Docker & Deploy
