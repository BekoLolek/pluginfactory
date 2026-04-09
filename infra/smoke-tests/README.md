# API Smoke Tests

Hurl-based smoke tests for the Plugin Factory HTTP API. These run against a
live URL (local dev, staging, or prod) and verify each endpoint responds with
the expected status code and structure.

## What's covered

| File | Endpoints | Safe for prod? |
|---|---|---|
| `public.hurl` | `/health`, `/health/ready`, `/api/v1/subscriptions/tiers`, `/api/v1/marketplace/plugins`, `/api/v1/auth/discord` | yes — read-only |
| `auth-negative.hurl` | Every authenticated endpoint, asserting 401 without a token, plus the Stripe webhook signature reject path | yes — every request is rejected before touching state |

The deploy workflow (`.github/workflows/deploy-main.yml`) runs both files
against `https://api.pluginfactory.org` after a successful deploy, so any
broken endpoint fails the run.

## Not covered (yet)

- **Authenticated happy paths.** The Discord OAuth flow needs a browser, so
  exercising `/api/v1/users/me`, `/api/v1/builds`, etc. with a real token
  requires either (a) a seeded test user with a directly-minted JWT, or
  (b) a mock auth provider in a dedicated test profile. Neither is wired up.
- **Stripe webhook happy path.** Needs a real Stripe-signed payload, which
  means either calling the Stripe CLI or constructing a signature manually
  with the webhook secret. Out of scope for a public smoke suite.
- **Server-Sent Events stream** (`/api/v1/builds/{id}/messages/stream`).
  Hurl can hit it, but verifying streaming output is awkward.

## Running locally

Install Hurl (one binary, no runtime):

```bash
# macOS
brew install hurl

# Linux (download from GitHub releases)
curl -LO https://github.com/Orange-OpenSource/hurl/releases/latest/download/hurl-5.0.1-x86_64-unknown-linux-gnu.tar.gz
tar -xzf hurl-*.tar.gz && sudo mv hurl-*/bin/hurl /usr/local/bin/

# Windows
winget install hurl
```

Then from the repo root:

```bash
# Against local dev (Spring Boot on :8080)
hurl --test --variable base=http://localhost:8080 \
  infra/smoke-tests/public.hurl \
  infra/smoke-tests/auth-negative.hurl

# Against production
hurl --test --variable base=https://api.pluginfactory.org \
  infra/smoke-tests/public.hurl \
  infra/smoke-tests/auth-negative.hurl
```

The `--test` flag exits non-zero on any failed assertion and prints a summary.

## Running via Docker (no install)

```bash
docker run --rm -v "$(pwd)/infra/smoke-tests:/tests" \
  ghcr.io/orange-opensource/hurl:latest \
  --test --variable base=https://api.pluginfactory.org \
  /tests/public.hurl /tests/auth-negative.hurl
```

This is what the deploy workflow uses.

## Adding a new endpoint

When you add a new controller method:

1. If it's **public** (no auth), add a `GET`/`POST` block to `public.hurl`
   with `HTTP 200` and a `[Asserts]` section verifying the response shape.
2. If it's **authenticated**, add a request to `auth-negative.hurl` with
   `HTTP 401` to prove the security boundary.
3. Run the suite locally before pushing.

Hurl docs: https://hurl.dev/docs/asserting-response.html
