# Deployment topology (recommended, not currently implemented)

This directory documents a reproducible deployment topology for ASSRONE. Nothing
here is read by the application or by any build — it exists purely as
documentation, since no reverse proxy, Dockerfile, or CI/CD pipeline currently
exists in this repository (confirmed by audit; see the `security/*` lot reports
for how each piece — CSP, cookies, CORS, headers — was verified against the real
code before being documented here).

## Why this topology

Every HTTP call the Angular frontend makes uses a relative path (`/api/...`,
`/auth/...` — see `frontend/proxy.conf.json` and any `*-service.ts` under
`frontend/src/app/core`). That is a structural requirement, not a preference:
frontend and backend must be reachable under the **same public origin**, or
those calls resolve against the wrong host. A reverse proxy is the standard way
to satisfy that while still running Angular (static files) and Spring Boot
(a JVM process) as two independent things.

## Recommended topology

```
Internet → reverse proxy (HTTPS, port 443) → ┬─ /            → Angular static files (with SPA fallback)
                                              ├─ /api/*       → Spring Boot backend (internal network only)
                                              ├─ /auth/*      → Spring Boot backend (internal network only)
                                              └─ /health      → Spring Boot backend (internal network only)
```

- **TLS terminates at the proxy.** The backend is never reached directly from
  the internet in this topology — see `deployment/nginx/assrone.conf.example`
  for the placeholder config.
- **Angular is served as plain static files** (the output of
  `ng build --configuration production`, i.e. `frontend/dist/frontend/browser/`)
  — Spring Boot does not serve them (confirmed by audit: no
  `src/main/resources/static`, no `WebMvcConfigurer` resource handler).
- **Security headers on the frontend are the proxy's responsibility** (see the
  example config) — Spring Security already sets the identical CSP/Referrer-
  Policy/Permissions-Policy/COOP/CORP/X-Content-Type-Options/X-Frame-Options on
  every backend response (see `SecurityConfig.java`/`ContentSecurityPolicy.java`),
  so the proxy config deliberately does not repeat them on the `/api/`,
  `/auth/`, or `/health` locations.
- **`app.security.trusted-proxies`** must list the reverse proxy's real address
  if X-Forwarded-For-based client IP resolution is needed (see
  `ClientIpResolver`) — never a wildcard, and left empty (the default) means
  `X-Forwarded-For` is ignored entirely.
- **`app.security.cors.allowed-origins`** should normally not even need a
  cross-origin entry under this topology (same public origin) — it exists for
  the case where a second, separate frontend origin genuinely needs credentialed
  access, not as a workaround for this topology.

## Health check

`GET /health` (see `HealthController`) returns `{"status":"UP"}` with no
details — no database, disk, or dependency status, no version, no environment.
It exists specifically so the reverse proxy (or any orchestrator) has something
unauthenticated to poll before routing traffic to a backend instance, without
the surface area Spring Boot Actuator would introduce. Actuator is not a
dependency of this project and should not be added without a concrete need for
its wider feature set (metrics, thread dumps, etc.) — see the lot's final report
for the full reasoning.

## Environment variables required in production

See `backend/backend/.env.example` for the authoritative, actively-maintained
list with generation instructions. At minimum: `SPRING_PROFILES_ACTIVE=production`,
`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`, `SMTP_PASSWORD`,
`CORS_ALLOWED_ORIGINS`. `ProductionSecurityGuard` and `CorsProperties` refuse to
let the application start if the security-critical ones are absent, empty, or
manifestly unsafe.

## JWT secret rotation

Rotating `JWT_SECRET` invalidates **every** currently-issued access token and
refresh token immediately — both are signed with the same HMAC key, there is no
multi-secret/grace-period validation in this codebase, and none was added by
this lot (out of scope — see the lot's final report for why). A rotation is
therefore always a hard cutover:

1. Schedule a maintenance window; users will be forced to log in again.
2. Optionally revoke all outstanding refresh tokens ahead of time (see
   `RefreshTokenService`) so a stale one can't be replayed against the old key
   in the gap before restart.
3. Generate a new secret (`openssl rand -base64 48` — see `.env.example`) and
   set it as `JWT_SECRET` in the deployment environment.
4. Restart the backend. `ProductionSecurityGuard` will refuse to start if the
   new value collides with the known test secret or fails `JwtService`'s
   structural checks (wrong length, invalid Base64, absent).
5. Confirm users can log in again and that no unexpected 401s persist beyond
   the expected "everyone must log in again" wave.
6. Check server logs for authentication errors — never for the secret value
   itself, which this codebase never logs anywhere (see the lot's final report,
   logging audit section).
