 -- Register date of login attempts 
 alter table benutzer add column last_seen timestamptz;
