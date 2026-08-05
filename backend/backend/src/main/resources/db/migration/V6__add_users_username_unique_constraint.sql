-- users.username was never declared unique on the entity (model/User.java),
-- yet MembershipApplicationService#accept generates a username from an
-- email's local part with no collision handling: two different emails
-- sharing the same local part (e.g. jean@gmail.com and jean@outlook.com)
-- could silently produce two rows with the identical username. Application
-- code now checks availability and picks a distinct username on collision,
-- but that check alone cannot close a genuine concurrent race — this
-- constraint is the actual guarantee.
--
-- Explicitly named (uk_users_username) so MembershipApplicationService and
-- UserInfoService can reliably tell a username collision apart from any
-- other unique constraint that might also fail on the same INSERT (e.g.
-- users.email), instead of guessing from an auto-generated name.
--
-- Existing duplicate usernames, if any, must be resolved manually before
-- this migration can apply — it fails loudly rather than silently picking
-- a "winner" among them, the same posture V1 already takes for duplicate
-- PENDING membership applications.
DO $$
DECLARE
    doublons_username integer;
BEGIN
    SELECT COUNT(*) INTO doublons_username
    FROM (
        SELECT username
        FROM users
        GROUP BY username
        HAVING COUNT(*) > 1
    ) AS groupes_en_doublon;

    IF doublons_username > 0 THEN
        RAISE EXCEPTION 'Migration annulée : % nom(s) d''utilisateur en doublon existent déjà. Identifiez-les avant de relancer avec : SELECT username, COUNT(*) FROM users GROUP BY username HAVING COUNT(*) > 1;', doublons_username;
    END IF;
END $$;

ALTER TABLE users
ADD CONSTRAINT uk_users_username UNIQUE (username);
