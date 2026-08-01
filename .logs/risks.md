# RISKS — Tawfir.ma



### [2026-07-21] REGULATORY — Payment custody model undecided
- **Specialist**: Security Engineer
- **Summary**: Whether Tawfir holds/moves member funds (custodial) vs only records external transfers (non-custodial) determines Bank Al-Maghrib payment-institution licensing exposure. Docs assume non-custodial as MVP default; not yet confirmed by user/legal counsel.
- **Probability**: Medium (depends on product decision, not yet made)
- **Mitigation**: Confirm with Moroccan legal counsel before Sprint 2 execution; non-custodial default keeps regulatory exposure low in the meantime.
- **Status**: closed — 2026-08-01, user confirmed non-custodial model as final (SDR-3 updated in system-design-tawfir.md, security-tawfir.md §6.1). No code changes needed; Sprint 4's webhook/payout implementation already matches this model. Residual item: BAM notification/registration duty for non-custodial record-keeping itself is still worth a legal check before public launch — tracked as a recommendation in security-tawfir.md §6.1, not a blocking risk.
- **Impact**: high
---

### [2026-07-21] DATA PROTECTION — users.phone_number stored in plaintext
- **Specialist**: Security Engineer / DBA
- **Summary**: `users.phone_number` (and `otp_challenges.phone_number`) stored as plaintext VARCHAR since Sprint 2 — flagged in database-tawfir.md §7 and security-tawfir.md §5 as needing encryption at rest before any shared/staging environment holds real phone numbers. Deferred because AES-GCM's random IV breaks the equality lookup/UNIQUE constraint needed for OTP login, requiring a blind-index design rather than a drop-in converter.
- **Probability**: High (column is written on every login, so exposure grows immediately once real users exist)
- **Mitigation**: Blind-index scheme — deterministic HMAC-SHA256 hash column for lookups + AES-GCM reversible copy for display, both keyed off the already-reserved `PII_ENCRYPTION_KEY`.
- **Status**: closed — 2026-08-01. Implemented `PhoneNumberCodec`; migration V10 replaces `users.phone_number`/`otp_challenges.phone_number` with `phone_number_hash` (+`phone_number_encrypted` on `users` only). Updated `AuthService`, `GroupService`, `MfaService`, and all affected repositories/tests. `database-tawfir.md` §3/§4/§7 and `security-tawfir.md` §5 updated to match. `users.national_id` intentionally left unencrypted — the column is unused/unpopulated (KYC deferred); apply the same scheme before it's ever written to.
- **Impact**: high
---
