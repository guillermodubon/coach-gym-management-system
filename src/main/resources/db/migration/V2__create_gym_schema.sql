CREATE SCHEMA IF NOT EXISTS gym;

REVOKE ALL ON SCHEMA gym FROM PUBLIC;

COMMENT ON SCHEMA gym IS 'Coach Gym application data. The schema is intentionally not exposed through Supabase Data API.';

CREATE OR REPLACE FUNCTION gym.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION gym.set_updated_at() IS 'Maintains updated_at for mutable Coach Gym entities.';
