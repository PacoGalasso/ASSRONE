-- failed_login_attempts/locked_until (see User#failedLoginAttempts/#lockedUntil)
-- were originally added to "users" via Hibernate's ddl-auto=update ALTER TABLE
-- rather than a tracked Flyway migration, back when Hibernate was still allowed
-- to evolve the schema directly. Now that ddl-auto is unconditionally "validate"
-- in every environment (see application.properties), Flyway must own this
-- column pair too. IF NOT EXISTS makes this a safe no-op on every database that
-- already has them (production, and any already-bootstrapped local database,
-- both of which picked them up via that historical Hibernate ALTER), and a
-- safe, default-backed add on any that doesn't. A "users" table bootstrapped
-- fresh by LegacyBaselineFlywayCallback already includes both columns, so this
-- is a no-op there too — this migration exists purely to close the gap for a
-- database whose "users" table predates this lockout feature.
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
