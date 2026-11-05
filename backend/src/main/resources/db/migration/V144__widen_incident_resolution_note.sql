-- The API accepts notes up to 1000 chars, and unban appends a system suffix to them.
ALTER TABLE security_incident MODIFY resolution_note VARCHAR(2000) NULL;
