# BekoLolek Plugin Factory — Implementation Plan for Claude Code

> **READ THIS FIRST**: This document is the step-by-step build plan for the entire platform.
> Complete ONE phase at a time. Each phase takes 2–3 hours of REAL implementation.
>
> **RULES**:
> 1. Do NOT create stub methods that just return null or empty objects. Every method must have real logic.
> 2. Do NOT write `// TODO` or `// simulated` comments. If a task says implement something, implement it fully.
> 3. Run the VERIFY commands at the end of each phase. Do not move to the next phase until all pass.
> 4. Commit after each phase with a conventional commit message.
> 5. If you need to reference architecture details, read `BekoLolek_Plugin_Factory_Architecture.docx` in the project root.

## Project Root: Monorepo Structure

```
plugin-factory/
├── api/                     # Spring Boot 3.x backend (Java 17, Maven)
│   ├── src/main/java/com/bekololek/pluginfactory/
│   │   ├── auth/            # Discord OAuth2, JWT, Spring Security
│   │   ├── user/            # User profiles, API keys
│   │   ├── subscription/    # Tiers, Stripe billing
│   │   ├── build/           # Build sessions, iterations, artifacts, errors
│   │   ├── plan/            # Plan document generation, complexity, scope gating
│   │   ├── agent/           # Claude API client, chatbot, implementer, model router
│   │   ├── container/       # Docker container pool, lifecycle, security
│   │   ├── marketplace/     # Listings, reviews, purchases
│   │   ├── team/            # Teams, workspaces, collaboration
│   │   └── common/          # Shared: exceptions, config, events, DTOs, utilities
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/    # Flyway SQL migrations
│   │   └── prompts/         # AI system prompt templates
│   └── src/test/
├── web/                     # React 18 + TypeScript + Vite (deployed via Vercel)
├── agents/                  # Agent prompt templates and plugin project templates
│   ├── prompts/             # System prompts for chatbot, plan gen, implementer
│   └── templates/           # Maven project template for generated plugins
├── containers/              # Dockerfiles for build and test containers
├── infra/                   # Docker Compose, Nginx, scripts
│   ├── nginx/
│   └── scripts/
└── shared/                  # Shared constants (if needed)
```

---

## Phase 1: Project Scaffolding & Backend Foundation

**Time**: 2–3 hours
**Goal**: Initialize the monorepo, Spring Boot project with all required dependencies, Flyway migrations, base entities, and health endpoints. The application MUST start and connect to a database.

### Task 1.1: Root monorepo files

Create these files in the project root:

**`.editorconfig`**: Standard config — charset utf-8, indent_size 4 for Java, indent_size 2 for JS/TS/JSON/YML, insert_final_newline true, trim_trailing_whitespace true.

**`.gitignore`**: Cover Java (target/, *.class, *.jar), Node (node_modules/, dist/), IDE (.idea/, .vscode/, *.iml), env files (.env, .env.local), Docker data volumes, OS files (.DS_Store, Thumbs.db).

**`README.md`**: Project name, one-line description, links to architecture doc.

### Task 1.2: Spring Boot project in `/api`

Use Spring Boot 3.3.x with Java 17. The `pom.xml` must include ALL of these dependencies (not just some — this was missed in the previous attempt):

**Core**:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-websocket`
- `spring-boot-starter-oauth2-client`
- `spring-boot-starter-actuator`

**Database**:
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql` (runtime)
- `h2` (runtime, dev only)

**Auth**:
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (io.jsonwebtoken 0.12.x)

**Payments**:
- `stripe-java` (28.x)

**Docker**:
- `com.github.docker-java:docker-java-core` (3.3.x)
- `com.github.docker-java:docker-java-transport-httpclient5` (3.3.x)

**Resilience**:
- `io.github.resilience4j:resilience4j-spring-boot3` (2.2.x)
- `io.github.resilience4j:resilience4j-circuitbreaker`
- `io.github.resilience4j:resilience4j-retry`

**Mapping**:
- `org.mapstruct:mapstruct` (1.5.x)
- `org.mapstruct:mapstruct-processor` (annotation processor)

**Caching**:
- `com.github.ben-manes.caffeine:caffeine` (3.1.x)
- `spring-boot-starter-data-redis`

**Object Storage**:
- `io.minio:minio` (8.5.x)

**Utilities**:
- `lombok` (annotation processor, configured alongside MapStruct)
- `jackson-datatype-jsr310` (for Java time serialization)

**Testing**:
- `spring-boot-starter-test`
- `spring-boot-testcontainers`
- `spring-security-test`
- `testcontainers:junit-jupiter`
- `testcontainers:postgresql`
- `org.wiremock:wiremock-standalone` (3.x)
- `com.tngtech.archunit:archunit-junit5` (1.2.x)

**Maven compiler plugin** must be configured with annotation processor paths for BOTH Lombok AND MapStruct (order matters: Lombok first, then MapStruct, then `lombok-mapstruct-binding`).

### Task 1.3: Application configuration

**`src/main/resources/application.yml`**:

```yaml
server:
  port: 8080

spring:
  profiles:
    active: dev
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles schema
    open-in-view: false
  jackson:
    serialization:
      write-dates-as-timestamps: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized

---
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:h2:mem:pluginfactory;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
  flyway:
    locations: classpath:db/migration
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect

jwt:
  secret: dev-secret-key-at-least-256-bits-long-for-hs256-algorithm-replace-in-prod
  access-expiry: 900000   # 15 minutes
  refresh-expiry: 604800000 # 7 days

anthropic:
  api-key: ${ANTHROPIC_API_KEY:}
  
stripe:
  api-key: ${STRIPE_API_KEY:}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:}

cors:
  allowed-origins: http://localhost:5173

---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DATABASE_URL}
    driver-class-name: org.postgresql.Driver
  flyway:
    locations: classpath:db/migration
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

### Task 1.4: Package structure

Create these packages under `com.bekololek.pluginfactory`:
`auth`, `user`, `subscription`, `build`, `plan`, `agent`, `container`, `marketplace`, `team`, `common`

Under `common`, create sub-packages: `config`, `exception`, `event`, `util`

Each package gets a `package-info.java` with a brief description.

### Task 1.5: Flyway migration V1__init.sql

Create `src/main/resources/db/migration/V1__init.sql`:

Tables to create (with ALL columns, constraints, and indexes):

**users**: id (UUID PK), email (VARCHAR 255 UNIQUE NOT NULL), display_name (VARCHAR 255 NOT NULL), auth_provider (VARCHAR 50 NOT NULL), discord_id (VARCHAR 100 UNIQUE), status (VARCHAR 20 NOT NULL DEFAULT 'ACTIVE'), last_active_at (TIMESTAMPTZ), created_at (TIMESTAMPTZ NOT NULL), updated_at (TIMESTAMPTZ NOT NULL). Indexes on email and discord_id.

**subscriptions**: id (UUID PK), user_id (UUID FK → users NOT NULL), tier (VARCHAR 20 NOT NULL DEFAULT 'FREE'), stripe_customer_id (VARCHAR 255), stripe_subscription_id (VARCHAR 255), current_period_start (TIMESTAMPTZ), current_period_end (TIMESTAMPTZ), builds_used_this_period (INT NOT NULL DEFAULT 0), status (VARCHAR 20 NOT NULL DEFAULT 'ACTIVE'), created_at (TIMESTAMPTZ NOT NULL), updated_at (TIMESTAMPTZ NOT NULL). Index on user_id. UNIQUE constraint on user_id (one active sub per user).

**tiers**: name (VARCHAR 20 PK), max_builds (INT NOT NULL), token_budget (INT NOT NULL), max_parallel (INT NOT NULL), max_iterations (INT NOT NULL), max_commands (INT NOT NULL), max_event_listeners (INT NOT NULL), jar_retention_days (INT NOT NULL), marketplace_slots (INT NOT NULL), source_code_access (BOOLEAN NOT NULL DEFAULT FALSE), price_monthly_cents (INT NOT NULL DEFAULT 0). Seed with:
- FREE: 1, 50000, 0, 0, 5, 3, 7, 0, false, 0
- BASIC: 5, 200000, 0, 2, 15, 10, 30, 1, false, 999
- PRO: 20, 500000, 5, 5, -1, -1, 90, 5, true, 2999
- TEAM: -1, 1000000, 20, -1, -1, -1, -1, -1, true, 7999

**api_keys**: id (UUID PK), user_id (UUID FK → users NOT NULL), key_hash (VARCHAR 255 NOT NULL), name (VARCHAR 100 NOT NULL), last_four (VARCHAR 4 NOT NULL), last_used_at (TIMESTAMPTZ), expires_at (TIMESTAMPTZ), revoked (BOOLEAN NOT NULL DEFAULT FALSE), created_at (TIMESTAMPTZ NOT NULL), updated_at (TIMESTAMPTZ NOT NULL). Indexes on user_id and key_hash.

> **NOTE for H2 compatibility**: Use `TIMESTAMP WITH TIME ZONE` which H2 supports. Do NOT use PostgreSQL-only syntax in migrations that also need to run on H2 in dev mode. If you must use PG-specific features, use Flyway's `db/migration/postgresql/` and `db/migration/h2/` separation.

### Task 1.6: Base entities

**`common/BaseEntity.java`**: `@MappedSuperclass` with `@Id` UUID (generated with `UUID.randomUUID()` in `@PrePersist`), `@Column createdAt` (TIMESTAMPTZ, not nullable, set in @PrePersist), `@Column updatedAt` (TIMESTAMPTZ, not nullable, set in @PrePersist and @PreUpdate). Use `@EntityListeners(AuditingEntityListener.class)` or manual `@PrePersist`/`@PreUpdate` callbacks.

**`user/User.java`**: Extends BaseEntity. Fields: email, displayName, authProvider (String), discordId, status (enum: ACTIVE, SUSPENDED, BANNED), lastActiveAt. Use `@Enumerated(EnumType.STRING)` for status. `@Table(name = "users")`.

**`user/UserRepository.java`**: extends `JpaRepository<User, UUID>`. Methods: `Optional<User> findByEmail(String email)`, `Optional<User> findByDiscordId(String discordId)`.

**`subscription/Subscription.java`**: Extends BaseEntity. Fields: userId (UUID), tier (String), stripeCustomerId, stripeSubscriptionId, currentPeriodStart, currentPeriodEnd, buildsUsedThisPeriod (int), status (enum: ACTIVE, PAST_DUE, CANCELLED). `@Table(name = "subscriptions")`.

**`subscription/SubscriptionRepository.java`**: Methods: `Optional<Subscription> findByUserId(UUID userId)`, `List<Subscription> findByStatus(String status)`.

**`subscription/Tier.java`**: An enum OR a POJO loaded from the tiers table. If enum, hard-code the limits as constants. If POJO, create TierRepository. **Recommended**: Use an enum with constants for simplicity since tier values rarely change at runtime:

```java
public enum Tier {
    FREE(1, 50_000, 0, 0, 5, 3, 7, 0, false),
    BASIC(5, 200_000, 0, 2, 15, 10, 30, 1, false),
    PRO(20, 500_000, 5, 5, -1, -1, 90, 5, true),
    TEAM(-1, 1_000_000, 20, -1, -1, -1, -1, -1, true);

