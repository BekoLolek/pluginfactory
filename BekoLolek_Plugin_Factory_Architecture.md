**BEKOLOLEK PLUGIN FACTORY**

System Architecture & Technical Specification

AI-Powered Minecraft Plugin Development Platform

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Version 1.5

March 2026

**CONFIDENTIAL**

**Table of Contents**

1\. Executive Summary

2\. System Architecture Overview

3\. Pipeline Flow

4\. Model Routing Strategy

5\. Token Budget System

6\. Container Infrastructure

7\. Security Architecture
   - 7.1 Input Filtering (Prompt Injection Defense)
   - 7.2 Container Sandboxing
   - 7.3 Static Analysis & Security Scan
   - 7.4 Output Validation
   - 7.5 Authentication & Authorization
   - 7.6 API Security
   - 7.7 Infrastructure Security
   - 7.8 Frontend Security
   - 7.9 WebSocket Security

8\. Error Classification & Fail-Fast

9\. Caching Strategy

10\. Deployment Options

11\. Git Branching Strategy

12\. CI/CD Pipeline

13\. Pricing Tiers

14\. Plugin Marketplace

15\. Infrastructure Upgrade Triggers

16\. Intellectual Property & Source Code Policy

17\. Frontend Architecture & Technologies

18\. Backend Architecture & Technologies

19\. Conceptual Domain Model

20\. Feature List

21\. API Endpoints

22\. Cost Analysis

23\. Implementation Roadmap

**1. Executive Summary**

BekoLolek Plugin Factory is an AI-powered platform that enables Minecraft server administrators and developers to create custom plugins through natural language conversations. Users describe their plugin idea to an AI chatbot, which guides them through refinement, generates a detailed plan document, and hands it off to an autonomous implementation agent that writes, compiles, tests, and delivers a production-ready JAR file.

The platform is designed around six core cost-control principles: intelligent model routing (using the cheapest capable model for each task), hard token budgets per session, aggressive caching of common patterns, scope gating before compute starts, container reuse via warm snapshots, and fail-fast error classification to avoid wasted iterations. Infrastructure costs start at \$0/month by leveraging Oracle Cloud's free tier for the backend and Vercel's free tier for the React/TypeScript frontend, scaling to paid infrastructure only when revenue and usage demand it.

Revenue is generated through a tiered subscription model (Free, Basic, Pro, Team) with a plugin marketplace providing an additional revenue stream through listing fees and transaction commissions.

**2. System Architecture Overview**

The system is composed of six primary layers, each with distinct responsibilities and clear interfaces between them. See the accompanying system architecture diagram (01-system-architecture.mermaid) for the visual representation.

**2.1 Client Layer**

The web dashboard is a React/TypeScript application that serves as the primary interface. It connects to the backend via WebSocket for real-time build progress updates and REST API for CRUD operations. The dashboard provides the chatbot interface, build monitoring, JAR downloads, and marketplace access.

**2.2 API Gateway & Auth**

All requests pass through an API gateway that handles authentication (JWT tokens), tier-based rate limiting, and input filtering. The input filter is the first line of defense against prompt injection attacks, stripping or flagging messages that contain patterns designed to manipulate the AI agents.

**2.3 Orchestration Layer**

The Pipeline Manager coordinates the entire build lifecycle. It works with three subsystems:

- **Token Budget Controller:** Allocates and tracks token consumption per session, enforcing tier limits and triggering warnings at 80% and 95% thresholds.

- **Model Router:** Selects the appropriate Claude model (Haiku/Sonnet) based on task type, optimizing cost without sacrificing quality.

- **Queue Engine:** Manages build priority. Free-tier jobs are queued for off-peak execution; paid tiers get priority proportional to their plan level.

**2.4 Planning Phase**

The chatbot agent uses Claude Haiku for initial conversation and clarification, upgrading to Sonnet for plan document generation. The scope gating engine estimates plugin complexity and validates it against the user's tier limits before any expensive compute begins.

**2.5 Implementation Phase**

The Claude Code agent (running Sonnet) receives the approved plan document and the versioned plugin template. It generates code within a sandboxed Docker container, runs compilation, performs static analysis and security scanning, then deploys to a Paper server container for integration testing.

**2.6 Data Layer**

PostgreSQL stores user accounts, build history, plan documents, and subscription data. Redis handles session caching, template caching, and common code pattern caching. Object storage (S3-compatible) holds compiled JARs and build logs.

**3. Pipeline Flow**

The plugin creation pipeline follows a strict sequence with defined handoff points. See the user flow diagram (02-user-flow.mermaid) for the complete sequence.

**3.1 Phase 1: Conversation & Planning**

The user describes their plugin idea in natural language. The chatbot (running on Haiku) asks targeted clarifying questions to establish the feature set, target Minecraft version, expected server type (Paper/Spigot), and any special requirements. This phase typically consumes 10--15% of the total token budget.

Once the user confirms the feature list, the model upgrades to Sonnet to generate a structured plan document containing: plugin name and description, command definitions with permissions, event listeners and their behavior, configuration file schema, database requirements (if any), test scenarios, and estimated complexity score.

**3.2 Phase 2: Scope Validation**

Before any implementation begins, the scope gating engine evaluates the plan document against the user's tier:

  ------------------------- ---------- ----------- ---------- --------------------
  **Complexity Factor**     **Free**   **Basic**   **Pro**    **Team**

  Commands                  1          5           15         Unlimited

  Event listeners           2          8           25         Unlimited

  Config options            5          20          50         Unlimited

  External dependencies     0          2           5          Unlimited

  Database tables           0          1           3          Unlimited

  Estimated lines of code   \<200      \<800       \<2500     Unlimited
  ------------------------- ---------- ----------- ---------- --------------------

If the plan exceeds the tier's limits, the user is prompted to either simplify the plugin or upgrade their subscription.

**3.3 Phase 3: Implementation**

The approved plan document, along with the versioned plugin template, is injected into a Docker container. The Claude Code agent (Sonnet) generates the plugin code following this cycle:

1.  Code generation from plan document

2.  Maven/Gradle compilation

3.  Static analysis and dependency security scan

4.  Deployment to Paper server in test container

5.  Integration test execution

6.  Error classification and retry (if needed)

7.  JAR extraction and delivery

**3.4 Phase 4: Iteration**

After receiving the JAR, users on paid tiers can request modifications. The iteration loop re-enters the implementation phase with updated requirements, drawing from the remaining token budget. If the budget is exhausted, the user can purchase additional tokens or download the current state as-is.

**4. Model Routing Strategy**

Intelligent model routing is the single most impactful cost optimization. By using the cheapest model capable of handling each task, we reduce costs by 60--70% on non-coding phases. See the model routing diagram (03-model-routing.mermaid).

  ------------------------------- ------------ --------------------------- --------------------------------
  **Task Category**               **Model**    **Approx Cost/1M tokens**   **Rationale**

  Greeting & clarification        Haiku 4.5    \$0.25 in / \$1.25 out      Simple conversational tasks

  Input validation & filtering    Haiku 4.5    \$0.25 in / \$1.25 out      Pattern matching, no reasoning

  Error message classification    Haiku 4.5    \$0.25 in / \$1.25 out      Categorization task

  Complexity estimation           Sonnet 4.5   \$3 in / \$15 out           Requires analytical reasoning

  Plan document generation        Sonnet 4.5   \$3 in / \$15 out           Structured output generation

  Code generation (Claude Code)   Sonnet 4.5   \$3 in / \$15 out           Complex reasoning + code

  Test writing & debugging        Sonnet 4.5   \$3 in / \$15 out           Code comprehension

  Security analysis               Sonnet 4.5   \$3 in / \$15 out           Pattern analysis on code
  ------------------------------- ------------ --------------------------- --------------------------------

The model router makes this decision at each pipeline step, not just once per session. A single build might use Haiku for 6--8 conversational turns, then Sonnet for plan generation, then Sonnet via Claude Code for implementation, then Haiku again for progress notifications.

**5. Token Budget System**

Every build session operates under a hard token budget allocated based on the user's tier. The budget is denominated in normalized token units that account for the different costs of each model. See the token budget lifecycle diagram (04-token-budget-lifecycle.mermaid).

**5.1 Budget Allocation by Tier**

  ------------ ------------------ ------------------------ ------------------------------
  **Tier**     **Budget/Build**   **Approx Real Tokens**   **Budget Distribution**

  Free         50K normalized     \~80K actual             15% plan, 70% code, 15% test

  Basic        200K normalized    \~350K actual            15% plan, 60% code, 25% test

  Pro          500K normalized    \~900K actual            10% plan, 55% code, 35% test

  Team         1M normalized      \~1.8M actual            10% plan, 50% code, 40% test
  ------------ ------------------ ------------------------ ------------------------------

**5.2 Budget Thresholds & Actions**

- **0--79% used:** Normal operation. Agent works freely within the pipeline.

- **80% used (WARNING):** Agent is notified to optimize. It prioritizes completing the current phase, avoids exploratory code paths, and reduces test coverage to critical paths only.

- **95% used (CRITICAL):** Agent enters best-effort mode. It compiles whatever code exists, documents known issues, and prepares the JAR for delivery with a status report.

- **100% used (HARD STOP):** No further API calls. The current artifact (compiled or not) is returned with a detailed report of what was completed and what remains.

