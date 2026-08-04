-- Adds visibility to documents so an administrator can restrict a document to
-- committee members (technically: users with role ADMIN) instead of exposing
-- it to every authenticated member. Backend enforcement lives in
-- DocumentService/DocumentController (see their Javadoc/comments); this
-- migration only prepares the column.
--
-- The column is added nullable first, backfilled to MEMBERS for every
-- existing document (preserving current, already-granted access — no
-- document silently disappears or becomes private), then made NOT NULL. No
-- row is deleted, no file is moved, no other column is touched.
--
-- No explicit CHECK constraint is added here: Hibernate's ddl-auto=update
-- already generates one automatically for @Enumerated(EnumType.STRING)
-- columns (documents_visibility_check), the exact mechanism documented in
-- V2's header. Duplicating it here would risk a constraint-name collision.
ALTER TABLE documents
ADD COLUMN IF NOT EXISTS visibility VARCHAR(32);

UPDATE documents
SET visibility = 'MEMBERS'
WHERE visibility IS NULL;

ALTER TABLE documents
ALTER COLUMN visibility SET NOT NULL;
