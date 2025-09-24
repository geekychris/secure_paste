-- SecurePaste Database Initialization Script

-- Create database if not exists
-- (This is done by POSTGRES_DB environment variable)

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE pastebin TO pastebin;

-- Connect to the pastebin database
\c pastebin;

-- Create schema if needed (optional)
-- CREATE SCHEMA IF NOT EXISTS pastebin;

-- Create indexes for better performance (will be created by Hibernate, but included for reference)
/*
CREATE INDEX IF NOT EXISTS idx_pastes_created_at ON pastes(created_at);
CREATE INDEX IF NOT EXISTS idx_pastes_expires_at ON pastes(expires_at);
CREATE INDEX IF NOT EXISTS idx_pastes_language ON pastes(language);
CREATE INDEX IF NOT EXISTS idx_pastes_visibility ON pastes(visibility);
CREATE INDEX IF NOT EXISTS idx_pastes_author_email ON pastes(author_email);
CREATE INDEX IF NOT EXISTS idx_pastes_is_deleted ON pastes(is_deleted);
*/

-- Insert sample data for development/testing
-- This will be handled by the application, but you can add sample data here if needed