**5.3 Budget Awareness in Agents**

Both the chatbot and implementer agents receive their remaining budget as a system prompt parameter, updated at each turn. This means the agent can make intelligent tradeoffs: if 40% of the budget remains but the plugin is 80% done, it knows it has room for thorough testing. If 20% remains and the plugin is 50% done, it shifts to completing core functionality and skipping nice-to-haves.

**6. Container Infrastructure**

Docker containers provide isolation, reproducibility, and security. See the container infrastructure diagram (05-container-infrastructure.mermaid).

**6.1 Base Image Stack**

The base image is built in layers to maximize cache reuse:

1.  Ubuntu 22.04 LTS (stable, well-supported)

2.  JDK 17 + Maven 3.9 + Gradle 8.x

3.  Paper Server (pre-downloaded, target version)

4.  BekoLolek plugin template (versioned per build)

5.  Test framework and common dependencies (pre-resolved)

Pre-resolving Maven/Gradle dependencies in the base image saves 30--60 seconds per build and avoids network calls during the build phase.

**6.2 Warm Pool Strategy**

Instead of cold-starting containers, we maintain a pool of warm containers at a clean snapshot state. When a build is claimed, the container is already running with all dependencies loaded. After the build completes, the container is reset to the snapshot (not destroyed and recreated). This reduces container startup from \~45 seconds to \~3 seconds.

Pool sizing is dynamic based on demand: minimum 2 containers during off-peak, scaling to match the concurrent build limit of active paid users. Team-tier users get dedicated containers that remain warm for the duration of their session.

**6.3 Container Separation**

Each build uses two separate containers:

- **Build Container:** Houses the Claude Code agent, source code, and compilation toolchain. Has limited network access (Maven Central only for dependency resolution).

- **Test Container:** Runs the Paper server with the compiled JAR. Completely network-isolated. Runs integration tests by simulating player actions and verifying expected behavior.

This separation ensures that even if the generated code contains malicious network calls, they cannot execute during testing.

**7. Security Architecture**

Security operates at four levels: input filtering, container sandboxing, code analysis, and output validation.

**7.1 Input Filtering (Prompt Injection Defense)**

All user messages pass through a multi-layer filter before reaching any AI agent:

- **Pattern matching:** Detects known prompt injection patterns (system prompt overrides, role-play directives, instruction injection, encoded payloads).

- **Semantic analysis:** Uses a lightweight classifier (Haiku) to flag messages that seem designed to manipulate agent behavior rather than describe a plugin feature.

- **Context validation:** Ensures messages are consistent with the current pipeline phase.

Flagged messages are quarantined and the user is asked to rephrase. Repeated violations result in session termination.

**7.2 Container Sandboxing**

  ------------------ -------------------------- --------------------------
  **Constraint**     **Build Container**        **Test Container**

  Network            Maven Central only         None

  Filesystem         /plugin-workspace only     /server directory only

  Memory             2GB limit                  4GB limit

  CPU                2 cores                    2 cores

  Execution time     15 min max                 10 min max

  Processes          Limited to build tools     Java only
  ------------------ -------------------------- --------------------------

**7.3 Static Analysis & Security Scan**

Before the compiled JAR enters the test container, it undergoes automated security analysis:

- Dependency audit: All Maven/Gradle dependencies are checked against known vulnerability databases (CVE).

- Code pattern scan: Checks for dangerous patterns (Runtime.exec(), ProcessBuilder, unauthorized reflection, NMS/CraftBukkit internals, network socket creation).

- Obfuscation detection: Any obfuscated or minified code in the output triggers an immediate security flag.

- Permission analysis: The plugin.yml is validated to ensure requested permissions match the plan document scope.

**7.4 Output Validation**

The final JAR is validated before delivery: correct plugin.yml structure, all declared commands and listeners are present, no undeclared dependencies, and the JAR size is within expected bounds for the plugin's complexity.

**7.5 Authentication & Authorization**

The platform uses Discord OAuth2 for user authentication with JWT-based session management:

- **OAuth2 Flow:** Users authenticate via Discord. The authorization URL includes a cryptographically generated `state` parameter (32 bytes from `SecureRandom`, hex-encoded) stored server-side with a 10-minute expiry. The callback endpoint validates the state parameter before exchanging the authorization code, preventing CSRF attacks.

- **JWT Tokens:** Access tokens (15-minute expiry) carry `sub` (user ID), `role` (USER or ADMIN), and `jti` (unique token ID) claims. Refresh tokens (7-day expiry) are stored in the database and support rotation. The signing key is enforced via the `JWT_SECRET` environment variable in production — no default fallback.

- **Role-Based Access Control (RBAC):** The `User` entity includes a `UserRole` enum (USER, ADMIN). The JWT authentication filter extracts the role claim and populates Spring Security's `GrantedAuthority` list. Method-level security is enabled via `@EnableMethodSecurity`. Admin endpoints (`/api/v1/admin/**`, `/actuator/**` except health) require `ROLE_ADMIN`. Resource ownership is enforced at the service layer (e.g., build session access, source code fulfillment, team membership).

- **Refresh Token Hygiene:** A scheduled task runs hourly to purge expired refresh tokens from the database, preventing table bloat. Refresh errors return a generic "Invalid or expired refresh token" message regardless of failure reason to prevent enumeration.

**7.6 API Security**

- **Rate Limiting:** Resilience4j rate limiters protect abuse-prone endpoints. Authentication endpoints (login callback, token refresh) are limited to 10 requests per 60-second window. Chat endpoints (message send) are limited to 30 requests per 60-second window. Exceeded limits return HTTP 429 with a descriptive error.

- **Input Validation:** All DTOs carry Jakarta Bean Validation constraints: `@Size` limits on strings (titles: 200 chars, descriptions: 10,000 chars, chat messages: 10,000 chars, review comments: 5,000 chars), `@Min(0)` on monetary fields, `@NotBlank` on required fields. Controllers use `@Valid` on request bodies. Invalid input returns HTTP 400 with field-level error details.

- **Pagination Caps:** All paginated endpoints enforce a maximum page size of 100 (`Math.min(size, 100)`) to prevent resource exhaustion via oversized queries.

- **Subscription Tier Validation:** Tier values are validated before `Enum.valueOf()` to return a clean HTTP 400 instead of an unhandled exception.

- **Stripe Integration:** Checkout success/cancel URLs use a configurable `app.base-url` property instead of hardcoded origins.

- **Zip Slip Protection:** Source code extraction validates that archive entry paths resolve within the target directory, preventing path traversal attacks.

**7.7 Infrastructure Security**

- **Docker Socket Proxy:** The API server communicates with Docker through a `tecnativa/docker-socket-proxy` sidecar instead of mounting `/var/run/docker.sock` directly. The proxy is configured to allow only container-related operations (`CONTAINERS=1`), blocking access to images, networks, volumes, and other Docker primitives. This limits the blast radius of a compromised API server.

- **No Default Credentials:** All sensitive configuration values (database credentials, Redis password, API keys, JWT secret) are sourced from environment variables with no hardcoded defaults in production. The application refuses to start if required secrets are missing.

- **Redis Authentication:** The Redis instance requires password authentication, configured via the `REDIS_PASSWORD` environment variable.

- **Non-Root Containers:** Build and test container Dockerfiles create and switch to unprivileged users (`builder`, `tester`) before executing workloads. This limits privilege escalation if a generated plugin exploits a container vulnerability.

- **Request Size Limits:** Tomcat is configured with `max-http-form-post-size: 2MB` and `max-swallow-size: 2MB` to reject oversized payloads at the connector level.

**7.8 Frontend Security**

- **Content Security Policy:** The frontend deployment (Vercel) serves strict CSP headers: `default-src 'self'`, `script-src 'self'`, `style-src 'self' 'unsafe-inline'`, `connect-src 'self'` plus the API origin. Additional headers include `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, and a restrictive `Permissions-Policy`.

- **Token Storage:** JWT tokens are stored in `sessionStorage` (not `localStorage`), limiting exposure to XSS — tokens are cleared when the browser tab closes. Only non-sensitive user profile data is persisted in `localStorage` via Zustand.

- **JWT Expiry Checks:** The `isAuthenticated()` function decodes the JWT payload and checks the `exp` claim against the current time. Expired tokens are treated as unauthenticated, forcing re-login instead of sending expired credentials to the API.

- **Redirect URL Validation:** Before performing `window.location.href` redirects to external services (Discord OAuth, Stripe Checkout), the frontend validates that the URL starts with the expected prefix (`https://discord.com/`, `https://checkout.stripe.com/`). Unexpected URLs are blocked and an error is displayed.

- **Error Information Leakage:** The `ErrorBoundary` component hides technical error details (stack traces, error messages) in production builds, showing only a generic "Something went wrong" message.

- **Source Maps:** Production builds disable source map generation (`sourcemap: false` in Vite config) to prevent reverse engineering of the client-side code.

- **Cache Cleanup:** On logout, the React Query cache is cleared to prevent stale authenticated data from being visible to subsequent sessions in the same tab.

**7.9 WebSocket Security**

- **CORS Restriction:** The WebSocket STOMP endpoint restricts allowed origins to the configured `CORS_ALLOWED_ORIGINS` value instead of accepting all origins. This prevents cross-origin WebSocket connections from unauthorized domains.