    // Constructor with all fields, getters
    // -1 means unlimited
    public boolean isUnlimited(int value) { return value == -1; }
}
```

### Task 1.7: Exception handling

**`common/exception/NotFoundException.java`**: extends RuntimeException, constructor takes String message.
**`common/exception/ForbiddenException.java`**: extends RuntimeException.
**`common/exception/ValidationException.java`**: extends RuntimeException.
**`common/exception/BudgetExhaustedException.java`**: extends RuntimeException.

**`common/exception/ErrorResponse.java`**: Record with fields: `Instant timestamp`, `int status`, `String error`, `String message`, `String path`.

**`common/exception/GlobalExceptionHandler.java`**: `@RestControllerAdvice`. Handle each exception type returning the appropriate HTTP status and ErrorResponse body. Also handle `MethodArgumentNotValidException` (400) and generic `Exception` (500 with logged stack trace). Use `HttpServletRequest` to get the request path.

### Task 1.8: Health and config controllers

**`common/config/SecurityConfig.java`**: Create a basic `@Configuration` class with `@Bean SecurityFilterChain`. For now, permit all endpoints (we'll add JWT filter in Phase 2). Disable CSRF (stateless API). Set session management to STATELESS. Configure CORS with the allowed origins from config.

**`common/config/CorsConfig.java`**: `@Configuration` implementing `WebMvcConfigurer`. Override `addCorsMappings` to allow origins from `cors.allowed-origins` config property, allow all methods and headers, allow credentials.

**`common/HealthController.java`**: `@RestController`. Two endpoints:
- `GET /health` → returns `{status: "UP", timestamp: Instant.now()}`
- `GET /health/ready` → injects `DataSource`, attempts `connection.isValid(2)`, returns 200 if OK, 503 if not. Wrap in try-catch.

### Task 1.9: Tests

**`common/HealthControllerTest.java`**: `@WebMvcTest(HealthController.class)`. Test both endpoints return 200.

**`user/UserRepositoryTest.java`**: `@DataJpaTest`. Save a User, find by email, find by discordId, verify fields persisted correctly.

**Flyway migration test**: `@SpringBootTest` that simply starts the context — if Flyway migrations are broken, this will fail.

### VERIFY Phase 1

```bash
cd api
mvn clean compile        # Must compile with zero errors
mvn test                 # All tests must pass
mvn spring-boot:run      # Must start on port 8080 (Ctrl+C to stop)
curl http://localhost:8080/health  # Must return {"status":"UP",...}
```

**Commit**: `feat: project scaffolding with Spring Boot, Flyway migrations, base entities`

---

## Phase 2: Authentication (Discord OAuth2 + JWT)

**Time**: 2–3 hours
**Goal**: Working Discord OAuth2 flow, JWT generation/validation, Spring Security filter chain, refresh token management.

### Task 2.1: JWT Service

**`auth/JwtService.java`**: `@Service`. Inject `jwt.secret`, `jwt.access-expiry`, `jwt.refresh-expiry` from config.

Methods (ALL must have real implementations):

```java
public String generateAccessToken(UUID userId)
// Build JWT with: subject = userId.toString(), issuedAt = now, 
// expiration = now + accessExpiry, sign with HS256 using Keys.hmacShaKeyFor(secret.getBytes())

public String generateRefreshToken(UUID userId)
// Same but with refreshExpiry

public Claims validateToken(String token)
// Parse with Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
// Throws JwtException on invalid/expired

public UUID extractUserId(String token)
// Calls validateToken, extracts subject, returns UUID.fromString()

public boolean isTokenValid(String token)
// Returns true if validateToken doesn't throw
```

Write unit tests: `auth/JwtServiceTest.java` — test generate → validate round-trip, test expired token (use a 1ms expiry), test tampered token (modify a character), test extractUserId.

### Task 2.2: JWT Authentication Filter

**`auth/JwtAuthenticationFilter.java`**: extends `OncePerRequestFilter`.

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                 FilterChain filterChain) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
        String token = header.substring(7);
        try {
            UUID userId = jwtService.extractUserId(token);
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            // Invalid token — don't set auth, let security chain reject
            log.debug("Invalid JWT: {}", e.getMessage());
        }
    }
    filterChain.doFilter(request, response);
}

@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.startsWith("/health") || path.startsWith("/api/v1/auth/") 
           || path.equals("/api/v1/subscriptions/tiers");
}
```

### Task 2.3: Update Security Config

