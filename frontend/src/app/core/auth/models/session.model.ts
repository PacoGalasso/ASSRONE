// core/auth/models/session.model.ts
// Mirrors backend SessionDto (GET/DELETE /api/me/sessions/**) — deliberately
// contains nothing beyond what that endpoint returns: no refresh token, no
// jti, no token hash, no internal (sequential) session ID, no other user's
// data. `id` here is always the opaque public session ID.
export interface SessionInfo {
  id: string;
  current: boolean;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
  ipAddress: string | null;
  device: string | null;
}

export interface SessionListResponse {
  sessions: SessionInfo[];
}

export interface SessionsRevokedResponse {
  revokedCount: number;
}
