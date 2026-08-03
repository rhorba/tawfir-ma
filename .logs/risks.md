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

### [2026-07-21] DENIAL OF SERVICE — no per-IP OTP request rate limiting
- **Specialist**: Security Engineer
- **Summary**: `POST /api/v1/auth/otp/request` only rate-limited per phone number (5/10min) — flagged in security-tawfir.md STRIDE table as a DoS risk (one IP spraying OTP requests across many different phone numbers to run up SMS costs or harass numbers, bypassing the per-phone limit entirely). AuthService's own javadoc explicitly called this out as intentionally deferred pending a design that survives horizontal scaling.
- **Probability**: Medium (requires an attacker to actively target the endpoint, but the endpoint is public/unauthenticated by design)
- **Mitigation**: DB-backed per-IP count (same mechanism as the existing per-phone check), not in-memory, so it works correctly even if the backend scales to multiple instances.
- **Status**: closed — 2026-08-01. Migration V11 adds `otp_challenges.ip_address`; `AuthService.requestOtp` now checks both `countByPhoneNumberHashAndCreatedAtAfter` (5/10min, existing) and `countByIpAddressAndCreatedAtAfter` (20/10min, new — higher threshold than per-phone to tolerate legitimate NAT/shared-IP traffic). `AuthController` passes `HttpServletRequest.getRemoteAddr()` through. security-tawfir.md STRIDE row and database-tawfir.md schema updated. Residual: this is fixed-window counting, not exponential backoff, and `getRemoteAddr()` assumes no reverse proxy in front of the app — if one is ever added, `X-Forwarded-For` trust handling will need revisiting.
- **Impact**: medium
---

### [2026-07-31] OPERATIONAL — no notification channel for late contributions or stuck/failed payouts
- **Specialist**: Backend Dev
- **Summary**: Story 3.4 (FR-5, auto-flag late contributions) and story 4.1 (payout cron) both shipped with a "notification channel TBD" placeholder — the `LateContributionScheduler` and `PayoutScheduler` jobs correctly flip status (`PENDING`→`LATE`, payout left `PENDING` when not ready) but nobody was actually told, so an organizer had no way to learn a payout needed manual attention short of checking the app.
- **Probability**: Medium (surfaces every time a real cycle runs late or a payout isn't ready — not an edge case, a routine operational path)
- **Mitigation**: Mock-first `NotificationProvider`/`MockNotificationProvider` (new `ma.tawfir.api.notification` package, same pattern as `OtpProvider`/`CmiClient`), selected via `NOTIFICATION_PROVIDER` env var (`TawfirProperties`, `application.yml`, `.env.example`, `docker-compose.yml`). `LateContributionScheduler` notifies the affected member ("Your contribution...") and the organizer ("A member's contribution..."); `PayoutScheduler` notifies the organizer when a payout can't auto-execute or throws. `ContributionScheduleRepository.findByStatusAndDueDateBefore` added so the scheduler can look up who to notify before the bulk status-flip UPDATE runs.
- **Status**: closed — 2026-08-03. Implemented, `mvnw verify` green (236/236 tests, checkstyle clean, coverage gate met — caught and fixed a real bug along the way: `LateContributionSchedulerTest` built its `scheduler` field before Mockito injected the `@Mock` fields, capturing nulls; fixed by moving construction into `@BeforeEach`, matching `PayoutSchedulerTest`'s existing pattern). `stories-tawfir.md` story 3.4's "channel TBD" note updated. Residual: still a mocked provider — swapping in a real SMS/push implementation before public launch remains open, same category as the OTP/CMI mocks.
- **Impact**: low
---
