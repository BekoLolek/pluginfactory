# BekoLolek Plugin Factory

AI-powered Minecraft plugin development platform.

See [BekoLolek_Plugin_Factory_Architecture.docx](BekoLolek_Plugin_Factory_Architecture.docx) for full architecture details.

## Security

Phase 20 added comprehensive security hardening:

- **RBAC**: JWT-based role system (USER/ADMIN) with `@EnableMethodSecurity`
- **Auth**: OAuth state parameter, rate limiting on auth/chat endpoints, refresh token cleanup
- **Input**: Prompt injection blocking, pagination caps, DTO validation constraints, Zip Slip protection
- **Infrastructure**: Docker socket proxy (no direct mount), Redis password, no default credentials
- **Frontend**: CSP headers, tokens in sessionStorage, redirect URL validation, JWT expiry checks
- **WebSocket**: CORS restricted to configured origins, unauthenticated connections rejected

### Required Environment Variables (Production)

```
JWT_SECRET, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD,
REDIS_PASSWORD, ANTHROPIC_API_KEY, STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET,
DISCORD_CLIENT_ID, DISCORD_CLIENT_SECRET, DISCORD_REDIRECT_URI,
MINIO_ACCESS_KEY, MINIO_SECRET_KEY, CORS_ALLOWED_ORIGINS, APP_BASE_URL
```
