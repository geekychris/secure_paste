-- View-limited ("burn after reading") pastes: how many times a paste may be
-- revealed before it is deleted. NULL means unlimited, so every existing paste
-- keeps working exactly as before.
--
-- The docker and kubernetes profiles (ddl-auto: update) add this column on
-- startup. Run this by hand only where Hibernate validates instead of
-- updating the schema (the production profile). It is idempotent.
--
--   psql -U pastebin pastebin -f db/migrations/V2__max_views.sql
ALTER TABLE pastes ADD COLUMN IF NOT EXISTS max_views INTEGER;
