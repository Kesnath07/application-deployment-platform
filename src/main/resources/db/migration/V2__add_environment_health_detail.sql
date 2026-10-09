-- Keeps the reason of the most recent failed health probe (HTTP status, timeout, refused
-- connection, ...) so the UI and the status API can explain why an environment is DOWN.
-- Nullable and additive: existing rows keep working and are filled in by the next probe.
ALTER TABLE environments ADD COLUMN last_health_detail VARCHAR(255);