- **Mandatory Authentication:** The WebSocket handshake interceptor rejects connections that do not include a valid JWT token. Unauthenticated connection attempts receive a `MessageDeliveryException` and are terminated immediately, preventing anonymous subscription to build progress or chat channels.

**8. Error Classification & Fail-Fast Logic**

Errors are classified into three categories with distinct handling strategies. See the error classification diagram (06-error-classification.mermaid).

**8.1 Recoverable Errors**

Common coding mistakes the agent can fix in 1--2 iterations: missing imports, typos, wrong parameter types, null pointer risks, incorrect event handler signatures, and minor logic bugs caught by tests. Maximum 3 retry attempts per error type. If the same category recurs after 3 fixes, it's escalated to structural.

**8.2 Structural Errors**

The approach itself is flawed: incompatible API versions, impossible dependency combinations, architectures that can't work within the Bukkit/Paper framework, or requirements that need external services not in the sandbox. Structural errors trigger an immediate build abort with a detailed failure report.

**8.3 Security Violations**

Unauthorized network calls, filesystem access outside the sandbox, obfuscated code, or matches against known malicious patterns. These trigger an immediate block, the build is terminated, and the account is flagged for review.

**9. Caching Strategy**

Caching reduces token consumption and build time by reusing common patterns across builds.

**9.1 Template Caching**

The plugin template structure and common boilerplate code (main class, plugin.yml generation, config loading, command registration) is cached in Redis. When the implementer agent starts, it receives pre-generated boilerplate rather than generating it from scratch. This saves approximately 5--10K tokens per build.

**9.2 Prompt Caching**

Anthropic's prompt caching feature is used for agent system prompts. Since the system prompt (containing the template structure, coding guidelines, and Minecraft API reference) is identical across builds, caching reduces input token costs by up to 90% for the cached portion. This is the single largest per-build cost reduction.

**9.3 Pattern Library**

Common plugin patterns (command with tab completion, config reload command, database connection pool, scheduled task, GUI menu builder) are stored as verified code snippets. When the plan document matches a known pattern, the implementer starts with the cached implementation. Over time, this library grows as successful builds contribute new patterns.

**9.4 Dependency Resolution Cache**

Maven/Gradle dependency resolution results are cached at the container image level. Common dependency combinations (Paper API + Vault + PlaceholderAPI, etc.) are pre-resolved, eliminating network calls and resolution time during builds.

**10. Deployment Options**

The platform uses a split deployment model: the React/TypeScript frontend is deployed through Vercel via Git integration (zero-config, global CDN), while the backend and build infrastructure scale through a progressive growth path starting at \$0/month. See the deployment options diagram (10-deployment-options.mermaid).

**10.1 Frontend: Vercel (All Phases)**

The React/TypeScript frontend is deployed to Vercel with automatic deployments triggered by Git pushes. This is a constant across all deployment phases --- the frontend never runs on the same infrastructure as the backend.

- **Git integration:** Push to main deploys to production. Push to develop deploys to a preview URL. Every PR gets its own preview deployment for testing.

- **Global CDN:** Vercel's edge network serves the frontend from the closest data center to the user, with automatic caching and invalidation.

- **Free tier (Hobby):** Sufficient for launch. Includes automatic HTTPS, preview deployments, and serverless functions if needed.

- **Pro tier (\$20/mo):** Upgrade when you need team collaboration, analytics, or higher bandwidth. This is a low-priority cost since the free tier handles significant traffic.

The frontend communicates with the backend API via REST (for CRUD) and WebSocket (for real-time build progress). Vercel handles the static assets and client-side rendering; all server-side logic stays on the backend infrastructure.

**10.2 Backend Phase 1: Bootstrap (Oracle Cloud Free Tier)**

**Target:** 0--50 users, pre-revenue

**Cost:** \$0/month

Oracle Cloud's Always Free tier provides an ARM Ampere A1 instance with 4 OCPUs and 24GB RAM --- more powerful than most entry-level paid VPS options. All backend services run on this single instance using Docker Compose: the Spring Boot API, PostgreSQL, Redis, Nginx (reverse proxy + Let's Encrypt SSL), and 2--3 warm build containers.

Java runs natively on ARM, so the Spring Boot API, Maven/Gradle builds, and Paper test servers all work without emulation. The 24GB of RAM provides comfortable headroom for the application stack (\~1GB) plus build containers (\~2--4GB each) plus the database and cache.

Oracle also provides two AMD micro VMs (1 OCPU, 1GB RAM each) which can serve as lightweight monitoring nodes or fallback services, plus 200GB of block storage and 10TB of monthly outbound bandwidth.

**Important caveats:** Convert to a Pay-As-You-Go account immediately after signup (you still get all free resources, but Oracle is less likely to reclaim idle instances). ARM availability varies by region --- choose a European region close to Denmark (Frankfurt or Amsterdam). Test early that all Maven dependencies and Paper server plugins work correctly on ARM.

**10.3 Backend Phase 2: Growth (Hetzner VPS)**

**Target:** 50--200 users, early revenue

**Cost:** \$50--150/month

When Oracle's ARM instance hits capacity limits (CPU sustained \>75%, build queue growing), migrate to a Hetzner CPX31 or CPX41 (4--8 vCPU, 8--16GB RAM, x86 architecture). The Docker Compose stack transfers directly --- same configuration, just a more powerful host. Hetzner's Finland datacenter offers low latency from Denmark.

This phase moves to x86 architecture, eliminating any ARM compatibility concerns. The additional CPU headroom supports 6--10 warm build containers and concurrent paid-tier users. Hetzner Object Storage (S3-compatible) replaces local block storage for JAR artifacts and build logs.

**10.4 Backend Phase 3: Scale (Hetzner Kubernetes)**

**Target:** 200--1000 users, established business

**Cost:** \$200--500/month

Migrate to Hetzner's managed Kubernetes for horizontal scaling. Separate the database to a managed PostgreSQL instance. The API scales to 2--3 replicas behind a load balancer. A dedicated node pool manages build containers (8--16 warm containers). Infrastructure-as-code (Terraform + Helm) ensures reproducible deployments and enables zero-downtime rolling updates.

**10.5 Backend Phase 4: Enterprise (Multi-Node)**

**Target:** 1000+ users

**Cost:** \$800+/month

Multi-node Kubernetes cluster with database read replicas, Redis clustering, CDN for JAR distribution, auto-scaling on both API and build node pools, and multi-region deployment (EU + US). Full observability stack. Consider AWS/GCP migration only if enterprise clients require specific compliance certifications.

**10.6 Deployment Decision Framework**

The decision to upgrade backend infrastructure is driven by the upgrade trigger thresholds defined in Section 15. The guiding principle is: do not over-engineer early, and do not spend money on infrastructure until revenue justifies it. Oracle's free tier should carry the platform through beta and early adoption. The frontend on Vercel scales independently and requires no migration between phases.

**11. Git Branching Strategy**

The project uses a modified Git Flow strategy optimized for a small team that will scale. See the git branching diagram (09-git-branching.mermaid).

**11.1 Branch Types**

  ------------ -------------------- -------------- -----------------------------------------------------------------------------------------
  **Branch**   **Naming**           **Lifetime**   **Purpose**

  main         main                 Permanent      Production-ready code. Every commit is tagged and deployed.

  develop      develop              Permanent      Integration branch. All feature branches merge here first.

  Feature      feature/short-desc   Temporary      Individual features or stories. Created from develop, merged back via PR.

  Release      release/x.y.z        Temporary      Stabilization before production. Created from develop, merged to both main and develop.

  Hotfix       hotfix/short-desc    Temporary      Emergency production fixes. Created from main, merged to both main and develop.
  ------------ -------------------- -------------- -----------------------------------------------------------------------------------------

**11.2 Branch Rules**

1.  **No direct commits to main or develop.** All changes go through pull requests.

2.  **PRs require at least 1 approval** and all CI checks must pass before merge.

3.  **Squash merge feature branches.** Keeps develop history clean. Merge commits for releases and hotfixes.

4.  **Delete branches after merge.** No stale branches. Tags preserve release history.

5.  **Hotfixes always back-merge to develop.** Prevents regression on the next release.

**11.3 Monorepo Structure**

The entire platform lives in a single monorepo with clear module boundaries:

- **/api** --- Spring Boot backend (Java)

- **/web** --- React frontend (TypeScript) --- deployed via Vercel

- **/agents** --- Agent prompts, templates, and configuration

- **/infra** --- Terraform, Helm charts, Docker configs

- **/containers** --- Build and test container Dockerfiles

- **/marketplace** --- Marketplace service (if decoupled later)

- **/shared** --- Shared types, utilities, constants

CI pipelines use path-based triggers so that changes to /web only trigger frontend builds (and Vercel auto-deploys), changes to /api only trigger backend builds, and so on. Vercel is configured with a root directory of /web and auto-detects the React framework for build settings.

**11.4 Commit Convention**

All commits follow Conventional Commits format for automated changelog generation:

- **feat:** New feature (triggers minor version bump)

- **fix:** Bug fix (triggers patch version bump)

- **perf:** Performance improvement

- **refactor:** Code restructuring, no behavior change

- **chore:** Build/CI/dependency updates

