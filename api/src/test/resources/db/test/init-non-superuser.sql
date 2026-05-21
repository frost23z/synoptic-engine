-- Prepare a non-BYPASSRLS role for integration tests.
DO
$$
DECLARE
    db_name text := current_database();
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synoptic_app') THEN
        CREATE ROLE synoptic_app NOBYPASSRLS;
    END IF;
    EXECUTE format('GRANT ALL PRIVILEGES ON DATABASE %I TO synoptic_app', db_name);
    GRANT USAGE, CREATE ON SCHEMA public TO synoptic_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO synoptic_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO synoptic_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON FUNCTIONS TO synoptic_app;
    GRANT synoptic_app TO CURRENT_USER;
END;
$$;
