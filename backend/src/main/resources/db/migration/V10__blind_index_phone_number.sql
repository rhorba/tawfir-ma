-- Phone-number blind-index encryption (security-tawfir.md §5, database-tawfir.md §7).
-- No backfill: pre-launch, no shared/staging data exists yet for either table.

ALTER TABLE users
    ADD COLUMN phone_number_hash      VARCHAR(64),
    ADD COLUMN phone_number_encrypted TEXT;

ALTER TABLE users DROP COLUMN phone_number;

ALTER TABLE users
    ALTER COLUMN phone_number_hash SET NOT NULL,
    ALTER COLUMN phone_number_encrypted SET NOT NULL;

ALTER TABLE users ADD CONSTRAINT users_phone_number_hash_key UNIQUE (phone_number_hash);

ALTER TABLE otp_challenges
    ADD COLUMN phone_number_hash VARCHAR(64);

DROP INDEX idx_otp_phone_expiry;

ALTER TABLE otp_challenges DROP COLUMN phone_number;

ALTER TABLE otp_challenges
    ALTER COLUMN phone_number_hash SET NOT NULL;

CREATE INDEX idx_otp_phone_hash_expiry ON otp_challenges (phone_number_hash, expires_at);
