-- Per-IP OTP request rate limiting (security-tawfir.md STRIDE — DoS via OTP spam).
-- No backfill: pre-launch, no shared/staging data exists yet for otp_challenges.

ALTER TABLE otp_challenges
    ADD COLUMN ip_address VARCHAR(45) NOT NULL;

CREATE INDEX idx_otp_ip_created ON otp_challenges (ip_address, created_at);
