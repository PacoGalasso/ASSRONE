# Session management

This documents the user-facing and system-level session-management feature
(`security/session-management` lot): what a "session" is in this codebase,
how it relates to refresh tokens, what revocation actually guarantees, and
what it deliberately does not.

## Session vs. refresh token

A refresh token (`RefreshToken`, table `refresh_tokens`) is a single-use
bookkeeping row backing one JWT refresh token, replaced by a new row on every
rotation (`RefreshTokenService#rotate`). A **session** (`UserSession`, table
`user_sessions`) is the stable identity that survives across every rotation
of a login: one row per login, referenced by every `RefreshToken` row minted
during that login's lifetime via `refresh_tokens.session_id`.

This is why "Sessions actives" shows one entry per login/device, not one per
refresh — a session that has silently rotated its refresh token forty times
over a week is still exactly one row a user can see and revoke, not forty.

The session's opaque public ID (`UserSession.publicId`, a server-generated
UUID, distinct from the refresh token, its hash, and its per-rotation `jti`)
is also embedded in every access token as the `sid` claim
(`JwtService.SESSION_ID_CLAIM`). It grants no authority by itself — `sub` and
`role` still drive authentication and authorization exactly as before — it
only lets the frontend and `SessionController` determine "is this my current
session" without needing a server round trip beyond the request already
being made.

## Revocation: real scope

Revoking a session (`DELETE /api/me/sessions/{id}`, or as a side effect of
"revoke others" / "revoke all" / reuse detection / a password change) does
two things atomically: marks the `UserSession` row revoked, and revokes
every `RefreshToken` row that ever belonged to it
(`RefreshTokenRepository#revokeAllForSession`). The next attempt to refresh
using any of that session's tokens is denied.

**What revocation does NOT do**: invalidate an access token already issued
and sitting in a browser tab. `JwtAuthFilter` is fully stateless — it
validates the JWT signature/expiry/type and re-checks the user's
`isActive`/lockout state on every request, but it never looks up the
session or refresh-token tables. A revoked session's already-issued access
token therefore remains technically usable **until it expires on its own
(30 minutes) or until the browser next tries to refresh it, whichever
happens first**. This is a deliberate, proportionate choice (Option 1 of
three considered — see the lot's cartographie) given the existing 30-minute
access-token lifetime and the added latency/complexity of the alternative
(a per-request session lookup, or a revocation cache). Frontend copy and
tests reflect this honestly: nothing in this feature claims instant
cross-device termination.

Revoking your own **current** session (or "revoke all", which always
includes it) additionally clears the refresh cookie and the frontend's
in-memory access token/user state immediately, with no refresh attempt —
see `AuthService#endLocalSession` and `SessionController`.

## Reuse detection and session scope

`RefreshTokenService#rotate` already revoked every refresh token for a user
on detecting reuse of an already-rotated token (pre-existing behavior, kept
unchanged: `REFRESH_TOKEN_REUSE_DETECTED`). This lot adds one refinement:
before escalating to reuse detection, it checks whether the *session* owning
the presented token is already explicitly revoked (logout, an explicit
"revoke this session" action, limit enforcement, a password change). If so,
the denial is a plain `REFRESH_DENIED` — an already-logged-out device
retrying a refresh is not evidence of theft, and must not silently nuke
every other session the user still has open. Genuine reuse (a superseded,
still-owned-by-an-active-session token replayed) keeps the original,
maximally cautious behavior: every session for that user is revoked.

## Session limit

`app.security.sessions.max-active` (default 5, validated 1–20 at startup)
caps concurrent active sessions per account. When a new login would exceed
it, the oldest active session (by last-used time) is revoked to make room —
never a login refusal. This is enforced under a per-user Postgres advisory
lock (`SessionService#createSession`, mirroring the existing lock pattern in
`AdminUserManagementService`), so two simultaneous logins can never durably
leave more than the configured limit active; see
`SessionLimitConcurrencyTest` for the real-Postgres concurrency proof. Each
eviction is audited as `SESSION_LIMIT_ENFORCED`.

## Cleanup

A scheduled job (`SessionCleanupService`, disableable via
`app.security.sessions.cleanup.enabled`, cron via
`app.security.sessions.cleanup.cron`) deletes: sessions revoked more than
`app.security.sessions.cleanup.retention-after-expiry` (default 7 days) ago,
and sessions that simply expired (never revoked) more than that long ago.
The retention window exists so a revoked session's rows survive long enough
for the reuse-detection check above to still work correctly for any
in-flight stray refresh attempt. Deleting a `UserSession` row cascades to
its `RefreshToken` rows (`ON DELETE CASCADE`), so no explicit token cleanup
query is needed. Safe to run redundantly if more than one backend instance
is ever deployed — every step is a plain, self-contained `DELETE ... WHERE`.

## Deletion / lifecycle integration

- **User deletion** (`AdminUserManagementService#deleteUser`): cascades
  through `users → user_sessions → refresh_tokens` via `ON DELETE CASCADE`
  at the database level — no orphaned rows.
- **Password change** (`UserProfileService#changePassword`, pre-existing):
  now revokes both refresh tokens and sessions in one call
  (`RefreshTokenService#revokeAllForUser` internally calls
  `SessionService#revokeAllSessionsSilently`), so "Sessions actives" no
  longer shows a stale session as active after a password change.
- **Account deactivation**: no dedicated deactivation endpoint exists yet in
  this codebase. `SessionService#revokeAllSessionsSilently` /
  `SessionService#revokeAll` are the reusable primitives a future
  deactivation (or account-recovery) lot should call.

## Personal data stored, and why

Each `UserSession` row stores, and only stores: `createdIp` / `lastSeenIp`
(client IP at creation / most recent activity — via the existing
`ClientIpResolver`, never a raw `X-Forwarded-For` read), and
`userAgentLabel` (the raw `User-Agent` header, control characters stripped,
truncated to 255 characters — not parsed into a friendly "Chrome on
Windows"-style label, to avoid the added complexity/dependency of a UA
parser for a first cut). No canvas fingerprint, font list, screen
resolution, hardware/ad ID, precise geolocation, or full IP history is
collected. Recommended retention: the default 7-day post-revocation/
post-expiry window (`app.security.sessions.cleanup.retention-after-expiry`)
is intentionally short — long enough for reuse-detection correctness, not a
long-term audit trail (that role is already served by the separate
security-audit-log lot, which never stores tokens or full session state).

## Endpoints

| Method & path | Purpose | Auth | Notable statuses |
|---|---|---|---|
| `GET /api/me/sessions` | List the caller's active sessions | Bearer | 200 |
| `DELETE /api/me/sessions/{id}` | Revoke one owned session | Bearer | 204; 400 malformed id; 404 unknown/not-owned (generic, indistinguishable) |
| `DELETE /api/me/sessions/others` | Revoke every session except the caller's current one | Bearer | 200 `{"revokedCount": n}` |
| `DELETE /api/me/sessions` | Revoke every session, including current; clears the refresh cookie | Bearer | 204 |

All four are Bearer-only (never cookie-authenticated), by design: they stay
naturally outside `AuthCookieOriginFilter`'s scope (that filter exists only
for the two genuinely cookie-driven endpoints, `/auth/refresh` and
`/auth/logout`) and add no CSRF exposure, consistent with every other
`/api/**` mutation in this codebase.