- **BREAKING CHANGE:** In footer, triggers major version bump

**12. CI/CD Pipeline**

The CI/CD pipeline uses GitHub Actions for the backend and Vercel's Git integration for the frontend. Backend uses path-based triggers per monorepo module. See the CI/CD pipeline diagram (08-cicd-pipeline.mermaid).

**12.1 Frontend Deployment (Vercel)**

The React/TypeScript frontend deploys automatically through Vercel's Git integration. No GitHub Actions workflow is needed for frontend deployment:

- **Production:** Merges to main auto-deploy to the production URL.

- **Preview:** Every PR and push to develop gets a unique preview URL for testing and review.

- **Rollback:** Instant rollback to any previous deployment through the Vercel dashboard.

Vercel is configured with root directory set to /web in the monorepo. Environment variables (API URL, WebSocket endpoint) are managed per environment in Vercel's project settings.

**12.2 Backend Continuous Integration**

Every pull request triggers the full CI pipeline:

1.  **Lint & Format:** Checkstyle (Java) + ESLint/Prettier (TypeScript). Fails on any violation.

2.  **Unit Tests:** JUnit 5 (backend) + Vitest (frontend). Coverage gate at 80%.

3.  **Integration Tests:** Testcontainers for PostgreSQL, Redis, and Docker-in-Docker build container tests.

4.  **Security Scan:** SAST via Semgrep + dependency audit via OWASP Dependency-Check (Java) and npm audit (frontend).

5.  **Build Artifacts:** Docker images built and pushed to container registry (tagged with commit SHA).

**12.3 Staging Deployment**

Merges to develop automatically deploy to a staging Kubernetes namespace. Staging runs a full end-to-end smoke test: a synthetic user submits a simple plugin idea, the chatbot generates a plan, the implementer builds the JAR, and the system verifies the JAR is downloadable and valid. A load test with simulated concurrent users validates performance under expected load. Manual approval is required to promote to production.

**12.4 Production Deployment**

Production deploys use canary rollouts:

- **Canary (10%):** New version receives 10% of traffic. Metrics are monitored for 5 minutes: error rate, latency p95, build success rate.

- **Progressive rollout:** If healthy, traffic shifts to 25%, then 50%, then 100% with monitoring between each step.

- **Auto-rollback:** If error rate exceeds 1% or latency p95 exceeds 2 seconds during any stage, the deployment is automatically rolled back to the previous version.

**12.5 Hotfix Path**

Critical production issues follow a fast-track path: hotfix branch from main, abbreviated CI (lint + unit tests + security scan only), direct canary deployment to production, then back-merge to develop. Hotfixes skip staging but still go through canary rollout for safety.

**12.6 Infrastructure as Code**

All infrastructure is defined in Terraform (cloud resources) and Helm charts (Kubernetes resources). Changes to /infra trigger a Terraform plan on PR and apply on merge. This ensures infrastructure changes go through the same review and approval process as application code.

**13. Pricing Tiers**

Pricing is designed to cover compute costs at each tier while incentivizing upgrades through meaningful feature differentiation. See the pricing tiers diagram (07-pricing-tiers.mermaid).

  ------------------------ ---------------- --------------------- -------------------- ------------------------
  **Feature**              **Free**         **Basic (\$10/mo)**   **Pro (\$30/mo)**    **Team (\$80/mo)**

  Builds per month         1                5                     20                   Unlimited

  Token budget per build   50K              200K                  500K                 1M

  Parallel builds          No               No                    Up to 5              Up to 20

  Queue priority           Off-peak         Priority              Immediate            Dedicated pool

  Iteration loops          0                2 per build           5 per build          Unlimited

  Testing level            Compile + unit   Full integration      Full + performance   Full + custom configs

  JAR retention            7 days           30 days               90 days              Unlimited

  Marketplace slots        None             1                     5                    Unlimited + featured

  Team features            None             None                  None                 Workspaces, brainstorm
  ------------------------ ---------------- --------------------- -------------------- ------------------------

**13.1 Overage & Add-ons**

Users who exhaust their monthly builds can purchase additional build credits at a per-build rate: \$3 per build for Basic, \$2 for Pro, \$1.50 for Team. Additional token budget can be purchased in 100K increments at \$1 per 100K normalized tokens.

**14. Plugin Marketplace**

The marketplace transforms the platform from a tool into an ecosystem, creating network effects and additional revenue.

**14.1 Revenue Model**

- **Free listings:** Included with paid tiers (slot count varies by tier).

- **Paid plugin sales:** Platform takes a 15% commission on all sales.

- **Featured placement:** \$5/month per plugin for homepage featuring.

- **Verified badge:** Plugins that pass enhanced security review get a verified badge (free for Pro/Team, \$2/plugin for Basic).

**14.2 Quality Control**

All marketplace listings undergo automated quality checks: the plugin must compile, pass integration tests, have a complete plugin.yml, include a README, and pass security scanning. User reviews and ratings provide ongoing quality signals. Plugins below a 3.0 rating after 10+ reviews are flagged for review.

**14.3 Version Management**

The marketplace supports semantic versioning. Users can publish updates through the same build pipeline, and server administrators can subscribe to update notifications.

**15. Infrastructure Upgrade Triggers**

Infrastructure upgrades are driven by measurable thresholds, not guesswork. Each metric has three alert levels: Warning (monitor closely), Plan Upgrade (begin planning, 2--4 week horizon), and Upgrade Now (immediate action required). See the upgrade triggers diagram (11-upgrade-triggers.mermaid).

**15.1 Compute Thresholds**

  --------------------------------- ---------------- --------------------- ---------------------
  **Metric**                        **⚠️ Warning**   **🟠 Plan Upgrade**   **🔴 Upgrade Now**

  CPU utilization (sustained 1hr)   \>60%            \>75%                 \>90%

  Memory usage (average)            \>70%            \>80%                 \>90% / OOM kills

  API response time (p95)           \>500ms          \>800ms               \>2000ms / timeouts
  --------------------------------- ---------------- --------------------- ---------------------

**15.2 Capacity Thresholds**

  --------------------------- -------------------- --------------------- ---------------------------
  **Metric**                  **⚠️ Warning**       **🟠 Plan Upgrade**   **🔴 Upgrade Now**

  Build queue depth           \>5 builds waiting   \>10 builds waiting   \>25 builds / 30min+ wait

  Container pool exhaustion   \>10% of requests    \>25% of requests     \>50% of requests

  DB connection pool usage    \>60% of pool        \>75% of pool         \>90% / deadlocks

  Storage usage               \>60% capacity       \>75% capacity        \>90% capacity
  --------------------------- -------------------- --------------------- ---------------------------

**15.3 Business Thresholds**

  --------------------------- ---------------- --------------------- --------------------
  **Metric**                  **⚠️ Warning**   **🟠 Plan Upgrade**   **🔴 Upgrade Now**

  Daily active users          50+              100+                  200+

  Monthly builds              500+             1000+                 2000+

  Error rate (all requests)   \>0.5%           \>1%                  \>3%
  --------------------------- ---------------- --------------------- --------------------

**15.4 Upgrade Decision Matrix**

When thresholds are hit, the action depends on which deployment phase you're currently on:

  -------------------- -------------------------------- --------------------------------- ---------------------------------------------
  **Current Phase**    **Warning Action**               **Plan Upgrade Action**           **Upgrade Now Action**

  Bootstrap (VPS)      Optimize queries, add caching    Begin K8s migration planning      Emergency: add second VPS or fast-track K8s

  Growth (K8s)         Scale replicas, tune resources   Plan multi-node cluster           Auto-scale aggressively, add nodes

  Scale (Multi-node)   Fine-tune auto-scaling           Evaluate cloud-native migration   Add regions, increase node count

  Enterprise (Cloud)   Adjust auto-scaling policies     Add regions or services           Incident response, scale everything
  -------------------- -------------------------------- --------------------------------- ---------------------------------------------

**15.5 Monitoring & Alerting Setup**

All thresholds are configured as Prometheus alerting rules with Grafana dashboards for visualization. Alerts route through a Discord bot to a dedicated server:

- **Warning:** Posts to #infra-alerts channel. No immediate action required.

- **Plan Upgrade:** Posts to #infra-alerts with \@here mention. Creates a thread for planning discussion.

- **Upgrade Now:** Posts to #infra-critical with \@everyone mention and DMs the admin. Requires acknowledgment reaction within 15 minutes or repeats the alert.

**16. Intellectual Property & Source Code Policy**

The platform's business model depends on users paying for plugin builds rather than paying once and iterating independently. The IP and source code policy is designed to protect this model while being fair and transparent to users.

**16.1 Ownership Model**

Plugins generated through the platform are licensed, not sold. The Terms of Service establish the following ownership structure:

- **Platform retains copyright on generated source code.** The AI-generated Java source, build configurations, and test files are the intellectual property of BekoLolek Plugin Factory. This is similar to how SaaS platforms retain ownership of their output formats.

- **Users receive a perpetual, non-exclusive license to use and share the compiled JAR.** This license permits deploying the JAR on any Minecraft server, sharing it with other server administrators, and distributing it freely to anyone. The JAR can be used and shared indefinitely, even after subscription cancellation. This applies to all tiers including Free.