Update `SecurityConfig` to:
1. Add the `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
2. Permit: `/health/**`, `/api/v1/auth/**`, `/api/v1/subscriptions/tiers`, `/api/v1/marketplace/plugins` (GET only), `/api/v1/webhooks/**`, `/actuator/**`
3. All other requests: authenticated
4. Exception handling: return 401 JSON response for unauthenticated, 403 for forbidden (not HTML redirect)

### Task 2.4: Discord OAuth Service

**`auth/DiscordOAuthService.java`**: `@Service`. Inject config: `discord.client-id`, `discord.client-secret`, `discord.redirect-uri`.

Methods:

```java
public String buildAuthorizationUrl()
// Returns: https://discord.com/api/oauth2/authorize?client_id=X&redirect_uri=X
//          &response_type=code&scope=identify+email

public DiscordTokenResponse exchangeCode(String code)
// POST to https://discord.com/api/oauth2/token
// Form body: client_id, client_secret, grant_type=authorization_code, code, redirect_uri
// Parse response JSON: access_token, token_type, expires_in, refresh_token, scope
// Use RestTemplate with FormHttpMessageConverter

public DiscordUserInfo fetchDiscordUser(String accessToken)
// GET https://discord.com/api/users/@me
// Authorization: Bearer {accessToken}
// Parse: id, username, email, avatar
```

Create DTOs: `auth/dto/DiscordTokenResponse.java`, `auth/dto/DiscordUserInfo.java`, `auth/dto/AuthResponse.java` (accessToken, refreshToken, user info).

**DO NOT** just return mock data. These must make real HTTP calls. For testing, use WireMock to mock Discord's API.

### Task 2.5: Refresh Token Entity

**Flyway migration `V2__refresh_tokens.sql`**:
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
```

**`auth/RefreshToken.java`**: Entity. Fields: id, userId, tokenHash, expiresAt, revoked, createdAt.
**`auth/RefreshTokenRepository.java`**: Methods: `Optional<RefreshToken> findByTokenHash(String hash)`, `void deleteByUserId(UUID userId)`.

### Task 2.6: Auth Service

**`auth/AuthService.java`**: `@Service`. Orchestrates the auth flow.

```java
public AuthResponse handleDiscordCallback(String code)
// 1. exchangeCode(code) → Discord tokens
// 2. fetchDiscordUser(accessToken) → user info
// 3. Find or create User (by discordId)
// 4. If new user, create FREE Subscription
// 5. Update lastActiveAt
// 6. Generate JWT access + refresh tokens
// 7. Store refresh token hash in DB
// 8. Return AuthResponse with tokens + user DTO

public AuthResponse refreshAccessToken(String refreshToken)
// 1. Hash the token
// 2. Look up in DB, verify not revoked and not expired
// 3. Generate new access token (keep same refresh token)
// 4. Return AuthResponse

public void logout(UUID userId)
// Revoke all refresh tokens for user (set revoked=true)
```

**DO NOT** skip the find-or-create logic. The user must be created in the DB with a Subscription on first login.

### Task 2.7: Auth Controller

**`auth/AuthController.java`**: `@RestController @RequestMapping("/api/v1/auth")`

```java
@GetMapping("/discord")
// Returns redirect URL (or JSON with url field — frontend redirects)

@GetMapping("/discord/callback")
// Receives ?code=X, calls authService.handleDiscordCallback(code)
// Returns AuthResponse JSON

@PostMapping("/refresh")
// Body: { refreshToken: "..." }
// Returns new AuthResponse

@PostMapping("/logout")
// Extract userId from SecurityContext
// Call authService.logout(userId)
// Return 200
```

### Task 2.8: Authenticated User Utility

**`common/util/AuthenticatedUser.java`**:
```java
public static UUID getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UUID)) {
        throw new ForbiddenException("Not authenticated");
    }
    return (UUID) auth.getPrincipal();
}
```

### Task 2.9: Tests

**`auth/JwtServiceTest.java`**: (from Task 2.1)
**`auth/AuthServiceTest.java`**: Unit test with mocked DiscordOAuthService, UserRepository, SubscriptionRepository, RefreshTokenRepository. Test: new user creation, existing user login, refresh flow, logout.
**`auth/AuthControllerTest.java`**: `@WebMvcTest`. Mock AuthService. Test: discord endpoint, callback, refresh, logout. Verify protected endpoints return 401 without token, 200 with valid token.

### VERIFY Phase 2

```bash
cd api
mvn clean test                    # All tests pass
mvn spring-boot:run              
# In another terminal:
curl http://localhost:8080/api/v1/auth/discord  # Returns Discord URL
curl -H "Authorization: Bearer invalid" http://localhost:8080/api/v1/users/me  # Returns 401
```

**Commit**: `feat: Discord OAuth2 authentication with JWT tokens`

---

## Phase 3: User & Subscription Management

**Time**: 2–3 hours
**Goal**: User profile endpoints, Stripe subscription management, tier enforcement, usage tracking.

### Task 3.1: User DTOs and Mapper

**`user/dto/UserDto.java`**: Record: id, email, displayName, discordId, status, tier (from subscription), createdAt.
**`user/dto/UpdateProfileRequest.java`**: Record: displayName (validated: @NotBlank, @Size max 100).
**`user/dto/UsageStatsDto.java`**: Record: buildsUsed, buildsLimit, tokensConsumedThisPeriod, parallelBuildsActive, tier.

**`user/UserMapper.java`**: `@Mapper(componentModel = "spring")`. Method: `UserDto toDto(User user, Subscription subscription)`. This is a MapStruct mapper — it must generate real mapping code at compile time, not be a manual mapper class.

### Task 3.2: User Service

**`user/UserService.java`**: `@Service`.

```java
public UserDto getCurrentUser(UUID userId)
// Load user + subscription, map to DTO. Throw NotFoundException if missing.

public UserDto updateProfile(UUID userId, UpdateProfileRequest request)
// Load user, update display_name, save, return DTO

public UsageStatsDto getUsageStats(UUID userId)
// Load subscription, count active builds (from BuildSessionRepository — inject it),
// Return stats with tier limits for comparison
```

### Task 3.3: User Controller

**`user/UserController.java`**: `@RestController @RequestMapping("/api/v1/users")`

```java
@GetMapping("/me")
public UserDto getCurrentUser()

@PatchMapping("/me")  
public UserDto updateProfile(@Valid @RequestBody UpdateProfileRequest request)

@GetMapping("/me/usage")
public UsageStatsDto getUsageStats()

@GetMapping("/me/api-keys")
public List<ApiKeyDto> listApiKeys()

@PostMapping("/me/api-keys")
public ApiKeyCreatedDto createApiKey(@Valid @RequestBody CreateApiKeyRequest request)
// Returns the full key ONCE. Store hash in DB.

@DeleteMapping("/me/api-keys/{keyId}")
public void revokeApiKey(@PathVariable UUID keyId)
```

### Task 3.4: API Key Implementation

**`user/ApiKey.java`**: Entity. Fields: id, userId, keyHash, name, lastFour, lastUsedAt, expiresAt, revoked, createdAt, updatedAt.
**`user/ApiKeyRepository.java`**: `findByUserIdAndRevokedFalse(UUID userId)`.

**`user/ApiKeyService.java`**:
```java
public ApiKeyCreatedDto createApiKey(UUID userId, String name)
// 1. Generate random key: "bpf_" + SecureRandom 32-byte hex string
// 2. Hash with SHA-256
// 3. Store hash, name, last 4 chars
// 4. Return DTO with the full key (only time it's visible)

public List<ApiKeyDto> listKeys(UUID userId)
// Return masked keys (name + last four + created date)

public void revokeKey(UUID userId, UUID keyId)
// Verify ownership, set revoked=true
```

### Task 3.5: Subscription Service

**`subscription/SubscriptionService.java`**: `@Service`.

```java
public Subscription getCurrentSubscription(UUID userId)
public Tier getTierForUser(UUID userId)
public boolean canBuild(UUID userId)
// Check: builds_used < tier.maxBuilds (or unlimited if -1)

public void incrementBuildCount(UUID userId)
public void resetBuildCounts()
// @Scheduled(cron = "0 0 0 1 * *") — 1st of each month at midnight UTC
// Reset builds_used_this_period = 0 for all ACTIVE subscriptions
```

### Task 3.6: Stripe Integration

**`subscription/StripeService.java`**: `@Service`. Inject `stripe.api-key` and `stripe.webhook-secret`.

```java
@PostConstruct
void init() { Stripe.apiKey = this.apiKey; }

public String createCheckoutSession(UUID userId, Tier targetTier)
// 1. Load user + current subscription
// 2. If no Stripe customer, create one via Stripe API
// 3. Create Checkout Session with price lookup (store Stripe price IDs in config)
// 4. Return session.getUrl()

public String createCustomerPortalSession(UUID userId)
// 1. Load subscription, get stripeCustomerId
// 2. Create BillingPortal Session
// 3. Return session.getUrl()

public void handleWebhookEvent(String payload, String sigHeader)
// 1. Verify signature with webhook secret
// 2. Parse event type
// 3. Switch on event type:
//    - checkout.session.completed → upgrade subscription tier
//    - customer.subscription.updated → sync tier changes
//    - customer.subscription.deleted → downgrade to FREE
//    - invoice.payment_failed → mark PAST_DUE
```

**DO NOT** stub the Stripe calls. Use the real Stripe SDK. For testing, use WireMock or Stripe's test mode.

### Task 3.7: Subscription Controller

**`subscription/SubscriptionController.java`**: `@RestController @RequestMapping("/api/v1/subscriptions")`

```java
@GetMapping("/tiers")        // PUBLIC — returns all tiers with limits and pricing
@GetMapping("/current")      // Returns current subscription details
@PostMapping("/checkout")    // Body: { tier: "PRO" } → returns { url: "https://checkout.stripe.com/..." }
@PostMapping("/portal")      // Returns { url: "https://billing.stripe.com/..." }
```

**`subscription/StripeWebhookController.java`**: `@RestController`
```java
@PostMapping("/api/v1/webhooks/stripe")
// Reads raw body + Stripe-Signature header, delegates to StripeService
```

### Task 3.8: Tests

**`user/UserServiceTest.java`**: Mock repos. Test getCurrentUser, updateProfile, not-found case.
**`subscription/SubscriptionServiceTest.java`**: Test canBuild (under limit, at limit, unlimited tier), incrementBuildCount, tier resolution.
**`subscription/StripeWebhookControllerTest.java`**: Integration test with mock webhook payloads (checkout completed, subscription deleted).

### VERIFY Phase 3

```bash
cd api
mvn clean test
mvn spring-boot:run
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
curl http://localhost:8080/api/v1/subscriptions/tiers  # Public, returns 4 tiers
```

**Commit**: `feat: user profiles, subscription management, Stripe integration`

---

## Phase 4: Build Session & Chat Foundation

**Time**: 2–3 hours
**Goal**: Build session lifecycle, chat message storage, token budgets with threshold tracking, WebSocket real-time updates.

### Task 4.1: Flyway migration V3__build_sessions.sql

```sql
CREATE TABLE build_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    workspace_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'CHATTING',
    current_phase VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    complexity_score INT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_build_sessions_user ON build_sessions(user_id);
CREATE INDEX idx_build_sessions_status ON build_sessions(status);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES build_sessions(id),
    role VARCHAR(20) NOT NULL,  -- 'user' or 'assistant'
    content TEXT NOT NULL,
    model_used VARCHAR(50),
    tokens_consumed INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_chat_messages_session ON chat_messages(session_id);

CREATE TABLE token_budgets (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES build_sessions(id),
    allocated_tokens INT NOT NULL,
    consumed_tokens INT NOT NULL DEFAULT 0,
    planning_tokens INT NOT NULL DEFAULT 0,
    implementation_tokens INT NOT NULL DEFAULT 0,
    testing_tokens INT NOT NULL DEFAULT 0,
    threshold_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### Task 4.2: Entities

**`build/BuildSession.java`**: Entity with enums:
```java
public enum BuildStatus { CHATTING, PLANNING, APPROVED, BUILDING, TESTING, COMPLETED, FAILED, CANCELLED }
public enum BuildPhase { IDLE, CLARIFICATION, PLAN_GENERATION, PLAN_REVIEW, IMPLEMENTATION, COMPILATION, SECURITY_SCAN, INTEGRATION_TEST, DELIVERING }
```

**`build/ChatMessage.java`**: Entity. Fields: id, sessionId, role, content, modelUsed, tokensConsumed, createdAt.

**`build/TokenBudget.java`**: Entity with enum:
```java
public enum ThresholdStatus { NORMAL, WARNING, CRITICAL, EXHAUSTED }
```

Create repositories for each.

### Task 4.3: Token Budget Service

**`build/TokenBudgetService.java`**: This is a CRITICAL service. **Do NOT stub it.**

```java
public TokenBudget allocateBudget(UUID sessionId, Tier tier)
// Create TokenBudget with allocated_tokens = tier.getTokenBudget()

public TokenBudget consumeTokens(UUID sessionId, String phase, int amount)
// 1. Load budget
// 2. Add amount to consumed_tokens
// 3. Add to correct phase counter (planning_tokens, implementation_tokens, testing_tokens)
// 4. Calculate new threshold:
//    - consumed / allocated < 0.8 → NORMAL
//    - 0.8 ≤ ratio < 0.95 → WARNING  
//    - 0.95 ≤ ratio < 1.0 → CRITICAL
//    - ratio ≥ 1.0 → EXHAUSTED
// 5. Save and return

public TokenBudget getRemainingBudget(UUID sessionId)
// Load and return

public boolean hasBudget(UUID sessionId, int estimatedTokens)
// Returns true if consumed + estimated ≤ allocated
```

### Task 4.4: Build Session Service

**`build/BuildSessionService.java`**:

```java
public BuildSession createSession(UUID userId)
// 1. Check subscriptionService.canBuild(userId) — throw ForbiddenException if false
// 2. Get tier
// 3. Create BuildSession (status=CHATTING, phase=CLARIFICATION)
// 4. Save
// 5. Allocate token budget
// 6. Increment build count
// 7. Return session

public BuildSession getSession(UUID sessionId, UUID userId)
// Find by id AND userId. Throw NotFoundException if not found (prevents access to others' sessions).

public BuildSession getSessionById(UUID sessionId)
// Internal use only — no ownership check. For service-to-service calls.

public Page<BuildSession> listSessions(UUID userId, Pageable pageable)

public BuildSession cancelSession(UUID sessionId, UUID userId)
// Load (with ownership check), set status=CANCELLED, completedAt=now

public void updateStatus(UUID sessionId, BuildSession.BuildStatus status)
public void updatePhase(UUID sessionId, BuildSession.BuildPhase phase)
```

### Task 4.5: Chat Message Service

**`build/ChatMessageService.java`**:

```java
public ChatMessage addMessage(UUID sessionId, String role, String content, String modelUsed, int tokensConsumed)
// Create and save

public List<ChatMessage> getMessages(UUID sessionId)
// Return ordered by createdAt ASC
```

### Task 4.6: Build Session Controller

**`build/BuildSessionController.java`**: `@RestController @RequestMapping("/api/v1/builds")`

```java
@PostMapping               // Create session. Returns BuildSessionDto.
@GetMapping                // List sessions (paginated). Query params: page, size, status.
@GetMapping("/{id}")       // Get session detail. Validate ownership.
@DeleteMapping("/{id}")    // Cancel session. Validate ownership.
@GetMapping("/{id}/budget")    // Get token budget.
@GetMapping("/{id}/messages")  // Get chat history.
```

Create DTOs: `build/dto/BuildSessionDto.java`, `build/dto/TokenBudgetDto.java`, `build/dto/ChatMessageDto.java`.

### Task 4.7: WebSocket Configuration

**`common/config/WebSocketConfig.java`**: `@Configuration @EnableWebSocketMessageBroker`

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
    config.setApplicationDestinationPrefixes("/app");
}

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
}
```

**`common/config/WebSocketAuthInterceptor.java`**: `ChannelInterceptor` that extracts JWT from STOMP CONNECT headers and authenticates the user.

### Task 4.8: Build Progress Service

**`build/BuildProgressService.java`**: `@Service`. Injects `SimpMessagingTemplate`.

```java
public void notifyPhaseChange(UUID sessionId, BuildSession.BuildPhase phase)
// Send to /topic/builds/{sessionId}/progress: { type: "PHASE_CHANGE", phase: "...", timestamp: "..." }

public void notifyBudgetUpdate(UUID sessionId, TokenBudget budget)
// Send budget snapshot

public void notifyError(UUID sessionId, String errorMessage)
// Send error notification

public void notifyStatusChange(UUID sessionId, BuildSession.BuildStatus status)
// Send status change
```

### Task 4.9: Tests

**`build/BuildSessionServiceTest.java`**: Test createSession (happy path + build limit exceeded), getSession (ownership check), cancelSession.
**`build/TokenBudgetServiceTest.java`**: Test allocate, consume with threshold transitions at exactly 80%, 95%, 100%. Test hasBudget.
**`build/ChatMessageServiceTest.java`**: Test add and retrieve ordered.

### VERIFY Phase 4

```bash
cd api
mvn clean test
mvn spring-boot:run
# Create a session (need auth token):
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/builds
# List sessions:
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/builds
```

**Commit**: `feat: build sessions, chat messages, token budgets, WebSocket progress`

---

## Phase 5: AI Agent Integration (Chatbot + Model Router)

**Time**: 2–3 hours
**Goal**: Claude API client with circuit breaker, model routing (Haiku/Sonnet), prompt sanitization, chatbot agent with real conversation flow, SSE streaming endpoint.

### Task 5.1: Anthropic Client with Resilience4j

**`agent/AnthropicClient.java`**: `@Service`.

**DO NOT** use a bare RestTemplate without error handling. Wrap with Resilience4j.

```java
@CircuitBreaker(name = "anthropic", fallbackMethod = "fallback")
@Retry(name = "anthropic")
public AnthropicResponse sendMessage(String model, String systemPrompt,
                                      List<Map<String, String>> messages, int maxTokens) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-api-key", apiKey);
    headers.set("anthropic-version", "2023-06-01");

    Map<String, Object> body = Map.of(
        "model", model,
        "max_tokens", maxTokens,
        "system", systemPrompt,
        "messages", messages
    );

    ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
        ANTHROPIC_API_URL, new HttpEntity<>(body, headers), ApiResponse.class);
    
    // Extract text, usage, etc.
    // Return AnthropicResponse with content, model, inputTokens, outputTokens
}

private AnthropicResponse fallback(String model, String systemPrompt,
                                    List<Map<String, String>> messages, int maxTokens,
                                    Exception e) {
    log.error("Anthropic API unavailable, circuit breaker triggered", e);
    throw new RuntimeException("AI service temporarily unavailable. Please try again in a moment.");
}
```

Add Resilience4j config to `application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      anthropic:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      anthropic:
        maxAttempts: 3
        waitDuration: 2s
        retryExceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
```

Also configure the RestTemplate bean with explicit timeouts:
```java
@Bean
public RestTemplate anthropicRestTemplate() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10_000);  // 10s connect
    factory.setReadTimeout(120_000);    // 120s read (LLM responses can be slow)
    return new RestTemplate(factory);
}
```

### Task 5.2: Model Router

**`agent/ModelRouter.java`**: `@Service`.

```java
public enum TaskType {
    CLARIFICATION, INPUT_VALIDATION, ERROR_CLASSIFICATION, COMPLEXITY_ESTIMATION,
    PLAN_GENERATION, CODE_GENERATION, TEST_GENERATION, SECURITY_ANALYSIS
}

private static final String HAIKU = "claude-haiku-4-5-20250929";
private static final String SONNET = "claude-sonnet-4-5-20250514";

public String selectModel(TaskType taskType) {
    return switch (taskType) {
        case CLARIFICATION, INPUT_VALIDATION, ERROR_CLASSIFICATION -> HAIKU;
        case PLAN_GENERATION, CODE_GENERATION, TEST_GENERATION, 
             COMPLEXITY_ESTIMATION, SECURITY_ANALYSIS -> SONNET;
    };
}

public int getMaxTokens(TaskType taskType) {
    return switch (taskType) {
        case CLARIFICATION -> 2048;
        case INPUT_VALIDATION, ERROR_CLASSIFICATION -> 1024;
        case COMPLEXITY_ESTIMATION -> 1024;
        case PLAN_GENERATION -> 8192;
        case CODE_GENERATION -> 16384;
        case TEST_GENERATION -> 8192;
        case SECURITY_ANALYSIS -> 4096;
    };
}
```

### Task 5.3: Prompt Sanitizer

**`agent/PromptSanitizer.java`**: `@Service`.

```java
public SanitizationResult sanitize(String userMessage) {
    List<String> flags = new ArrayList<>();
    String clean = userMessage;

    // Check for injection patterns
    List<Pattern> injectionPatterns = List.of(
        Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
        Pattern.compile("(?i)you\\s+are\\s+now\\b"),
        Pattern.compile("(?i)system\\s*:\\s*"),
        Pattern.compile("(?i)\\bact\\s+as\\b"),
        Pattern.compile("(?i)forget\\s+(everything|all|your)"),
        Pattern.compile("(?i)new\\s+instructions?\\s*:"),
        Pattern.compile("(?i)override\\s+(your|the)\\s+(rules|instructions)")
    );
    
    for (Pattern p : injectionPatterns) {
        if (p.matcher(clean).find()) {
            flags.add("PROMPT_INJECTION: " + p.pattern());
        }
    }

    // Check for base64 payloads (long strings of base64 chars)
    if (Pattern.compile("[A-Za-z0-9+/=]{100,}").matcher(clean).find()) {
        flags.add("BASE64_PAYLOAD");
    }
    
    // Check for excessive special characters (>30% non-alphanumeric)
    long specialCount = clean.chars().filter(c -> !Character.isLetterOrDigit(c) && c != ' ').count();
    if (clean.length() > 20 && (double) specialCount / clean.length() > 0.3) {
        flags.add("EXCESSIVE_SPECIAL_CHARS");
    }

    return new SanitizationResult(clean, flags);
}

public record SanitizationResult(String cleanMessage, List<String> flags) {
    public boolean hasSuspiciousContent() { return !flags.isEmpty(); }
}
```

### Task 5.4: System Prompt Templates

Create REAL prompt files (not empty directories):

**`src/main/resources/prompts/chatbot_system.txt`**:
```
You are BekoLolek Plugin Factory's AI assistant. You help Minecraft server administrators create custom plugins by understanding their requirements.

Current session: Phase={{phase}}, Status={{status}}, Remaining tokens={{remaining_tokens}}

## Your Role
- Help the user describe their plugin idea clearly and completely
- Ask targeted clarifying questions about: commands (names, permissions, arguments), events (which Bukkit/Paper events to listen to), configuration options (what should be configurable in config.yml), target Minecraft version and server type (Paper/Spigot)
- Suggest improvements based on Minecraft plugin best practices
- Keep responses concise to conserve token budget

## Rules
- Only discuss Minecraft plugin development
- Do not write code in this phase — that happens after plan approval
- When the user seems satisfied with the description, suggest moving to plan generation by saying: "Your plugin idea is clear. Would you like me to generate a detailed plan document?"
- If the user confirms, respond with exactly: [TRANSITION:PLAN_GENERATION]

## Minecraft Plugin Knowledge
- Use Paper API (extends Bukkit/Spigot)
- Main class extends JavaPlugin
- Commands registered in plugin.yml and onCommand()
- Events use @EventHandler annotation with Listener interface
- Config via getConfig() with config.yml defaults
- Permissions defined in plugin.yml
- Player data: use UUID, never player names
- Async tasks: BukkitScheduler, never raw threads
```

**`src/main/resources/prompts/plan_generation_system.txt`**:
```
You are a Minecraft plugin architect. Generate a structured plan document from the conversation history.

Respond with ONLY a JSON object (no markdown, no explanation) in this exact format:
{
  "pluginName": "string",
  "description": "string (1-2 sentences)",
  "minecraftVersion": "1.20.4",
  "serverType": "PAPER",
  "commands": [
    {
      "name": "string",
      "description": "string",
      "permission": "pluginname.command.name",
      "usage": "/<command> [args]",
      "arguments": [
        { "name": "string", "type": "STRING|INT|PLAYER|BOOLEAN", "required": true }
      ]
    }
  ],
  "eventListeners": [
    {
      "event": "org.bukkit.event.EventClass",
      "priority": "NORMAL",
      "description": "What this listener does",
      "conditions": ["When X happens"]
    }
  ],
  "configSchema": [
    {
      "key": "path.to.key",
      "type": "STRING|INT|DOUBLE|BOOLEAN|LIST",
      "defaultValue": "value",
      "description": "What this configures"
    }
  ],
  "dependencies": [],
  "testScenarios": [
    { "name": "Test name", "description": "What to verify", "type": "UNIT|INTEGRATION" }
  ],
  "estimatedLinesOfCode": 150
}
```

### Task 5.5: Chatbot Agent

**`agent/ChatbotAgent.java`**: `@Service`. This is the core conversation agent.

The implementation must:
1. Sanitize input with PromptSanitizer
2. Load full chat history from ChatMessageService
3. Check token budget — return budget exhausted response if empty
4. Determine task type based on session status
5. Select model via ModelRouter
6. Load appropriate system prompt template and fill placeholders
7. Build the messages array from chat history
8. Call AnthropicClient with the selected model
9. Store BOTH user message and assistant response in ChatMessageService
10. Update token consumption via TokenBudgetService
11. Detect phase transition signals in the response (e.g., `[TRANSITION:PLAN_GENERATION]`)
12. Return AgentResponse

**DO NOT** skip any of these steps. Each one must be implemented.

### Task 5.6: Chat Controller with SSE Streaming

**`agent/ChatController.java`**: `@RestController @RequestMapping("/api/v1/builds")`

```java
@PostMapping("/{sessionId}/messages")
public AgentResponse sendMessage(@PathVariable UUID sessionId, @RequestBody SendMessageRequest request)
// 1. Validate session ownership
// 2. Validate session is in CHATTING or PLANNING status
// 3. Call chatbotAgent.handleMessage()
// 4. Send WebSocket notification
// 5. Return response

@GetMapping("/{sessionId}/messages/stream")
public SseEmitter streamMessage(@PathVariable UUID sessionId, @RequestParam String message)
// SSE endpoint for streaming responses
// 1. Validate session
// 2. Create SseEmitter with 120s timeout
// 3. In async thread: call Anthropic streaming API
// 4. For each token chunk, send SSE event
// 5. On complete, send final event with metadata
```

For the streaming variant, the AnthropicClient needs a streaming method:
```java
public void sendMessageStreaming(String model, String systemPrompt,
                                  List<Map<String, String>> messages, int maxTokens,
                                  Consumer<String> onToken, Consumer<Integer> onComplete)
// Use RestTemplate with streaming response extraction
// Or use OkHttp with EventSource for SSE from Anthropic
// Send "stream": true in the request body
// Parse SSE events: content_block_delta → extract text → call onToken
// On message_stop → call onComplete with total tokens
```

### Task 5.7: Tests

**`agent/ModelRouterTest.java`**: Verify each task type maps to correct model.
**`agent/PromptSanitizerTest.java`**: Test each injection pattern is caught. Test clean messages pass through.
**`agent/ChatbotAgentTest.java`**: Mock AnthropicClient. Test: normal message flow, budget exhausted flow, phase transition detection, token consumption tracking.
**`agent/AnthropicClientTest.java`**: Use WireMock to mock Anthropic API. Test: successful response, 429 rate limit (retry), 500 error (circuit breaker), timeout.

### VERIFY Phase 5

```bash
cd api
mvn clean test
mvn spring-boot:run
# Send a chat message (need a valid session):
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content":"I want a plugin that lets players set home locations"}' \
  http://localhost:8080/api/v1/builds/{sessionId}/messages
# Should return real AI response with model and token count
```

**Commit**: `feat: AI chatbot with Claude API, model routing, streaming responses`

---

## Phase 6: Plan Document Generation & Scope Gating

**Time**: 2–3 hours
**Goal**: Generate structured plan documents from conversations, estimate complexity scores, enforce tier-based scope limits, plan approval/revision flow.

### Task 6.1: Flyway migration V4__plan_documents.sql

```sql
CREATE TABLE plan_documents (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES build_sessions(id),
    plugin_name VARCHAR(255),
    description TEXT,
    minecraft_version VARCHAR(20),
    server_type VARCHAR(20),
    commands JSONB NOT NULL DEFAULT '[]',
    event_listeners JSONB NOT NULL DEFAULT '[]',
    config_schema JSONB NOT NULL DEFAULT '[]',
    dependencies JSONB NOT NULL DEFAULT '[]',
    test_scenarios JSONB NOT NULL DEFAULT '[]',
    estimated_loc INT,
    complexity_score INT,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

> For H2 compatibility: H2 supports JSON type from 2.x. Use `TEXT` instead of `JSONB` for H2, or use separate migration files. Jackson will handle serialization either way.

### Task 6.2: Plan Document Entity and DTOs

**`plan/PlanDocument.java`**: Entity. Store commands/events/config/dependencies/testScenarios as `@Column(columnDefinition = "jsonb")` with `@Convert(converter = JpaJsonConverter.class)` or use `@Type` with a custom Hibernate type.

**Recommended approach**: Use `String` column type and let Jackson handle serialization in the service layer. Simpler and H2-compatible.

Create these DTOs in `plan/dto/`:
- `PlanDocumentDto`
- `CommandSpec` (name, description, permission, usage, arguments list)
- `EventListenerSpec` (event class, priority, description, conditions)
- `ConfigEntry` (key, type, defaultValue, description)
- `DependencySpec` (groupId, artifactId, version, reason)
- `TestScenario` (name, description, type)

### Task 6.3: Plan Generation Agent

**`agent/PlanGenerationAgent.java`**: `@Service`.

```java
public PlanDocument generatePlan(UUID sessionId)
// 1. Load chat history
// 2. Load plan_generation_system.txt prompt
// 3. Route to Sonnet (PLAN_GENERATION task type)
// 4. Call AnthropicClient with chat history + system prompt
// 5. Parse the JSON response into PlanDocument fields
//    - Use Jackson ObjectMapper to parse the JSON
//    - Handle malformed JSON gracefully (log and throw with user-friendly message)
// 6. Calculate complexity score
// 7. Save PlanDocument to database
// 8. Update session status to PLANNING, phase to PLAN_REVIEW
// 9. Return saved PlanDocument
```

**DO NOT** skip the JSON parsing. The model returns a JSON string that must be parsed into the structured fields. Handle edge cases: model returns markdown-wrapped JSON (strip ```json...```), model returns extra text before/after JSON (extract with regex).

### Task 6.4: Complexity Estimator

**`plan/ComplexityEstimator.java`**: `@Service`.

```java
public ComplexityResult estimateComplexity(PlanDocument plan) {
    int score = 0;
    Map<String, Integer> breakdown = new LinkedHashMap<>();
    
    int commandScore = plan.getCommandCount() * 10;
    breakdown.put("commands", commandScore);
    score += commandScore;
    
    int eventScore = plan.getEventListenerCount() * 15;
    breakdown.put("eventListeners", eventScore);
    score += eventScore;
    
    int configScore = plan.getConfigEntryCount() * 3;
    breakdown.put("configOptions", configScore);
    score += configScore;
    
    int depScore = plan.getDependencyCount() * 25;
    breakdown.put("dependencies", depScore);
    score += depScore;
    
    int locScore = (int)(plan.getEstimatedLoc() * 0.1);
    breakdown.put("estimatedLOC", locScore);
    score += locScore;
    
    return new ComplexityResult(score, breakdown);
}

public record ComplexityResult(int totalScore, Map<String, Integer> breakdown) {}
```

### Task 6.5: Scope Gating Service

**`plan/ScopeGatingService.java`**: `@Service`.

```java
public ScopeValidationResult validateScope(PlanDocument plan, Tier tier) {
    List<String> violations = new ArrayList<>();
    
    if (!tier.isUnlimited(tier.getMaxCommands()) 
        && plan.getCommandCount() > tier.getMaxCommands()) {
        violations.add("Commands: %d exceeds tier limit of %d"
            .formatted(plan.getCommandCount(), tier.getMaxCommands()));
    }
    
    if (!tier.isUnlimited(tier.getMaxEventListeners()) 
        && plan.getEventListenerCount() > tier.getMaxEventListeners()) {
        violations.add("Event listeners: %d exceeds tier limit of %d"
            .formatted(plan.getEventListenerCount(), tier.getMaxEventListeners()));
    }
    
    // Add more dimension checks as needed
    
    ScopeStatus status = violations.isEmpty() ? ScopeStatus.PASS : ScopeStatus.EXCEEDS_TIER;
    return new ScopeValidationResult(status, violations);
}

public enum ScopeStatus { PASS, EXCEEDS_TIER, REQUIRES_SIMPLIFICATION }
public record ScopeValidationResult(ScopeStatus status, List<String> violations) {}
```

### Task 6.6: Plan Controller

**`plan/PlanController.java`**: `@RestController @RequestMapping("/api/v1/builds")`

```java
@GetMapping("/{sessionId}/plan")
// Return current plan document (404 if none generated yet)

@PostMapping("/{sessionId}/plan/approve")
// 1. Load plan + user's tier
// 2. Run scope gating
// 3. If PASS: update session status to APPROVED, return plan with approval confirmation
// 4. If EXCEEDS_TIER: return 422 with violations list and upgrade suggestion

@PostMapping("/{sessionId}/plan/revise")
// Body: { feedback: "Make it simpler, remove the X feature" }
// 1. Re-enter PLANNING phase
// 2. Add feedback as user message to chat history
// 3. Re-trigger plan generation with conversation context + feedback
// 4. Return revised plan
```

### Task 6.7: Wire plan generation into ChatbotAgent

Update `ChatbotAgent.handleMessage()`: after receiving the AI response, check if it contains `[TRANSITION:PLAN_GENERATION]`. If so:
1. Strip the transition marker from the displayed response
2. Update session status to PLANNING
3. Call `planGenerationAgent.generatePlan(sessionId)` asynchronously (or synchronously if acceptable)
4. Include plan summary in the AgentResponse

### Task 6.8: Tests

**`plan/ComplexityEstimatorTest.java`**: Test with simple (1 command, 0 events), medium (5 commands, 3 events), complex (15 commands, 10 events, 3 dependencies) plans.
**`plan/ScopeGatingServiceTest.java`**: Test FREE tier (1 command max → 2 commands fails), PRO tier (unlimited → passes), at-limit (exactly equals max → passes).
**`plan/PlanControllerTest.java`**: Integration test: approve flow, rejection flow with violations, revision flow.

### VERIFY Phase 6

```bash
cd api
mvn clean test
mvn spring-boot:run
# After chatting to define a plugin, the plan endpoint should work:
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/builds/{sessionId}/plan
# Approve:
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/builds/{sessionId}/plan/approve
```

**Commit**: `feat: plan generation, complexity scoring, scope gating`

---

## Phase 7: Docker Container Management

**Time**: 2–3 hours
**Goal**: Build container Dockerfiles, Docker client wrapper, container pool manager with warm pool, security constraints, and cleanup.

### Task 7.1: Dockerfiles

**`containers/Dockerfile.build`**:
```dockerfile
FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    maven gradle git curl && \
    rm -rf /var/lib/apt/lists/*

# Pre-download common dependencies
WORKDIR /tmp/warmup
COPY containers/warmup-pom.xml pom.xml
RUN mvn dependency:resolve -q && rm -rf /tmp/warmup

WORKDIR /plugin-workspace
VOLUME /plugin-workspace
```

Create `containers/warmup-pom.xml`: a minimal Maven POM that declares Paper API, JUnit 5, and Mockito as dependencies so they're pre-cached in the Docker image.

**`containers/Dockerfile.test`**:
```dockerfile
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Download Paper server JAR
ARG PAPER_VERSION=1.20.4
ARG PAPER_BUILD=496
RUN mkdir -p /server && \
    curl -o /server/paper.jar \
    "https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${PAPER_BUILD}/downloads/paper-${PAPER_VERSION}-${PAPER_BUILD}.jar"

WORKDIR /server
RUN echo "eula=true" > eula.txt
VOLUME /server/plugins
```

### Task 7.2: Docker Service

**`container/DockerService.java`**: `@Service`. Uses docker-java library.

```java
@PostConstruct
void init() {
    this.dockerClient = DockerClientBuilder.getInstance()
        .withDockerHttpClient(new ApacheDockerHttpClient.Builder()
            .dockerHost(URI.create(dockerHost))
            .build())
        .build();
}

public enum ContainerType { BUILD, TEST }

public String createContainer(ContainerType type, Map<String, String> env)
// Create container from appropriate image
// Apply security constraints from ContainerSecurityConfig
// Set environment variables
// Return container ID

public void startContainer(String containerId)
public void stopContainer(String containerId)
public void removeContainer(String containerId)

public ExecResult executeCommand(String containerId, String... command)
// Create exec, start exec, capture stdout+stderr, return ExecResult
// ExecResult: record(int exitCode, String stdout, String stderr)

public void copyToContainer(String containerId, byte[] tarContent, String destPath)
// Use docker-java's copyArchiveToContainerCmd

public byte[] copyFromContainer(String containerId, String sourcePath)
// Use docker-java's copyArchiveFromContainerCmd
// Return the file bytes
```

**DO NOT** make these no-op methods. They must call the real Docker daemon via docker-java. For environments without Docker, they will fail — that's expected. Tests will use Testcontainers or mock the DockerClient.

### Task 7.3: Container Security Config

**`container/ContainerSecurityConfig.java`**: `@Component`.

```java
public HostConfig getSecurityConstraints(DockerService.ContainerType type) {
    long memoryBytes = type == DockerService.ContainerType.BUILD 
        ? 2L * 1024 * 1024 * 1024  // 2GB
        : 4L * 1024 * 1024 * 1024; // 4GB
    
    return HostConfig.newHostConfig()
        .withMemory(memoryBytes)
        .withMemorySwap(memoryBytes)  // No swap
        .withCpuQuota(200_000L)       // 2 cores (100000 per core)
        .withCpuPeriod(100_000L)
        .withPidsLimit(256L)
        .withNetworkMode(type == DockerService.ContainerType.TEST ? "none" : "bridge")
        .withSecurityOpts(List.of("no-new-privileges"));
}
```

### Task 7.4: Container Pool Manager

**`container/ContainerPoolManager.java`**: Use the implementation structure from the audit but ensure:
1. `initializePool()` is called from an `@EventListener(ApplicationReadyEvent.class)` (not left uncalled)
2. `claimContainer()` creates a new container if pool is empty (don't just return null)
3. `releaseContainer()` actually resets the workspace by executing `rm -rf /plugin-workspace/*`
4. `cleanupStaleContainers()` runs every 5 minutes and force-kills timed-out containers
5. Pool status is exposed via admin endpoint

### Task 7.5: Container Session Entity

**Flyway migration V5__build_iterations.sql** (rename from previous V5):

```sql
CREATE TABLE build_iterations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES build_sessions(id),
    iteration_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    trigger VARCHAR(30) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_build_iterations_session ON build_iterations(session_id);

CREATE TABLE container_sessions (
    id UUID PRIMARY KEY,
    iteration_id UUID NOT NULL REFERENCES build_iterations(id),
    container_id VARCHAR(100) NOT NULL,
    container_type VARCHAR(20) NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    memory_mb INT NOT NULL,
    cpu_millicores INT NOT NULL
);

CREATE TABLE build_errors (
    id UUID PRIMARY KEY,
    iteration_id UUID NOT NULL REFERENCES build_iterations(id),
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    stack_trace TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE artifacts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES build_sessions(id),
    iteration_id UUID NOT NULL REFERENCES build_iterations(id),
    jar_file_path VARCHAR(500),
    file_hash VARCHAR(64),
    file_size_bytes BIGINT,
    plugin_version VARCHAR(50),
    plugin_yml TEXT,
    security_passed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retention_expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE source_bundles (
    id UUID PRIMARY KEY,
    artifact_id UUID NOT NULL UNIQUE REFERENCES artifacts(id),
    source_zip_path VARCHAR(500),
    source_hash VARCHAR(64),
    source_size_bytes BIGINT,
    template_version VARCHAR(20),
    build_tool VARCHAR(20) NOT NULL DEFAULT 'MAVEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retention_expires_at TIMESTAMP WITH TIME ZONE
);
```

Create entities and repositories for all tables.

### Task 7.6: Admin Container Controller

**`container/ContainerController.java`**: `@RestController @RequestMapping("/api/v1/admin/containers")`

Only accessible with admin role (add a simple role check or use `@PreAuthorize`).

```java
@GetMapping      // Returns pool status
@PostMapping("/scale")  // Body: { warmBuild: 3, warmTest: 2 }
```

### Task 7.7: Tests

**`container/ContainerPoolManagerTest.java`**: Mock DockerService. Test: claim from warm pool, claim when pool empty (creates new), release back to pool, stale cleanup.
**`container/ContainerSecurityConfigTest.java`**: Verify memory/CPU/network constraints for each type.

### VERIFY Phase 7

```bash
cd api
mvn clean test
# If Docker is available:
docker build -f containers/Dockerfile.build -t pluginfactory-build .
docker build -f containers/Dockerfile.test -t pluginfactory-test .
```

**Commit**: `feat: Docker container management with warm pool and security constraints`

---

## Phase 8: Implementation Agent & Build Pipeline

**Time**: 2–3 hours
**Goal**: The CORE of the platform — Claude Code agent generates real plugin code, compiles it in a container, runs security scan, produces JAR artifact.

> **CRITICAL**: This phase is where the previous attempt failed completely. The build pipeline was stubbed with "simulated" comments. Every step below MUST have real implementation.

### Task 8.1: Plugin Template

Create **`agents/templates/plugin-template/`** directory with a real Maven project:

**`pom.xml`**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.bekololek.generated</groupId>
    <artifactId>{{artifactId}}</artifactId>
    <version>{{version}}</version>
    <packaging>jar</packaging>

    <repositories>
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>{{minecraftVersion}}-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**`src/main/resources/plugin.yml`** template:
```yaml
name: {{pluginName}}
version: {{version}}
main: com.bekololek.generated.{{mainClassName}}
api-version: '{{apiVersion}}'
description: "{{description}} | Generated by BekoLolek Plugin Factory"
commands:
{{commandsYaml}}
permissions:
{{permissionsYaml}}
```

### Task 8.2: Template Service

**`agent/TemplateService.java`**: `@Service`.

```java
public Map<String, String> renderTemplate(PlanDocument plan)
// 1. Load template files from classpath (or file system)
// 2. Replace all {{placeholders}} with plan document values
// 3. Generate plugin.yml from commands/permissions in the plan
// 4. Return Map<filePath, fileContent> representing the full project
// Paths like: "pom.xml", "src/main/java/com/bekololek/generated/MainClass.java", etc.
```

This must produce a REAL, compilable Maven project structure. Not stubs.

### Task 8.3: Implementer Agent

**`agent/ImplementerAgent.java`**: `@Service`.

```java
public ImplementationResult implement(UUID sessionId)
// 1. Load PlanDocument
// 2. Render template via TemplateService → base project files
// 3. Build system prompt with:
//    - Full plan document as context
//    - Template code as starting point
//    - Coding guidelines: use Paper API, don't use NMS, follow Java conventions
// 4. Call Claude API (Sonnet, CODE_GENERATION task type) with:
//    "Here is the plugin plan and template. Write the complete Java source code 
//     for each class. Return a JSON object where keys are file paths and values 
//     are the complete file contents."
// 5. Parse response: extract file path → content mapping
// 6. Merge generated code with template (template provides pom.xml, generated code
//    provides Java files)
// 7. Track tokens consumed
// 8. Return ImplementationResult(Map<String, String> files, int tokensUsed)
```

**Create `src/main/resources/prompts/implementer_system.txt`** with detailed coding instructions:
- Must compile against Paper API
- Must follow Java 17 conventions
- Must register commands and events properly
- Must handle config loading
- Must include proper error handling (try-catch in event handlers)
- Must use plugin logger, not System.out
- Response format specification

### Task 8.4: Build Pipeline Service — REAL Implementation

**`build/BuildPipelineService.java`**: This orchestrates the ENTIRE build. **Every step must be real.**

```java
public BuildIteration executeBuild(UUID sessionId) {
    BuildIteration iteration = createIteration(sessionId);
    
    try {
        // 1. IMPLEMENTATION: Generate code
        updatePhase(sessionId, IMPLEMENTATION);
        ImplementationResult implResult = implementerAgent.implement(sessionId);
        
        // 2. PREPARE CONTAINER: Create tar archive of the project
        updatePhase(sessionId, COMPILATION);
        String containerId = containerPoolManager.claimContainer(BUILD);
        recordContainerSession(iteration.getId(), containerId);
        
        try {
            // 3. INJECT: Copy project files into container
            byte[] projectTar = createTarArchive(implResult.files());
            dockerService.copyToContainer(containerId, projectTar, "/plugin-workspace/");
            
            // 4. COMPILE: Run Maven in the container
            ExecResult compileResult = dockerService.executeCommand(
                containerId, "sh", "-c", 
                "cd /plugin-workspace && mvn clean package -q -DskipTests 2>&1");
            
            if (compileResult.exitCode() != 0) {
                throw new CompilationException(compileResult.stderr());
            }
            
            // 5. SECURITY SCAN
            updatePhase(sessionId, SECURITY_SCAN);
            // Scan generated source code (not JAR) for dangerous patterns
            String allSource = implResult.files().entrySet().stream()
                .filter(e -> e.getKey().endsWith(".java"))
                .map(Map.Entry::getValue)
                .collect(Collectors.joining("\n"));
            SecurityScanResult scanResult = securityScanService.scanSource(allSource);
            
            if (!scanResult.passed()) {
                throw new SecurityViolationException(scanResult.violations());
            }
            
            // 6. EXTRACT JAR from container
            byte[] jarBytes = dockerService.copyFromContainer(
                containerId, "/plugin-workspace/target/" + artifactName + ".jar");
            
            // 7. Create source ZIP
            byte[] sourceZip = createSourceZip(implResult.files());
            
            // 8. STORE ARTIFACT
            updatePhase(sessionId, DELIVERING);
            Artifact artifact = artifactService.storeArtifact(
                sessionId, iteration.getId(), jarBytes, sourceZip, 
                extractPluginYml(implResult.files()));
            
            // 9. SUCCESS
            completeIteration(iteration, COMPLETED);
            buildSessionService.updateStatus(sessionId, COMPLETED);
            buildProgressService.notifyStatusChange(sessionId, COMPLETED);
            
        } finally {
            containerPoolManager.releaseContainer(containerId);
        }
        
    } catch (CompilationException e) {
        handleBuildError(iteration, sessionId, e, RECOVERABLE);
    } catch (SecurityViolationException e) {
        handleBuildError(iteration, sessionId, e, SECURITY);
    } catch (Exception e) {
        handleBuildError(iteration, sessionId, e, STRUCTURAL);
    }
    
    return iteration;
}
```

Implement the helper methods: `createTarArchive()`, `createSourceZip()`, `extractPluginYml()`, `handleBuildError()`.

### Task 8.5: Artifact Service

**`build/ArtifactService.java`**:

```java
public Artifact storeArtifact(UUID sessionId, UUID iterationId, byte[] jarBytes, 
                                byte[] sourceZipBytes, String pluginYml)
// 1. Generate file paths: builds/{sessionId}/{iterationId}/plugin.jar and /source.zip
// 2. Store JAR in filesystem (dev) or S3 (prod) — for now, filesystem is fine
//    Path: /data/artifacts/{sessionId}/{iterationId}/plugin.jar
// 3. Store source ZIP alongside
// 4. Compute SHA-256 hash of JAR
// 5. Create Artifact record with: jarFilePath, fileHash, fileSizeBytes, pluginVersion, 
//    pluginYml, securityPassed=true, retentionExpiresAt (based on tier)
// 6. Create SourceBundle record
// 7. Return Artifact

public byte[] downloadArtifact(UUID artifactId, UUID userId)
// 1. Load artifact
// 2. Load session, verify userId matches session.userId
// 3. Read file from storage path
// 4. Return bytes
```

### Task 8.6: Artifact Controller

**`build/ArtifactController.java`**: `@RestController`

```java
@GetMapping("/api/v1/builds/{sessionId}/artifacts")     // List artifacts for session
@GetMapping("/api/v1/artifacts/{artifactId}/download")   // Download JAR (validates ownership)
@GetMapping("/api/v1/artifacts/{artifactId}/security")   // Get security scan results
```

### Task 8.7: Tests

**`agent/TemplateServiceTest.java`**: Verify rendered project has valid pom.xml, plugin.yml, correct package structure.
**`build/BuildPipelineServiceTest.java`**: Mock agent + Docker. Test: full success flow, compilation failure, security failure. Verify all entities created correctly.
**`build/ArtifactServiceTest.java`**: Test store and retrieve. Test ownership validation.

### VERIFY Phase 8

```bash
cd api
mvn clean test
# Full integration test (requires Docker):
# Create build → chat → approve plan → execute build → download JAR
```

**Commit**: `feat: implementation agent, build pipeline, artifact storage`

---

## Phase 9: Error Classification & Iteration Loops

**Time**: 2–3 hours
**Goal**: Classify build errors, implement retry logic with budget awareness, user-facing iteration requests.

### Task 9.1: Error Classifier

**`build/ErrorClassifier.java`**: `@Service`.

```java
public ErrorCategory classify(String errorMessage) {
    if (errorMessage == null) return ErrorCategory.STRUCTURAL;
    String lower = errorMessage.toLowerCase();
    
    // SECURITY — immediate block
    if (containsAny(lower, "runtime.exec", "processbuilder", "socket", "serversocket",
        "urlconnection", "sun.misc.unsafe", "net.minecraft.server",
        "java.lang.reflect", "class.forname")) {
        return ErrorCategory.SECURITY;
    }
    
    // RECOVERABLE — auto-retry worth attempting
    if (containsAny(lower, "cannot find symbol", "cannot resolve", "incompatible types",
        "missing return", "unreported exception", "not a statement", "';' expected",
        "package does not exist", "cannot be applied", "null pointer", 
        "variable might not have been initialized")) {
        return ErrorCategory.RECOVERABLE;
    }
    
    // STRUCTURAL — abort, fundamental issue
    return ErrorCategory.STRUCTURAL;
}

public enum ErrorCategory { RECOVERABLE, STRUCTURAL, SECURITY }
```

### Task 9.2: Retry Policy

**`build/RetryPolicy.java`**: `@Component`.

```java
public boolean shouldRetry(UUID sessionId, ErrorCategory category, int currentRetryCount) {
    if (category == ErrorCategory.SECURITY) return false;
    if (category == ErrorCategory.STRUCTURAL) return false;
    if (currentRetryCount >= 3) return false;
    
    // Check budget — need at least 20% remaining for a retry to be worthwhile
    TokenBudget budget = tokenBudgetService.getRemainingBudget(sessionId);
    int remaining = budget.getAllocatedTokens() - budget.getConsumedTokens();
    return remaining > budget.getAllocatedTokens() * 0.2;
}
```

### Task 9.3: Integrate into BuildPipelineService

Update `handleBuildError()`:
```java
private void handleBuildError(BuildIteration iteration, UUID sessionId, 
                               Exception e, ErrorCategory category) {
    BuildError error = BuildError.builder()
        .iterationId(iteration.getId())
        .category(category)
        .severity(category == ErrorCategory.SECURITY ? CRITICAL : MEDIUM)
        .message(e.getMessage())
        .stackTrace(ExceptionUtils.getStackTrace(e))
        .retryCount(0)
        .build();
    buildErrorRepository.save(error);
    
    if (category == ErrorCategory.SECURITY) {
        // Flag the user account
        userService.flagAccount(iteration.getUserId(), "Security violation in build " + sessionId);
    }
    
    if (retryPolicy.shouldRetry(sessionId, category, countRetries(iteration))) {
        // Retry: re-run implementation with error context
        retryBuild(sessionId, iteration, error);
    } else {
        completeIteration(iteration, FAILED);
        buildSessionService.updateStatus(sessionId, FAILED);
        buildProgressService.notifyError(sessionId, e.getMessage());
    }
}

private void retryBuild(UUID sessionId, BuildIteration iteration, BuildError error) {
    // Append error context to the agent prompt so it can fix the issue
    String errorContext = "Previous compilation failed with: " + error.getMessage() 
        + "\nPlease fix the code and try again.";
    // Create new iteration linked to the same session
    // Re-run the pipeline with error context
}
```

### Task 9.4: Iteration Service

**`build/IterationService.java`**: `@Service`.

```java
public BuildIteration requestIteration(UUID sessionId, UUID userId, String feedback)
// 1. Load session, verify ownership, verify status == COMPLETED
// 2. Get tier, check iterations used vs max_iterations
// 3. Check token budget remaining
// 4. Create new BuildIteration (trigger=USER_REQUEST)
// 5. Add feedback as context: append to chat history as a user message
// 6. Re-run BuildPipelineService.executeBuild() with the feedback context
// 7. Return the new iteration
```

### Task 9.5: Iteration Controller

**`build/IterationController.java`**: `@RestController`

```java
@GetMapping("/api/v1/builds/{sessionId}/iterations")   // List all iterations
@PostMapping("/api/v1/builds/{sessionId}/iterate")      // Body: { feedback: "..." }
// Returns 403 if iteration limit reached
// Returns 402 if budget exhausted 
// Returns 200 with new iteration started
```

### Task 9.6: Tests

**`build/ErrorClassifierTest.java`**: Test each pattern category.
**`build/RetryPolicyTest.java`**: Test budget checks, retry limits, category blocking.
**`build/IterationServiceTest.java`**: Happy path, over-limit, budget-exhausted.

### VERIFY Phase 9

```bash
cd api
mvn clean test
```

**Commit**: `feat: error classification, retry logic, iteration loops`

---

## Phase 10: Frontend — Project Setup & Auth

**Time**: 2–3 hours
**Goal**: React/TypeScript project with Vite, Tailwind, routing, Discord OAuth flow, authenticated layout.

### Task 10.1: Initialize React project in /web

```bash
cd web
npm create vite@latest . -- --template react-ts
npm install react-router-dom @tanstack/react-query zustand axios tailwindcss @tailwindcss/vite
npm install -D @types/react @types/react-dom vitest @testing-library/react @testing-library/jest-dom
```

Configure Tailwind with `@tailwindcss/vite` plugin in `vite.config.ts`. Set up path alias `@/` → `src/` in both vite config and tsconfig.

### Task 10.2: Project structure

```
src/
├── api/
│   ├── client.ts          # Axios instance with auth interceptors
│   ├── auth.ts            # Auth API functions
│   ├── builds.ts          # Build session API functions
│   ├── subscriptions.ts   # Subscription API functions
│   └── marketplace.ts     # Marketplace API functions
├── components/
│   ├── ChatInput.tsx
│   ├── ChatMessage.tsx
│   ├── BuildStatusBadge.tsx
│   ├── TokenBudgetBar.tsx
│   ├── ProtectedRoute.tsx
│   ├── Sidebar.tsx
│   ├── LoadingSkeleton.tsx
│   ├── ErrorBoundary.tsx
│   ├── EmptyState.tsx
│   └── NotificationToast.tsx
├── hooks/
│   ├── useAuth.ts
│   ├── useBuilds.ts
│   └── useWebSocket.ts
├── layouts/
│   ├── AuthLayout.tsx
│   └── DashboardLayout.tsx
├── pages/
│   ├── LoginPage.tsx
│   ├── AuthCallbackPage.tsx
│   ├── DashboardPage.tsx
│   ├── NewBuildPage.tsx
│   ├── BuildsPage.tsx
│   ├── BuildDetailPage.tsx
│   ├── MarketplacePage.tsx
│   ├── SubscriptionPage.tsx
│   └── SettingsPage.tsx
├── stores/
│   └── authStore.ts
├── types/
│   └── index.ts           # All TypeScript interfaces matching backend DTOs
├── App.tsx
├── main.tsx
└── index.css
```

### Task 10.3: TypeScript types

**`types/index.ts`**: Define interfaces for ALL backend DTOs:

```typescript
export interface User {
  id: string; email: string; displayName: string;
  discordId: string; status: string; tier: string; createdAt: string;
}

export interface BuildSession {
  id: string; userId: string; status: BuildStatus; currentPhase: BuildPhase;
  complexityScore: number | null; createdAt: string; completedAt: string | null;
}

export type BuildStatus = 'CHATTING' | 'PLANNING' | 'APPROVED' | 'BUILDING' | 'TESTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type BuildPhase = 'IDLE' | 'CLARIFICATION' | 'PLAN_GENERATION' | 'PLAN_REVIEW' | 'IMPLEMENTATION' | 'COMPILATION' | 'SECURITY_SCAN' | 'INTEGRATION_TEST' | 'DELIVERING';

export interface ChatMessageDto { id: string; sessionId: string; role: 'user' | 'assistant'; content: string; modelUsed: string | null; tokensConsumed: number; createdAt: string; }
export interface TokenBudgetDto { allocated: number; consumed: number; planning: number; implementation: number; testing: number; thresholdStatus: 'NORMAL' | 'WARNING' | 'CRITICAL' | 'EXHAUSTED'; }
export interface PlanDocumentDto { /* ... all fields ... */ }
export interface Artifact { id: string; sessionId: string; jarFilePath: string; fileHash: string; fileSizeBytes: number; pluginVersion: string; securityPassed: boolean; createdAt: string; }
export interface TierDto { name: string; maxBuilds: number; tokenBudget: number; maxParallel: number; maxIterations: number; marketplaceSlots: number; priceMonthly: number; }
export interface MarketplaceListing { /* ... */ }
export interface AuthResponse { accessToken: string; refreshToken: string; user: User; }
```

### Task 10.4: Axios client with interceptors

**`api/client.ts`**:
```typescript
import axios from 'axios';
import { useAuthStore } from '@/stores/authStore';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
});

