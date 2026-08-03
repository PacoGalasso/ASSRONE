-- This migration only runs once membership_applications already exists: Hibernate
-- (ddl-auto=update) is the sole owner of this table's schema, and Flyway's actual
-- migrate() call is deliberately deferred until after the Spring context is fully
-- started (see FlywayMigrationConfig / FlywayStartupMigrator), guaranteeing the
-- table is present here on every environment, fresh or existing.
--
-- The unique index is expression-based (lower(btrim(email))) rather than on the
-- plain email column, so the DB-level guarantee holds even if a future write path
-- forgets Java-side normalization.
--
-- Existing PENDING duplicates are never merged or deleted automatically: if any
-- are found, the migration fails loudly so an administrator can resolve them
-- first, using the same grouping query below.
DO $$
DECLARE
    doublons_pending integer;
BEGIN
    SELECT COUNT(*) INTO doublons_pending
    FROM (
        SELECT lower(btrim(email))
        FROM membership_applications
        WHERE status = 'PENDING'
        GROUP BY lower(btrim(email))
        HAVING COUNT(*) > 1
    ) AS groupes_en_doublon;

    IF doublons_pending > 0 THEN
        RAISE EXCEPTION 'Migration annulée : % email(s) normalisé(s) (lower(btrim(email))) ont plusieurs demandes d''adhésion PENDING en doublon. Identifiez-les avant de relancer avec : SELECT lower(btrim(email)) AS normalized_email, COUNT(*) FROM membership_applications WHERE status = ''PENDING'' GROUP BY lower(btrim(email)) HAVING COUNT(*) > 1;', doublons_pending;
    END IF;
END $$;

CREATE UNIQUE INDEX uk_membership_application_pending_email
    ON membership_applications ((lower(btrim(email))))
    WHERE status = 'PENDING';