- **No commercial exploitation of JARs or source.** Users may not sell the compiled JAR, charge for access to it, bundle it with paid products, or monetize it in any way outside the platform's marketplace. The sole exception is listing it on the BekoLolek Plugin Factory marketplace, where the platform handles pricing and commission.

- **Marketplace is the only sales channel.** Users who want to sell their plugins must do so through the platform's marketplace. This ensures quality control, security scanning, and fair revenue sharing. Selling or charging for plugins outside the marketplace (on SpigotMC, Polymart, BuiltByBit, or any other platform) is a license violation.

**16.2 Source Code Storage**

Every build stores the complete source tree (Java files, pom.xml/build.gradle, test files, resources) as a SourceBundle in S3-compatible object storage alongside the compiled JAR. Source bundles are never exposed through the standard API or UI. They exist for three purposes:

- **Iteration loops:** The implementation agent needs the existing source code to make modifications during iteration. Without stored source, every iteration would start from scratch.

- **Debugging and support:** When a user reports a plugin issue, support can inspect the generated source to diagnose problems without re-running the build.

- **Audit trail:** Source bundles provide a complete record of what was generated, useful for security reviews and dispute resolution.

Source bundles follow the same retention policy as artifacts: 7 days (Free), 30 days (Basic), 90 days (Pro), unlimited (Team). After expiration, both the JAR and source are permanently deleted from storage.

**16.3 Source Code Request Process**

Users may request access to their plugin's source code through the dashboard. Source code requests are subject to the following conditions:

- **Availability:** Pro and Team tiers only. Basic and Free tier users must upgrade to request source code.

- **License agreement:** Before receiving the source, the user must digitally accept the Source Code License Agreement (see 16.4). This is a click-through agreement recorded with timestamp and IP.

- **Delivery:** Source is delivered as a ZIP archive containing the complete Maven/Gradle project structure, ready to open in any Java IDE.

- **Watermarking:** Source files include a header comment with the build session ID, user ID, license type, and generation timestamp. This enables tracing if the code appears in unauthorized contexts.

- **One-time fee (optional):** Consider charging a per-request fee (\$5--10) to add friction and offset the lost future-build revenue. This is a business decision that can be adjusted based on user behavior data.

**16.4 Source Code License Terms**

The Source Code License Agreement grants the user a restricted license with the following terms:

- **Personal and non-commercial use only.** The source code may be modified and compiled for personal use. The resulting JAR may be shared freely (consistent with the JAR license), but the source code itself may not be used to create commercial products or generate revenue.

- **No source redistribution.** The source code may not be shared, published, uploaded to public repositories, or transferred to any third party in any form (original or modified). Only the compiled JAR may be distributed.

- **No commercial exploitation.** Neither the source code nor any compiled derivatives may be sold, licensed for a fee, bundled with paid products, or monetized in any way outside the BekoLolek Plugin Factory marketplace.

- **No marketplace bypass.** Plugins derived from the source code may not be listed on any external marketplace or distribution platform. If the user wants to sell their plugin, it must go through BekoLolek Plugin Factory's marketplace.

- **Attribution required.** Any compiled plugin derived from the source must retain the "Generated by BekoLolek Plugin Factory" attribution in the plugin.yml description.

- **License survives subscription.** The license terms persist even after the user cancels their subscription. If the user's subscription lapses, they retain the right to use existing source under these terms but cannot request new source code.

**16.5 Enforcement**

Enforcement focuses on preventing commercial exploitation and source code leaks, not on restricting JAR sharing (which is explicitly permitted):

- **Source watermarking:** Every source file contains a traceable header. Automated scanning of public repositories (GitHub, GitLab, Bitbucket) can detect leaked or redistributed source code.

- **DMCA takedowns:** If watermarked source code is found on public repositories or competing platforms, a DMCA takedown request is filed. The watermark provides clear evidence of origin.

- **Commercial sales monitoring:** Automated monitoring of SpigotMC, Polymart, BuiltByBit, and other Minecraft plugin marketplaces for plugins that match the platform's output signatures (plugin.yml attribution, code patterns, dependency fingerprints). Plugins being sold outside the platform trigger an investigation.

- **Account termination:** Users who sell plugins outside the marketplace or redistribute source code face immediate account suspension and permanent ban from the platform.

**16.6 Future Considerations**

As the platform matures, the IP model may evolve:

- An "Open Source" build option could generate plugins under MIT or Apache 2.0 license for a premium fee, granting the user full ownership and redistribution rights.

- Enterprise licensing could grant organizations full IP ownership of plugins built under their Team subscription.

- A "Buyout" option could let users purchase full IP rights to a specific plugin for a one-time fee.

These options create additional revenue streams while preserving the default protective model for the majority of users.

**17. Frontend Architecture & Technologies**

The frontend is a single-page application deployed on Vercel with Git-based continuous deployment. It serves as the primary user interface for plugin creation, build monitoring, and marketplace access.

**17.1 Core Stack**

  --------------------- ------------- ----------------------------------------------------------------
  **Technology**        **Version**   **Purpose**

  React                 18.x          UI framework with concurrent rendering

  TypeScript            5.x           Type safety across the entire frontend

  Vite                  5.x           Build tool and dev server (fast HMR)

  React Router          6.x           Client-side routing and navigation

  TanStack Query        5.x           Server state management, caching, optimistic updates

  Zustand               4.x           Client state management (lightweight, no boilerplate)

  Tailwind CSS          3.x           Utility-first styling

  shadcn/ui             latest        Accessible component primitives (not a dependency, copy-paste)
  --------------------- ------------- ----------------------------------------------------------------

**17.2 Communication & Real-Time**

  ------------------------------------- -----------------------------------------------------------
  **Technology**                        **Purpose**

  Axios                                 HTTP client for REST API calls with interceptors for auth

  Socket.IO Client / native WebSocket   Real-time build progress, chat streaming, status updates

  React Hook Form + Zod                 Form handling with schema-based validation
  ------------------------------------- -----------------------------------------------------------

**17.3 Developer Tooling**

  ------------------------- -----------------------------------------------------------
  **Tool**                  **Purpose**

  ESLint + Prettier         Code linting and formatting (enforced in CI)

  Vitest                    Unit and component testing (Jest-compatible, Vite-native)

  Playwright                End-to-end testing

  Storybook                 Component development and visual testing

  Husky + lint-staged       Pre-commit hooks for lint/format enforcement
  ------------------------- -----------------------------------------------------------

**17.4 Key Frontend Features**

- **Chatbot interface:** Streaming message display with markdown rendering, code highlighting, and typing indicators. Uses WebSocket for real-time agent responses.

- **Build dashboard:** Real-time build progress with phase indicators (planning, compiling, testing), token budget visualization, and live log streaming.

- **Marketplace:** Plugin discovery with search, filtering, categories, ratings, and version history. Plugin detail pages with README rendering and download tracking.

- **Responsive design:** Mobile-first layout using Tailwind breakpoints. Core chatbot and build monitoring work on mobile; marketplace and admin features optimized for desktop.

**18. Backend Architecture & Technologies**

The backend is a Java 17 Spring Boot 3.x monolith following a modular package structure that can be decomposed into microservices later if needed. It handles API requests, orchestrates AI agents, manages build pipelines, and serves the marketplace.

**18.1 Core Stack**

  --------------------- ------------- -----------------------------------------------------------------
  **Technology**        **Version**   **Purpose**

  Java                  17 (LTS)      Primary language, matches Minecraft plugin ecosystem

  Spring Boot           3.x           Application framework, auto-configuration, dependency injection

  Spring Web MVC        6.x           REST API controllers and request handling

  Spring WebSocket      6.x           Real-time communication for build progress and chat

  Spring Security       6.x           Authentication, authorization, JWT token handling

  Spring Data JPA       3.x           Database access with Hibernate ORM

  Spring Data Redis     3.x           Redis cache integration

  Spring Validation     3.x           Request body and path parameter validation

  Flyway                9.x           Database schema migrations (version-controlled SQL)

  MapStruct             1.5.x         Type-safe DTO-to-entity mapping (compile-time generated)

  Lombok                1.18.x        Boilerplate reduction (getters, builders, constructors)
  --------------------- ------------- -----------------------------------------------------------------

**18.2 Infrastructure Libraries**

  ---------------------------------- ----------------------------------------------------------------
  **Technology**                     **Purpose**

  Anthropic Java SDK                 Claude API integration for Haiku/Sonnet model calls

  Docker Java Client (docker-java)   Container lifecycle management (create, start, stop, snapshot)

  Stripe Java SDK                    Subscription management, payment processing, webhook handling

  MinIO Java SDK                     S3-compatible object storage for JAR artifacts and logs

  Jackson                            JSON serialization/deserialization with JSONB support

  OkHttp / RestTemplate              HTTP client for external service calls

  Caffeine                           In-process caching (L1 cache before Redis)

  Resilience4j                       Circuit breaker, retry, rate limiter for external calls
  ---------------------------------- ----------------------------------------------------------------

