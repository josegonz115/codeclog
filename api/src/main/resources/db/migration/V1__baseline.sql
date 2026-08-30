-- V1 — baseline.
--
-- No domain tables yet; those arrive with the milestones that own them (M2 users and
-- linked_accounts, M3 games / library_entries / playtime_snapshots / sync_jobs, M4+ reviews and
-- social). What lands here is the schema-wide groundwork every later migration depends on, so that
-- a fresh database and a migrated one are identical from V1 onwards.

-- Case-insensitive text, used for `users.handle` in M2. Handles are compared case-insensitively but
-- displayed as typed, which citext gives us without a lower() index on every lookup.
CREATE EXTENSION IF NOT EXISTS citext;

-- Every table in the data model (§6) carries created_at / updated_at. Maintaining updated_at in the
-- database rather than in the application means a manual UPDATE or a future batch job cannot leave
-- a stale timestamp behind.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION set_updated_at() IS
    'BEFORE UPDATE trigger function: stamps updated_at. Attach to every table with that column.';
