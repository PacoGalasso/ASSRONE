-- Hibernate (ddl-auto=update) automatically generates a CHECK constraint named
-- <table>_<column>_check for any @Enumerated(EnumType.STRING) attribute. The
-- Event.type field was originally mapped as such an enum, which produced the
-- constraint events_type_check restricting the column to a fixed set of legacy
-- values (e.g. WEBINAIRE, ATELIER...).
--
-- Event.type is now a free-text VARCHAR (no enum), but ddl-auto=update is
-- additive-only: it never drops a constraint that the current mapping no
-- longer implies. On any database that already went through the old mapping,
-- the orphaned constraint remains and rejects legitimate free-text values such
-- as "Assemblée générale". A freshly created database never had the old
-- mapping applied, so it never had the constraint in the first place.
--
-- This migration only removes the now-obsolete constraint. It does not touch
-- the column type, does not alter any existing row, and is idempotent via
-- IF EXISTS so it is a no-op on databases where the constraint was never
-- created.
ALTER TABLE events
DROP CONSTRAINT IF EXISTS events_type_check;