**18.3 Testing Stack**

  ------------------------- ------------------------------------------------------------------------
  **Technology**            **Purpose**

  JUnit 5                   Unit and integration test framework

  Mockito                   Mocking framework for unit tests

  Testcontainers            Disposable Docker containers for integration tests (PostgreSQL, Redis)

  AssertJ                   Fluent assertion library

  WireMock                  HTTP mock server for external API testing (Anthropic, Stripe)

  JaCoCo                    Code coverage reporting (80% gate in CI)

  ArchUnit                  Architecture rule enforcement (package dependencies, layer access)
  ------------------------- ------------------------------------------------------------------------

**18.4 General Design Principles**

The backend follows a set of design principles that guide every implementation decision. These principles prioritize maintainability, testability, and cost-consciousness.

**Principle 1: Layered Architecture with Strict Boundaries**

The application uses a four-layer architecture: Controller → Service → Repository → Domain. Each layer only depends on the layer below it, never sideways or upward. Controllers handle HTTP concerns (request/response mapping, validation). Services contain business logic and orchestration. Repositories handle persistence. Domain objects are plain Java objects with no framework annotations.

Implementation: ArchUnit rules enforce these boundaries in CI. A controller importing a repository class directly will fail the build. DTOs exist at the controller layer and are mapped to domain objects via MapStruct --- domain objects never leak into API responses.

**Principle 2: Domain-Driven Package Structure**

Packages are organized by business domain, not by technical layer. This means all code related to builds lives under com.bekololek.pluginfactory.build (controllers, services, repositories, domain, DTOs), not scattered across com.bekololek.controller, com.bekololek.service, etc.

Implementation: Top-level packages are: auth, user, subscription, build, plan, agent, container, marketplace, team, and common. Each package is self-contained and could theoretically be extracted into a separate module or microservice.

**Principle 3: Event-Driven Internal Communication**

Modules communicate through Spring Application Events rather than direct service-to-service calls where possible. When a build completes, it publishes a BuildCompletedEvent. The marketplace module listens for this event to update listing availability. The analytics module listens to record metrics. This keeps modules decoupled and makes the system easier to extend.

Implementation: Spring's \@EventListener and \@TransactionalEventListener annotations. Events are published via ApplicationEventPublisher. For the MVP, events are in-process. When scaling requires it, events can be migrated to Redis Pub/Sub or a message queue without changing the publishing code.

**Principle 4: Fail-Safe External Integrations**

Every external service call (Anthropic API, Stripe, Docker daemon, S3) is wrapped in a Resilience4j circuit breaker with configurable retry policies and fallback behavior. The system must degrade gracefully: if the Anthropic API is down, the chatbot returns a friendly error rather than crashing. If Stripe is unreachable, subscription checks fall back to cached tier data.

Implementation: Each external integration has a dedicated client class annotated with \@CircuitBreaker and \@Retry. Timeouts are explicit (no unbounded waits). HTTP clients use connection pools with max-connection limits. All external calls are logged with correlation IDs for debugging.

**Principle 5: Cost-Aware by Default**

Every AI API call includes the token budget context. The token budget is not an afterthought --- it's a first-class parameter in every agent interaction. Services that call the Anthropic API must accept a TokenBudget parameter and check the remaining budget before making the call. The model router considers both task requirements and remaining budget when selecting a model.

Implementation: A TokenBudgetService acts as a central ledger. Before any API call, the calling service requests a "reservation" of estimated tokens. If the reservation exceeds the remaining budget, the call is either downgraded (use a cheaper model), truncated (reduce max_tokens), or rejected (budget exhausted). After the call, actual usage is recorded and the reservation is settled.

**Principle 6: Idempotent Operations**

All state-changing operations are designed to be safely retried. Build iterations have unique IDs. Stripe webhooks are deduplicated by event ID. Container operations use idempotency keys. This protects against network failures, duplicate webhook deliveries, and retry storms.

Implementation: Database operations use INSERT ON CONFLICT for upserts. Stripe webhook handler checks a processed_events table before acting. All API endpoints that modify state return the same result if called twice with the same parameters.

**Principle 7: Observable by Default**

Every significant operation emits structured logs and metrics. Build sessions, API calls, token consumption, container lifecycle events, and error classifications are all tracked. This is not optional instrumentation added later --- it's part of the initial implementation of every feature.

Implementation: SLF4J with structured JSON logging (Logback). Micrometer for Prometheus-compatible metrics. Custom annotations (@Timed, \@Counted) on service methods. A correlation ID propagates through the entire request lifecycle (HTTP → WebSocket → agent → container).

**Principle 8: Security in Depth**

Security is not a single layer --- it's applied at every boundary. Input validation at the controller. Authorization checks at the service. Prompt injection filtering before the agent. Container sandboxing during the build. Static analysis on the output. Output validation before delivery. Each layer assumes the previous one might have been bypassed.

Implementation: Spring Security filters for authentication/authorization. A dedicated PromptSanitizer service for AI input. Container security profiles (seccomp, AppArmor) defined in Docker configs. A SecurityScanService runs on every artifact before it's stored.

**19. Conceptual Domain Model**

The domain model represents the core business entities and their relationships. See the domain model diagram (12-domain-model.mermaid) for the full entity-relationship view. Below is a summary of the key aggregates and their responsibilities.

**19.1 User Aggregate**

The User entity is the root of identity and access. Each user has exactly one active subscription (including free tier), zero or more API keys for programmatic access, and optional team membership. Users authenticate via OAuth providers (initially Discord OAuth2, expandable to GitHub and Google). The user's Discord ID is stored for community integration and alert notifications.

**19.2 Subscription & Tier**

Subscription tracks the user's current plan status, billing cycle, and usage within the current period. Tier is a reference entity that defines the limits and capabilities of each plan (Free, Basic, Pro, Team). The separation means tier definitions can be updated independently of active subscriptions. Usage records track build count, token consumption, and parallel build slots consumed per billing period.

**19.3 Build Session Aggregate**

BuildSession is the central aggregate for the plugin creation pipeline. It owns the entire lifecycle from initial chat through JAR delivery. A session contains: a sequence of ChatMessages (the conversation history), exactly one PlanDocument (generated from the conversation), a TokenBudget (the resource ledger), and one or more BuildIterations (each attempt to compile and test the plugin).

The session tracks its current phase (chatting, planning, approved, building, testing, completed, failed) and enforces state machine transitions --- you cannot start building without an approved plan, and you cannot iterate without a completed first build.

**19.4 Plan Document**

PlanDocument is a versioned specification of the plugin to be built. It stores structured data: command definitions (name, permission, arguments, tab completion), event listener specifications (event type, behavior, conditions), configuration schema (keys, types, defaults), dependency list, and test scenarios. The document is stored as JSONB in PostgreSQL for flexible querying. Each user modification or AI refinement increments the version number, preserving history.

A ComplexityScore is computed from the plan document and compared against the user's tier limits during scope gating. The score is a weighted sum of command count, event listener count, estimated lines of code, and dependency complexity.

**19.5 Build Iteration & Container Session**

Each BuildIteration represents a single attempt to generate, compile, and test the plugin. Iterations are numbered sequentially within a session. Each iteration creates a ContainerSession --- a record of the Docker container used, its resource allocation, and lifecycle timestamps. Build errors are classified and stored with their category (recoverable, structural, security), retry count, and stack trace. When an iteration succeeds, it produces an Artifact.

**19.5.1 Build Pipeline Execution Model**

The build pipeline runs asynchronously off the HTTP request thread. When a user approves a plan (`POST /builds/{sessionId}/plan/approve`) or requests an iteration (`POST /builds/{sessionId}/iterations`), the controller delegates to a thin `BuildLauncher` service. The launcher synchronously creates the `BuildIteration` row (so the API can return a real, persisted iteration immediately) and then calls `BuildPipelineService.executeBuild(sessionId, iterationId)`, which is annotated `@Async` and runs on a dedicated `buildPipelineExecutor` thread pool (core 2 / max 4 / queue 16, graceful shutdown with 120s drain). The launcher and the pipeline live in separate Spring beans on purpose: Spring's async proxy only fires when the call crosses a bean boundary, so a self-invocation from inside `BuildPipelineService` would silently run synchronously and block the HTTP thread. The controllers respond `202 Accepted` and clients poll session status or subscribe to the WebSocket progress stream.

The pipeline body is wrapped in a top-level `try/catch (Throwable)` that marks the session `FAILED` on any unhandled error. This is defense-in-depth so an async worker exception can never strand a session in `BUILDING`.

**19.5.2 Build Recovery & Stuck-Build Reaping**

Two complementary mechanisms guarantee that sessions never get permanently stuck in a transient state:

- **Restart recovery.** `BuildRecoveryService` listens for Spring's `ApplicationReadyEvent`. On boot it sweeps any session whose status is `PLANNING`, `APPROVED`, `BUILDING`, or `TESTING` --- these were mid-flight when the previous JVM died --- and transitions them to `FAILED` with a chat message explaining the restart, while also releasing any open `ContainerSession` rows.
- **Stale-build reaper.** A `@Scheduled(fixedDelay = 2 minutes)` job inside the same service queries `findByStatusInAndUpdatedAtBefore(transientStatuses, now - 15 minutes)` and recovers any session whose `updatedAt` heartbeat has gone cold. Hibernate's `@PreUpdate` on `BaseEntity.updatedAt` provides the heartbeat for free --- every status, phase, or progress write naturally bumps the timestamp, so no schema migration was needed. This catches workers that wedged on a Docker call, an Anthropic API hang, or any other silent failure mode that didn't throw.

