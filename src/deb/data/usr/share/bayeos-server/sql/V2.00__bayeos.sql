-- Drop database connection settings for authentication
ALTER TABLE benutzer DROP COLUMN fk_auth_db CASCADE;
drop table auth_db;