-- Story 1.4 (Admin TOTP MFA): totp_secret holds the AES-GCM encrypted+base64
-- TOTP secret (app-layer encryption using PII_ENCRYPTION_KEY, see AesGcmEncryptor);
-- null until an admin calls POST /api/v1/admin/mfa/setup. totp_enabled_at is null
-- until the first successful POST /api/v1/admin/mfa/verify — this two-phase design
-- means a stored-but-unverified secret never grants MFA-bypass on its own.
--
-- mfa_failed_attempts / mfa_locked_until: brute-force protection for TOTP code
-- verification (both the setup-activation and login-exchange endpoints share this
-- counter), mirroring the existing otp_challenges.attempt_count pattern — a 6-digit
-- code must never be guessable via unlimited attempts against an admin account.
ALTER TABLE users
    ADD COLUMN totp_secret         VARCHAR(255),
    ADD COLUMN totp_enabled_at     TIMESTAMPTZ,
    ADD COLUMN mfa_failed_attempts SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN mfa_locked_until    TIMESTAMPTZ;