// Request interceptor: add auth token
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Response interceptor: handle 401, auto-refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      const { refreshToken, refresh, logout } = useAuthStore.getState();
      if (refreshToken) {
        try {
          await refresh();
          return api.request(error.config); // Retry original request
        } catch { logout(); }
      } else { logout(); }
    }
    return Promise.reject(error);
  }
);

export default api;
```

### Task 10.5: Auth store (Zustand)

**`stores/authStore.ts`**: Zustand store with persist middleware (sessionStorage):

```typescript
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  isAuthenticated: boolean;
  login: (response: AuthResponse) => void;
  logout: () => void;
  refresh: () => Promise<void>;
}
```

### Task 10.6: Pages and routing

Implement LoginPage (Discord button), AuthCallbackPage (exchange code for tokens), DashboardLayout (sidebar + header + outlet), ProtectedRoute (redirect to login if not authenticated).

**App.tsx** routing:
```
/ → LoginPage
/auth/callback → AuthCallbackPage
/dashboard → DashboardLayout
  /dashboard → DashboardPage (overview)
  /dashboard/builds/new → NewBuildPage
  /dashboard/builds → BuildsPage (list)
  /dashboard/builds/:id → BuildDetailPage
  /dashboard/marketplace → MarketplacePage
  /dashboard/settings → SettingsPage
  /dashboard/settings/subscription → SubscriptionPage
