-- Adds email_verified_at (User#emailVerifiedAt), owned by Flyway from the
-- start — unlike V8's lockout columns, this is a genuinely new column, not a
-- historical retrofit, so LegacyBaselineFlywayCallback's "users" baseline
-- deliberately does NOT include it: this migration is the only thing that
-- ever adds it, on a freshly-bootstrapped database exactly like on an
-- existing one.
--
-- Backfill: every account that already exists at the moment this migration
-- runs is, by definition, an already-active member who registered under the
-- old (no-verification) system — leaving their email_verified_at null would
-- lock every single existing account out the moment login starts requiring
-- verification (see EmailVerificationService / UserController). Marking
-- them verified as of their own created_at is the explicit, documented
-- choice: accounts created before this migration are grandfathered in as
-- verified; only accounts created after it start out unverified and must
-- complete the new flow.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP;

UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;