Both paths share a single `recoverSession(session, now, reason)` helper so the recovery logic --- update status, append a chat message, release containers --- is identical regardless of trigger.

**19.6 Artifact, Source Bundle & Marketplace Listing**

Artifact represents a compiled JAR file with its metadata: file hash (SHA-256 for integrity verification), file size, plugin.yml contents, and security scan results. Artifacts are stored in S3-compatible object storage with the file path recorded in the database.

Every Artifact has an associated SourceBundle --- the complete Java source tree, build configuration (pom.xml or build.gradle), and generated test files. Source bundles are stored in S3 alongside the JAR but are never exposed to users by default. They serve three internal purposes: enabling iteration loops (the agent needs the source to make changes), supporting debugging and customer support, and providing an audit trail of what was generated. Source bundles follow the same retention policy as their parent artifact.

Users may request access to their plugin's source code, but it is delivered under a restrictive license (see Section 16: Intellectual Property & Source Code Policy). A SourceCodeRequest entity tracks these requests, their approval status, and the license version the user agreed to.

A MarketplaceListing is an optional wrapper around an Artifact that makes it publicly discoverable. Listings have their own lifecycle (draft, published, suspended, archived) independent of the artifact. Listings accumulate reviews, ratings, and purchase records. Version management links multiple artifacts to the same listing as ArtifactVersions.

**19.7 Token Budget & Budget Events**

TokenBudget is a ledger that tracks token allocation and consumption for a build session. It maintains separate counters for planning, implementation, and testing phases. BudgetEvents log every reservation, consumption, and threshold crossing, providing a full audit trail of how tokens were spent. The threshold status field tracks whether the session is in normal, warning (80%), critical (95%), or exhausted (100%) state.

**19.8 Team & Shared Workspace**

Team is a grouping entity for the Team tier. A team has an owner, members, and one or more SharedWorkspaces. Workspaces provide shared build history and allow team members to view, iterate on, or fork each other's build sessions. Team-level analytics aggregate build metrics, token consumption, and cost across all members.

**20. Feature List**

Comprehensive feature inventory organized by functional area. Features marked with tier indicators show the minimum tier required.

**20.1 Plugin Creation**

- **Natural language plugin description** \[All tiers\] --- Describe your plugin idea in plain English. The chatbot guides you through refinement.

- **AI-powered clarification** \[All tiers\] --- The chatbot asks targeted questions about commands, events, config options, and target Minecraft version.

- **Plan document generation** \[All tiers\] --- Structured specification document with commands, events, config schema, and test scenarios.

- **Plan review and approval** \[All tiers\] --- Review the generated plan before any compute is spent. Modify or approve.

- **Automated code generation** \[All tiers\] --- Claude Code agent writes Java plugin code from the approved plan.

- **Automated compilation** \[All tiers\] --- Maven/Gradle compilation in an isolated Docker container.

- **Unit test generation** \[All tiers\] --- Agent generates and runs unit tests for the plugin.

- **Integration testing** \[Basic+\] --- Plugin deployed to a live Paper server container for behavioral testing.

- **Performance testing** \[Pro+\] --- Load simulation to detect performance bottlenecks.

- **Iteration loops** \[Basic+\] --- Request modifications after receiving the JAR (2 for Basic, 5 for Pro, unlimited for Team).

- **JAR download** \[All tiers\] --- Download the compiled JAR ready for deployment to your server.

- **Source code request** \[Pro+\] --- Request the full Java source project (ZIP) under a restrictive personal-use license. Watermarked with session ID for traceability.

**20.2 Build Management**

- **Build history dashboard** \[All tiers\] --- View all past builds, their status, plan documents, and artifacts.

- **Real-time build progress** \[All tiers\] --- WebSocket-powered live updates showing current phase, progress percentage, and log streaming.

- **Token budget visualization** \[All tiers\] --- See how much of your token budget has been consumed and by which phase.

- **Parallel builds** \[Pro+\] --- Run multiple plugin builds simultaneously (up to 5 for Pro, 20 for Team).

- **Build queue status** \[All tiers\] --- See your position in the build queue and estimated wait time.

- **JAR retention** \[All tiers\] --- Artifacts retained based on tier (7 days Free, 30 Basic, 90 Pro, unlimited Team).

**20.3 Marketplace**

Note: Users can always share their plugins for free with anyone (directly sending the JAR). The marketplace is specifically for discovery, distribution, and monetization.

- **Plugin discovery** \[All tiers\] --- Search, browse by category, filter by Minecraft version, sort by rating/downloads.

- **Plugin listing** \[Basic+\] --- Publish your plugins to the marketplace (1 slot Basic, 5 Pro, unlimited Team).

- **Free and paid plugins** \[Basic+\] --- List plugins as free or set a price. Platform takes 15% commission on sales.

- **Version management** \[Pro+\] --- Publish updates with semantic versioning. Users get update notifications.

- **Reviews and ratings** \[All tiers\] --- Rate and review plugins you've used.

- **Verified badge** \[Basic+\] --- Enhanced security review for trusted status (free for Pro/Team, \$2 for Basic).

- **Featured placement** \[Team / paid add-on\] --- Homepage and category page featuring (\$5/month per plugin).

**20.4 Account & Subscription**

- **OAuth authentication** \[All tiers\] --- Sign in with Discord (primary), GitHub, or Google.

- **Subscription management** \[All tiers\] --- View current plan, upgrade/downgrade, manage payment method via Stripe.

- **Usage dashboard** \[All tiers\] --- Monthly build count, token consumption, and spending breakdown.

- **Overage purchasing** \[Basic+\] --- Buy additional build credits or token budget when monthly allocation is exhausted.

- **API key management** \[Pro+\] --- Generate API keys for programmatic access to the build pipeline.

**20.5 Team Collaboration**

- **Team creation and management** \[Team\] --- Create a team, invite members, assign roles.

- **Shared workspaces** \[Team\] --- Shared build history visible to all team members.

- **AI brainstorm sessions** \[Team\] --- Multi-user planning sessions with AI facilitation.

- **Plugin discussion boards** \[Team\] --- Internal discussion threads attached to builds and plugins.

- **Team analytics** \[Team\] --- Aggregate build metrics, token usage, and cost across the team.

- **Dedicated container pool** \[Team\] --- Warm containers reserved for your team, no queue wait.

**21. API Endpoints**

All endpoints are prefixed with /api/v1. Authentication is via Bearer JWT token in the Authorization header unless marked as public. WebSocket endpoints use the same JWT for the initial handshake.

**21.1 Authentication**

  ------------ ------------------------ ----------------------------------------- --------------
  **Method**   **Endpoint**             **Description**                           **Auth**

  GET          /auth/discord            Initiate Discord OAuth2 flow              Public

  GET          /auth/discord/callback   Handle Discord OAuth2 callback            Public

  POST         /auth/refresh            Refresh expired JWT using refresh token   Public

  POST         /auth/logout             Invalidate refresh token                  JWT
  ------------ ------------------------ ----------------------------------------- --------------

**21.2 Users**

  ------------ ---------------------------- ------------------------------------------- --------------
  **Method**   **Endpoint**                 **Description**                             **Auth**

  GET          /users/me                    Get current user profile and subscription   JWT

  PATCH        /users/me                    Update display name or preferences          JWT

  GET          /users/me/usage              Get current period usage stats              JWT

  GET          /users/me/api-keys           List active API keys                        JWT

  POST         /users/me/api-keys           Generate new API key                        JWT

  DELETE       /users/me/api-keys/{keyId}   Revoke an API key                           JWT
  ------------ ---------------------------- ------------------------------------------- --------------

**21.3 Subscriptions**

  ------------ ------------------------- -------------------------------------------- --------------
  **Method**   **Endpoint**              **Description**                              **Auth**

  GET          /subscriptions/tiers      List all available tiers and pricing         Public

  GET          /subscriptions/current    Get current subscription details             JWT

  POST         /subscriptions/checkout   Create Stripe checkout session for upgrade   JWT

  POST         /subscriptions/portal     Create Stripe customer portal session        JWT

  POST         /webhooks/stripe          Handle Stripe webhook events                 Stripe sig
  ------------ ------------------------- -------------------------------------------- --------------

**21.4 Build Sessions**

  ------------ ---------------------------- ---------------------------------------- --------------
  **Method**   **Endpoint**                 **Description**                          **Auth**

  POST         /builds                      Create a new build session               JWT

  GET          /builds                      List user's build sessions (paginated)   JWT

  GET          /builds/{sessionId}          Get build session details and status     JWT

  DELETE       /builds/{sessionId}          Cancel an active build session           JWT

  GET          /builds/{sessionId}/logs     Stream build logs (SSE)                  JWT

  GET          /builds/{sessionId}/budget   Get token budget status                  JWT
  ------------ ---------------------------- ---------------------------------------- --------------

**21.5 Chat & Planning**

  ------------ ---------------------------------- ------------------------------------- --------------
  **Method**   **Endpoint**                       **Description**                       **Auth**

  POST         /builds/{sessionId}/messages       Send a chat message to the agent      JWT

  GET          /builds/{sessionId}/messages       Get chat history for a session        JWT

  GET          /builds/{sessionId}/plan           Get the current plan document         JWT

  POST         /builds/{sessionId}/plan/approve   Approve the plan and start building   JWT

  POST         /builds/{sessionId}/plan/revise    Request plan revision with feedback   JWT
  ------------ ---------------------------------- ------------------------------------- --------------