```

### Task 10.7: Sidebar component

Real navigation with links, active state highlighting, user avatar/name at bottom, responsive (hamburger on mobile).

### VERIFY Phase 10

```bash
cd web
npm run lint    # No errors
npm run build   # Successful build
npm run dev     # Starts on localhost:5173
# Navigate to http://localhost:5173 — login page renders
```

**Commit**: `feat: React frontend with auth flow, routing, dashboard layout`

---

## Phase 11: Frontend — Chat, Build Progress, Plan Review

**Time**: 2–3 hours
**Goal**: Working chatbot UI with streaming, plan review panel, real-time build progress dashboard.

### Task 11.1: TanStack Query hooks

**`hooks/useBuilds.ts`**: Custom hooks using TanStack Query:

```typescript
export function useCreateBuild() // useMutation → POST /builds
export function useBuilds(page: number) // useQuery → GET /builds
export function useBuild(id: string) // useQuery → GET /builds/:id
export function useMessages(sessionId: string) // useQuery → GET /builds/:id/messages (refetch every 5s during active session)
export function useSendMessage(sessionId: string) // useMutation → POST /builds/:id/messages
export function useTokenBudget(sessionId: string) // useQuery → GET /builds/:id/budget
export function usePlan(sessionId: string) // useQuery → GET /builds/:id/plan
export function useApprovePlan(sessionId: string) // useMutation → POST /builds/:id/plan/approve
export function useRevisePlan(sessionId: string) // useMutation → POST /builds/:id/plan/revise
export function useIterate(sessionId: string) // useMutation → POST /builds/:id/iterate
```

### Task 11.2: WebSocket hook

**`hooks/useWebSocket.ts`**: Connect to WebSocket for real-time build progress:

```typescript
export function useBuildProgress(sessionId: string, onMessage: (msg: BuildProgressEvent) => void)
// Connect to ws://localhost:8080/ws via SockJS + STOMP
// Subscribe to /topic/builds/{sessionId}/progress
// Call onMessage for each event
// Cleanup on unmount
```

### Task 11.3: ChatMessage component

Render user messages (right-aligned, blue) and assistant messages (left-aligned, gray). Assistant messages render markdown (use `react-markdown`). Show model badge and token count on assistant messages.

### Task 11.4: ChatInput component

Text area with send button. Shift+Enter for newline, Enter to send. Disabled while message is pending. Character count indicator.

### Task 11.5: NewBuildPage — full implementation

1. On mount with no ID: create build session, redirect to /dashboard/builds/:id
2. On mount with ID: load session, messages, budget
3. Render ChatInterface: message list + ChatInput
4. On send: call useSendMessage, optimistically add user message to list
5. When session enters PLANNING: show plan review panel
6. When session enters BUILDING: show build progress panel

### Task 11.6: Plan Review Panel

**`components/PlanReviewPanel.tsx`**: Structured display of the plan document — plugin name, description, commands table, event listeners list, config options, dependencies. Two buttons: "Approve & Build" (calls approve endpoint), "Request Changes" (opens feedback input, calls revise endpoint).

Handle scope violation responses: show red alert with specific violations and "Upgrade Tier" link.

### Task 11.7: Build Progress Panel

**`components/BuildProgressPanel.tsx`**: Stepper showing phases (Planning → Compiling → Scanning → Testing → Done). Active phase highlighted with spinner. Connect to WebSocket for real-time updates. On completion: show "Download JAR" button.

### Task 11.8: Token Budget Bar

**`components/TokenBudgetBar.tsx`**: Horizontal progress bar. Green 0-79%, yellow 80-94%, red 95-100%. Shows "125K / 200K tokens used" text. Updates in real-time via budget query.

### Task 11.9: My Builds Page

**`pages/BuildsPage.tsx`**: Paginated table/card list of build sessions. Each row: plugin name (from plan), status badge (colored), tier, date, actions (view, download if completed). Empty state if no builds.

### Task 11.10: Build Detail Page

**`pages/BuildDetailPage.tsx`**: Full session view with tabs: Chat (full history), Plan (structured plan), Iterations (list with status, errors), Artifacts (download buttons). "Iterate" button if session is completed and iterations remaining.

### VERIFY Phase 11

```bash
cd web
npm run build
npm run dev
# With backend running: create a build, chat, see real AI responses
```

**Commit**: `feat: chatbot interface, plan review, build progress, builds list`

---

## Phase 12: Frontend — Subscriptions, Settings, Final Pages

**Time**: 2 hours
**Goal**: Subscription management, usage dashboard, API keys, remaining frontend pages.

### Task 12.1: Subscription Page

Tier comparison cards (4 tiers side by side). Current tier highlighted. Feature comparison table. Upgrade/downgrade buttons → Stripe checkout. "Manage Billing" → Stripe portal.

### Task 12.2: Usage Dashboard

Stats cards: builds used / limit, tokens consumed (bar chart with recharts), active builds. Period selector.

### Task 12.3: Settings Page

Profile section (edit display name). API key management (list, create with copy-once modal, revoke with confirmation).

### Task 12.4: Loading, Error, Empty States

**`components/LoadingSkeleton.tsx`**: Reusable skeleton with Tailwind `animate-pulse`.
**`components/ErrorBoundary.tsx`**: Class component wrapping routes. Shows friendly error + retry button.
**`components/EmptyState.tsx`**: Reusable with icon, title, description, optional CTA button.
**`components/NotificationToast.tsx`**: Toast system using React portal. Auto-dismiss after 5s.

Add these to ALL pages that fetch data.

### VERIFY Phase 12

```bash
cd web
npm run build
npm run dev
# Navigate all pages, verify no blank screens or unhandled states
```

**Commit**: `feat: subscription, settings, usage dashboard, loading/error states`

---

## Phase 13: Marketplace Backend

**Time**: 2–3 hours
**Goal**: Marketplace API — listings CRUD, search, reviews, purchases.

### Task 13.1: Flyway migration V6__marketplace.sql

Tables: marketplace_listings, reviews, purchases, artifact_versions. (See architecture doc Section 21.7 for full schema.)

### Task 13.2: Entities and repositories

MarketplaceListing, Review, Purchase, ArtifactVersion — each with full entity definitions and repositories with query methods (search by category, filter by Minecraft version, pagination).

### Task 13.3: Marketplace Service

Full CRUD + search with Spring Data JPA Specifications for dynamic filtering:
- Text search (LIKE on title + description)
- Category filter
- Minecraft version filter
- Price filter (free/paid)
- Sort by: rating, downloads, date, price
- Pagination

### Task 13.4: Review Service

Submit review (validate purchaser/downloader), prevent duplicates, recalculate average rating.

### Task 13.5: Purchase Service

Free plugins: record download, return JAR. Paid plugins: create Stripe PaymentIntent, record purchase on webhook confirmation.

### Task 13.6: Marketplace Controller

All endpoints from architecture doc Section 21.7. Enforce marketplace slot limits per tier.

### Task 13.7: Tests

Search with various filters. Review submission and duplicate prevention. Purchase flow (free and paid). Slot limit enforcement.

### VERIFY Phase 13

```bash
cd api
mvn clean test
```

**Commit**: `feat: marketplace backend with search, reviews, purchases`

---

## Phase 14: Frontend — Marketplace

**Time**: 2–3 hours
**Goal**: Marketplace browse, plugin detail, publish flow, reviews, purchases.

### Task 14.1–14.7

MarketplaceBrowsePage (search + filter + grid), PluginCard component, PluginDetailPage (description, reviews, download/purchase), ReviewSection (submit + display), PublishPluginPage (select build → fill metadata → publish), MyListingsPage, MyPurchasesPage.

Each page must have loading skeletons, error states, and empty states.

### VERIFY Phase 14

```bash
cd web
npm run build
```

**Commit**: `feat: marketplace frontend with browse, publish, reviews`

---

## Phase 15: Source Code Storage & Watermarking

**Time**: 2 hours
**Goal**: Source code request workflow, watermarking, license agreement, download.

### Task 15.1: Flyway migration V7__source_requests.sql

```sql
CREATE TABLE source_code_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    artifact_id UUID NOT NULL REFERENCES artifacts(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    license_version VARCHAR(20) NOT NULL,
    license_accepted_at TIMESTAMP WITH TIME ZONE,
    license_accepted_ip VARCHAR(45),
    watermark_id UUID NOT NULL,
    download_path VARCHAR(500),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fulfilled_at TIMESTAMP WITH TIME ZONE
);
```

### Task 15.2: Watermark Service

**`build/WatermarkService.java`**: Inject header comment into EVERY .java file:

```java
public Map<String, String> watermarkSource(Map<String, String> sourceFiles, 
                                            UUID sessionId, UUID userId, UUID watermarkId) {
    String header = """
        /*
         * Generated by BekoLolek Plugin Factory
         * Session: %s
         * Licensed to: %s
         * Watermark: %s
         * Generated: %s
         * License: Personal use only. No redistribution. No commercial use.
         *          Full terms at https://pluginfactory.bekololek.com/license
         */
        """.formatted(sessionId, userId, watermarkId, Instant.now());
    
    return sourceFiles.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> e.getKey().endsWith(".java") ? header + "\n" + e.getValue() : e.getValue()
        ));
}
```

### Task 15.3: Source Code Request Service and Controller

Full flow: request (validate Pro+ tier, create record) → fulfill (retrieve source bundle, apply watermark, create ZIP, store) → download (validate ownership, return bytes).

### Task 15.4: Tests

Watermark injection test. Tier enforcement test. Full request → fulfill → download flow.

### VERIFY Phase 15

```bash
cd api
mvn clean test
```

**Commit**: `feat: source code requests with watermarking and license enforcement`

---

## Phase 16: Infrastructure — Docker Compose, Nginx, Monitoring

**Time**: 2–3 hours
**Goal**: Production-ready Docker Compose stack, Nginx reverse proxy, monitoring, Discord alerting.

### Task 16.1: docker-compose.yml

Services: api, postgres, redis, nginx, minio. All with health checks, restart policies, named volumes.

### Task 16.2: Nginx config

Reverse proxy /api → api:8080, WebSocket upgrade for /ws, rate limiting, gzip, SSL placeholder.

### Task 16.3: API Dockerfile

Multi-stage: Maven build → JRE runtime. Use `eclipse-temurin:17-jre-jammy` for small image.

### Task 16.4: Monitoring docker-compose.monitoring.yml

Prometheus (scrape Actuator /actuator/prometheus) + Grafana (pre-configured dashboards).

### Task 16.5: Discord alerting script

**`infra/scripts/discord-alert.sh`**: Receives AlertManager webhook JSON, formats it, posts to Discord webhook URL with embeds (color-coded by severity).

### Task 16.6: Deploy and smoke test scripts

**`infra/scripts/deploy.sh`**: SSH deploy with zero-downtime.
**`infra/scripts/smoke-test.sh`**: Start stack, wait for health, run basic API calls, tear down.

### Task 16.7: Vercel config

**`web/vercel.json`**: Root directory `/web`, build command `npm run build`, output `dist`.

### VERIFY Phase 16

```bash
cd infra
docker compose up -d
docker compose ps           # All services healthy
curl http://localhost/health  # Via nginx
docker compose down
```

**Commit**: `feat: Docker Compose stack, Nginx, monitoring, deploy scripts`

---

## Phase 17: CI/CD Pipeline

**Time**: 2 hours
**Goal**: GitHub Actions workflows for backend CI, frontend CI, security scan, staging and production deploy.

### Task 17.1–17.6

- `.github/workflows/backend-ci.yml` — lint, test, coverage gate (80%)
- `.github/workflows/frontend-ci.yml` — lint, test, build
- `.github/workflows/security-scan.yml` — OWASP + npm audit (weekly + on PR)
- `.github/workflows/deploy-staging.yml` — on push to develop
- `.github/workflows/deploy-production.yml` — on release tag
- `.github/dependabot.yml` — weekly updates for Maven and npm
- Root `Makefile` with: dev, test, build, deploy-staging, deploy-prod targets

### VERIFY Phase 17

Push to a branch, verify CI runs.

**Commit**: `ci: GitHub Actions pipelines for CI/CD`

---

## Phase 18: Team Features

**Time**: 2–3 hours
**Goal**: Team creation, member management, shared workspaces, team analytics.

### Task 18.1: Migration, entities, services

V8__teams.sql: teams, team_members, shared_workspaces tables.
TeamService, SharedWorkspaceService with full CRUD and access control.
TeamController with all endpoints from architecture doc Section 21.8.

### Task 18.2: Update BuildSessionService

Allow optional workspaceId when creating builds. Workspace builds visible to all team members.

### Task 18.3: Tests

Team CRUD, member management, workspace visibility, non-member access denied.

### VERIFY Phase 18

```bash
cd api
mvn clean test
```

**Commit**: `feat: team collaboration with shared workspaces`

---

## Phase 19: Frontend — Teams & Final Polish

**Time**: 2–3 hours
**Goal**: Team UI, final polish across entire frontend.

### Task 19.1–19.4

TeamDashboardPage, TeamMemberManagement, SharedWorkspacePage. Notification toast system. Accessibility pass (aria labels, keyboard nav).

### VERIFY Phase 19

```bash
cd web
npm run build
npm run lint
```

**Commit**: `feat: team frontend, notifications, accessibility polish`

---

## Phase 20: Security Hardening

**Time**: 3–4 hours
**Goal**: Comprehensive security audit and hardening across backend, frontend, and infrastructure. Fix all CRITICAL, HIGH, MEDIUM, and LOW severity findings from the security review.

### Task 20.1: RBAC & JWT Hardening (CRITICAL)

- Add `UserRole` enum (`USER`, `ADMIN`) to `User` entity
- Flyway migration `V9__user_role.sql` to add `role` column
- Include `role` claim in JWT access tokens (`JwtService.generateAccessToken(UUID, String)`)
- Extract role in `JwtAuthenticationFilter` and populate `GrantedAuthority` list
- Enable `@EnableMethodSecurity` in `SecurityConfig`
- Protect `/api/v1/admin/**` with `hasRole('ADMIN')` in security filter chain
- Enforce `JWT_SECRET` env var in prod profile (no fallback to dev secret)

### Task 20.2: Authorization Fixes (CRITICAL/HIGH)

- Add ownership check to `SourceCodeRequestService.fulfillRequest(UUID requestId, UUID userId)`
- Restrict `/actuator/**` to admin role (except `/actuator/health/**`)
- Remove `downloadPath` from `SourceCodeRequestDto` to prevent internal path leakage

### Task 20.3: Docker Socket Security (CRITICAL)

- Replace direct Docker socket mount with `tecnativa/docker-socket-proxy`
- Add `docker-proxy` service to `docker-compose.yml` with restricted API permissions (CONTAINERS, EXEC, IMAGES only)
- Remove default credentials from postgres, redis, minio environment variables

### Task 20.4: WebSocket Security (HIGH)

- Restrict WebSocket CORS to configured `cors.allowed-origins` (not `*`)
- Reject unauthenticated STOMP CONNECT in `WebSocketAuthInterceptor` (throw `MessageDeliveryException`)

### Task 20.5: Input Validation & Rate Limiting (MEDIUM)

- Add OAuth `state` parameter to Discord flow (generate, store, validate)
- URL-encode `redirectUri` and `clientId` in authorization URL
- Add `@RateLimiter(name = "auth")` to `/discord/callback` and `/refresh`
- Add `@RateLimiter(name = "chat")` to `/messages` endpoint
- Cap pagination `size` to `Math.min(size, 100)` on all paginated endpoints
- Add `@Valid` to `UpdateListingRequest`
- Add `@Size` / `@Min` constraints to all marketplace DTOs and `SendMessageRequest`
- Validate tier string before `Tier.valueOf()` in `SubscriptionController`
- Fix hardcoded `localhost:5173` URLs in `StripeService` to use `app.base-url` config
- Add Zip Slip protection in `SourceCodeRequestService.extractSourceFiles()`

### Task 20.6: Prompt Injection Defense (HIGH)

- Change `PromptSanitizer` to throw `ValidationException` when injection patterns detected (instead of logging and passing through)
- Add additional patterns: `disregard`, `pretend you`, `jailbreak`

### Task 20.7: Error Handling & Cleanup (LOW)

- Unify refresh token error messages to "Invalid or expired refresh token"
- Add team membership check to `GET /api/v1/teams/{teamId}`
- Add `RefreshTokenCleanupTask` scheduled hourly to purge expired tokens
- Add `IllegalArgumentException` and `RequestNotPermitted` handlers to `GlobalExceptionHandler`
- Restrict CORS `allowedHeaders` to specific headers instead of `*`
- Add non-root user to build/test container Dockerfiles

### Task 20.8: Frontend Security Headers (HIGH)

- Add CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy to `vercel.json`

### Task 20.9: Frontend Token Storage (HIGH/MEDIUM)

- Move JWT tokens from `localStorage` to `sessionStorage` (not persisted across browser sessions)
- Only persist user info (not tokens) to `localStorage` via Zustand persist
- Add JWT expiry check in `isAuthenticated()` using decoded `exp` claim
- Add `updateUser()` action to auth store (eliminate direct localStorage manipulation)
- Update API client to read tokens from `sessionStorage`

### Task 20.10: Frontend Auth Flow Hardening (MEDIUM)

- Add OAuth `state` parameter validation to `AuthCallbackPage`
- Validate redirect URLs before `window.location.href` assignment (Discord: `https://discord.com/`, Stripe: `https://checkout.stripe.com/` or `https://billing.stripe.com/`)
- Remove production fallback to `http://localhost:8080` in API client

### Task 20.11: Frontend Polish (LOW)

- Hide error details in production ErrorBoundary (`import.meta.env.PROD`)
- Clear React Query cache on logout
- Explicitly disable source maps in Vite config (`build.sourcemap: false`)
- Add `maxLength` to all text inputs (team name, review comment, API key name, display name)

### VERIFY

```bash
cd api && JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw test    # 287 tests pass
cd web && npx tsc --noEmit                                          # 0 errors
```

---

## Phase Summary

| # | Phase | Time | Depends On |
|---|-------|------|------------|
| 1 | Project Scaffolding | 2–3h | None |
| 2 | Authentication | 2–3h | 1 |
| 3 | User & Subscription | 2–3h | 2 |
| 4 | Build Session & Chat | 2–3h | 3 |
| 5 | AI Agent (Chatbot) | 2–3h | 4 |
| 6 | Plan Generation & Scope Gating | 2–3h | 5 |
| 7 | Docker Container Management | 2–3h | 1 |
| 8 | Build Pipeline (CRITICAL) | 2–3h | 6 + 7 |
| 9 | Error Classification & Iteration | 2–3h | 8 |
| 10 | Frontend: Setup & Auth | 2–3h | 2 |
| 11 | Frontend: Chat & Build Flow | 2–3h | 5 + 10 |
| 12 | Frontend: Subscription & Settings | 2h | 3 + 10 |
| 13 | Marketplace Backend | 2–3h | 8 |
| 14 | Frontend: Marketplace | 2–3h | 10 + 13 |
| 15 | Source Code & Watermarking | 2h | 8 |
| 16 | Infrastructure & Deploy | 2–3h | 1–9 |
| 17 | CI/CD Pipeline | 2h | 16 |
| 18 | Team Features | 2–3h | 9 |
| 19 | Frontend: Teams & Polish | 2–3h | 10 + 18 |
| 20 | Security Hardening | 3–4h | 1–19 |

**Total: 43–57 hours across 20 phases.**

Parallelizable: Phases 7, 10, 12 can run alongside main backend track. Phases 13–15 can run in parallel after Phase 8. Phase 20 runs after all phases complete.