## Configuration variables

See `.env.example` for the authoritative list with defaults:
`SESSIONS_MAX_ACTIVE`, `SESSIONS_CLEANUP_ENABLED`, `SESSIONS_CLEANUP_CRON`,
`SESSIONS_CLEANUP_RETENTION`.

## Manual validation procedure

Documented here rather than automated (no E2E/Playwright/Cypress
infrastructure exists in this repository, and this lot does not introduce
one — see the lot's final report). Perform locally, against a local
Postgres and local backend/frontend, never against a real deployed
environment:

1. Log in from two different browsers (or one normal + one private window)
   with the same account.
2. Open "Mon compte → Sécurité → Sessions actives" in browser A; confirm
   both sessions are listed, and that the one in browser A is marked
   "Session actuelle" while browser B's is not.
3. From browser A, revoke browser B's session. Confirm it disappears from
   browser A's list.
4. In browser B, wait for its access token to be used again (or trigger a
   refresh manually) — confirm the next refresh attempt fails and browser B
   is logged out. Note honestly: if browser B's access token was still
   valid (within its 30-minute window) at step 3, ordinary API calls in
   browser B may keep succeeding until that token expires or a refresh is
   attempted — this is the documented, accepted behavior above, not a bug.
5. Log in again from browser B. From browser A, click "Déconnecter les
   autres sessions". Confirm browser A stays logged in and browser B is
   logged out on its next refresh.
6. Log in from browser B again. From browser A, click "Déconnecter tous les
   appareils". Confirm browser A itself is immediately logged out (no
   refresh attempt observed in the network tab) and redirected, and browser
   B is logged out on its next refresh attempt.
7. Log in repeatedly (more than `SESSIONS_MAX_ACTIVE`) from new
   sessions/browsers for the same account. Confirm the oldest session is
   silently revoked each time the limit would be exceeded, and the account
   is never locked out of logging in.
8. Present an already-rotated (superseded) refresh token by reusing a
   captured `refresh_token` cookie value after it has legitimately rotated
   once. Confirm every session for that account is revoked
   (`REFRESH_TOKEN_REUSE_DETECTED` in the logs) and the response is generic.
9. Set `app.security.sessions.cleanup.retention-after-expiry` to a very
   short value locally, revoke a session, wait past the window, trigger the
   cleanup job (or wait for its cron), and confirm the row is purged from
   `user_sessions` — and that still-active sessions are never touched.
10. Inspect backend logs for the whole procedure above: confirm no refresh
    token, hash, `jti`, cookie value, or `Authorization` header value ever
    appears — only opaque session public IDs, internal user IDs, and
    reason codes.

## Residual risks

- **Access-token residual validity window**: by design (see above), up to
  30 minutes. Not addressable without either shortening the access-token
  lifetime (out of scope — constraints for this lot froze existing token
  durations) or adding per-request session validation (a bigger
  architectural change, deliberately not taken here).
- **User-Agent as stored is unparsed**: a user comparing sessions sees the
  raw (truncated, sanitized) `User-Agent` string, not a friendly "Chrome on
  Windows" label — less immediately readable, but avoids a UA-parsing
  dependency and its own maintenance burden.
- **No distributed lock on the cleanup job**: acceptable at this
  application's scale (see "Cleanup" above); would need revisiting if the
  backend is ever horizontally scaled to many instances with a much larger
  session table.
- **IP address stored is only as trustworthy as `ClientIpResolver`'s
  existing trusted-proxy configuration** (`app.security.trusted-proxies`) —
  unchanged by this lot, documented in `deployment/README.md`.