**21.6 Iterations & Artifacts**

  ------------ ----------------------------------------- ------------------------------------------------- --------------
  **Method**   **Endpoint**                              **Description**                                   **Auth**

  GET          /builds/{sessionId}/iterations            List all iterations for a session                 JWT

  POST         /builds/{sessionId}/iterate               Request a new iteration with changes              JWT

  GET          /builds/{sessionId}/artifacts             List artifacts produced by the session            JWT

  GET          /artifacts/{artifactId}/download          Download JAR file                                 JWT

  GET          /artifacts/{artifactId}/security          Get security scan results                         JWT

  POST         /artifacts/{artifactId}/source-request    Request source code (accepts license agreement)   JWT (Pro+)

  GET          /artifacts/{artifactId}/source-request    Check source request status                       JWT (Pro+)

  GET          /artifacts/{artifactId}/source-download   Download watermarked source ZIP                   JWT (Pro+)
  ------------ ----------------------------------------- ------------------------------------------------- --------------

**21.7 Marketplace**

  ------------ ------------------------------------------- ----------------------------------------------- --------------
  **Method**   **Endpoint**                                **Description**                                 **Auth**

  GET          /marketplace/plugins                        Browse/search plugins (paginated, filterable)   Public

  GET          /marketplace/plugins/{listingId}            Get plugin listing detail                       Public

  POST         /marketplace/plugins                        Create a new marketplace listing                JWT

  PATCH        /marketplace/plugins/{listingId}            Update listing metadata                         JWT

  DELETE       /marketplace/plugins/{listingId}            Unpublish a listing                             JWT

  POST         /marketplace/plugins/{listingId}/versions   Publish a new version                           JWT

  GET          /marketplace/plugins/{listingId}/reviews    Get reviews for a listing                       Public

  POST         /marketplace/plugins/{listingId}/reviews    Submit a review                                 JWT

  POST         /marketplace/plugins/{listingId}/purchase   Purchase a paid plugin                          JWT

  GET          /marketplace/my-listings                    List current user's marketplace listings        JWT

  GET          /marketplace/my-purchases                   List current user's purchased plugins           JWT
  ------------ ------------------------------------------- ----------------------------------------------- --------------

**21.8 Teams**

  ------------ ---------------------------------- ------------------------------- -----------------
  **Method**   **Endpoint**                       **Description**                 **Auth**

  POST         /teams                             Create a new team               JWT (Team tier)

  GET          /teams/{teamId}                    Get team details and members    JWT

  POST         /teams/{teamId}/members            Invite a member to the team     JWT

  DELETE       /teams/{teamId}/members/{userId}   Remove a member from the team   JWT

  GET          /teams/{teamId}/workspaces         List shared workspaces          JWT

  GET          /teams/{teamId}/analytics          Get team-wide build analytics   JWT
  ------------ ---------------------------------- ------------------------------- -----------------

**21.9 WebSocket Endpoints**

  --------------------------------------- ----------------- --------------------------------------------------------------------
  **Endpoint**                            **Direction**     **Description**

  ws://api/v1/builds/{sessionId}/stream   Server → Client   Real-time build progress updates (phase changes, percentage, logs)

  ws://api/v1/builds/{sessionId}/chat     Bidirectional     Real-time chat with the AI agent (streaming responses)

  ws://api/v1/notifications               Server → Client   User-level notifications (build complete, marketplace events)
  --------------------------------------- ----------------- --------------------------------------------------------------------

**21.10 Admin / Internal**

  ------------ ------------------------- --------------------------------------- --------------
  **Method**   **Endpoint**              **Description**                         **Auth**

  GET          /admin/builds             List all builds across all users        Admin JWT

  GET          /admin/containers         View container pool status              Admin JWT

  POST         /admin/containers/scale   Manually scale container pool           Admin JWT

  GET          /admin/metrics            Get system metrics snapshot             Admin JWT

  GET          /admin/flagged-accounts   View security-flagged accounts          Admin JWT

  GET          /health                   Health check endpoint                   Public

  GET          /health/ready             Readiness probe (DB + Redis + Docker)   Public
  ------------ ------------------------- --------------------------------------- --------------

**22. Cost Analysis**

Estimated per-build costs to the platform, assuming full caching and warm containers:

  ------------------------------ ------------------- ------------------- --------------------
  **Cost Component**             **Simple Plugin**   **Medium Plugin**   **Complex Plugin**

  Planning (Haiku)               \$0.01--\$0.03      \$0.02--\$0.05      \$0.03--\$0.08

  Plan doc (Sonnet)              \$0.02--\$0.05      \$0.05--\$0.15      \$0.10--\$0.30

  Implementation (Sonnet/Code)   \$0.20--\$0.50      \$0.80--\$2.00      \$2.00--\$5.00

  Docker compute                 \$0.02--\$0.05      \$0.05--\$0.10      \$0.10--\$0.20

  Total per build                \$0.25--\$0.63      \$0.92--\$2.30      \$2.23--\$5.58
  ------------------------------ ------------------- ------------------- --------------------

**22.1 Breakeven Analysis by Tier**

- **Free:** Loss leader. Capped at \$0.25--\$0.63 per user. Acceptable customer acquisition cost.

- **Basic (\$10/mo):** Breaks even at \~4--5 medium builds. 5 builds/month allocation is near breakeven, with margin from users who don't use all builds.

- **Pro (\$30/mo):** Breaks even at \~6--8 complex builds. 20 builds/month provides healthy margin.

- **Team (\$80/mo):** Breaks even at \~15--20 complex builds. Dedicated container pool has fixed overhead but scales well.

**22.2 Cost Optimization Impact**

  ---------------------------------------- ----------------------------------------------
  **Strategy**                             **Estimated Savings**

  Model routing (Haiku for conversation)   60--70% on planning phase tokens

  Prompt caching                           Up to 90% on cached system prompt input

  Template/pattern caching                 5--15% on implementation tokens

  Scope gating                             Eliminates wasted compute on doomed builds

  Container warm pool                      \~90% reduction in startup time cost

  Fail-fast error classification           30--50% reduction in wasted iteration tokens
  ---------------------------------------- ----------------------------------------------

**23. Implementation Roadmap**

The roadmap is organized into sequential phases. Each phase builds on the previous one and has a clear definition of done before the next phase begins. Timeline depends on team size and capacity.

**Phase 1: MVP**

**Goal:** End-to-end plugin generation for simple, single-command plugins.

- Chatbot with Haiku for clarification

- Plan document generation with Sonnet

- Basic Claude Code integration for implementation

- Single Docker container (build + test combined)

- Simple token budget (hard limit, no thresholds)

- Free tier only (invite-only beta)

- Backend on Oracle Cloud Free Tier (ARM) with Docker Compose

- Frontend on Vercel with Git-based auto-deploy from develop/main

- Basic GitHub Actions CI (lint + test + build)

**Done when:** A user can describe a simple plugin and receive a working JAR.

**Phase 2: Cost Optimization**

**Goal:** Implement all six cost-control strategies to make the unit economics viable.

- Model routing between Haiku and Sonnet

- Prompt caching integration

- Template and pattern caching in Redis

- Scope gating engine

- Separate build and test containers

- Warm container pool with snapshot/restore

- Error classification and fail-fast logic

- Token budget thresholds (80%, 95%, 100%)

**Done when:** Per-build cost is within the target ranges defined in cost analysis.

**Phase 3: Monetization**

**Goal:** Launch paid tiers and subscription billing.

- Stripe integration for subscriptions and overage billing

- Tier-based feature gating

- Queue priority system

- Parallel build support for Pro/Team

- Iteration loops for paid tiers

- Build history dashboard and JAR retention policies

**Done when:** Users can subscribe, build plugins within their tier limits, and pay for overages.

**Phase 4: Marketplace**

**Goal:** Launch the plugin marketplace as a distribution and revenue channel.

- Marketplace listing, discovery, and search

- Plugin version management and update notifications

- Review and rating system

- Sales processing and commission collection via Stripe

- Featured placement and verified badges

**Done when:** Users can publish, discover, purchase, and download plugins through the marketplace.

**Phase 5: Team & Collaboration**

**Goal:** Launch team collaboration features for the Team tier.

- Shared workspaces with team-level build history

- AI brainstorm sessions (multi-user + AI planning conversations)

- Plugin discussion boards

- Team build analytics and cost dashboards

- Dedicated container pools for Team tier

**Done when:** Teams can collaborate on plugin development with shared resources and AI brainstorming.

**Phase 6: Scale & Hardening**

**Goal:** Production hardening, monitoring, and infrastructure scaling.

- Full Prometheus/Grafana monitoring stack

- All upgrade trigger thresholds configured as alerts

- Canary deployment pipeline with auto-rollback

- Migration from Oracle Cloud Free Tier to Hetzner (when thresholds demand it)

- Kubernetes migration (when single-VPS capacity is exceeded)

- Performance optimization based on real usage data

- Security audit and penetration testing

**Done when:** The platform can handle 500+ users with automated scaling and alerting.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

*End of Document*
