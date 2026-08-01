# Security Baseline: Tawfir.ma
**Architecture Reference**: docs/architecture-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: Security Engineer

> ⚠️ This document covers application/infra security. It is **not legal advice**. The regulatory sections below flag categories of Moroccan law/regulation likely relevant to a fintech-adjacent product handling group savings — confirm exact statutory citations and licensing requirements with Moroccan legal counsel (and Bank Al-Maghrib directly if pursuing a custodial model) before launch.

## 1. Threat Model (5-Minute)
- **What are we building?** A web platform (React member app + Angular admin app + Spring Boot API) that digitizes rotating savings groups (daret) — tracking contributions, payouts, and disputes, with a phone-number identity for ~thousands of low-to-medium digital-literacy users in Morocco.
- **Who would attack it?** Mostly: (a) a dishonest Organizer or Member trying to manipulate the ledger or dispute outcomes for financial gain; (b) opportunistic attackers targeting PII/phone numbers for SIM-swap or OTP-interception fraud; (c) low probability of nation-state or highly sophisticated actors at MVP scale.
- **Worst outcome?** Financial loss to members (fraudulent payout/contribution records), reputational collapse of the platform (trust is the entire product), and regulatory action if money movement is deemed unlicensed payment-institution activity.

## 2. STRIDE Analysis

| Threat | Component | Mitigation | Status |
|---|---|---|---|
| **S**poofing — attacker impersonates a member via SIM-swap/OTP interception | Auth (OTP) | Rate-limit OTP requests per phone number; short OTP TTL (5 min); alert user via existing session on new-device login; consider secondary factor before high-value actions (payout release) | TODO |
| **S**poofing — forged CMI webhook claiming a payment/payout confirmation | Payment webhook | HMAC signature verification on every webhook payload; reject unsigned/invalid-signature requests; IP allowlist if CMI supports it | TODO |
| **T**ampering — Organizer edits ledger history to hide a missed contribution | Ledger | Append-only ledger (ADR-2) — no UPDATE/DELETE grants on `ledger_entry` at the DB role level, enforced beyond just app-layer logic | TODO |
| **T**ampering — request body modified in transit | All API traffic | TLS 1.2+ enforced everywhere, HSTS | TODO |
| **R**epudiation — Organizer denies resolving a dispute a certain way | Dispute module | Every dispute resolution logged with resolver's user ID, timestamp, and reason — immutable, queryable audit trail | TODO |
| **I**nformation Disclosure — one member views another group's ledger (IDOR) | Group/Ledger endpoints | Resource-level authorization check on every group-scoped endpoint (group membership required, not just "authenticated") — see architecture doc §5 and adversarial test checklist in test strategy doc | TODO |
| **I**nformation Disclosure — phone numbers/PII leaked via logs or error messages | Logging | Structured logging with PII scrubbing (never log full phone numbers, OTPs, tokens); error responses never include stack traces or raw exception messages to clients | TODO |
| **D**enial of Service — OTP endpoint abused to spam SMS costs or lock out users | Auth | Rate limit per phone number + per IP; exponential backoff after repeated requests | TODO |
| **D**enial of Service — CMI webhook endpoint flooded | Webhook | Rate limiting at reverse-proxy layer; webhook signature check rejects invalid traffic before it reaches business logic | TODO |
| **E**levation of Privilege — Member calls an Admin-only endpoint directly | Admin API | Method-level `@PreAuthorize` role checks on every admin endpoint, tested explicitly (not just relying on frontend hiding the UI) | TODO |
| **E**levation of Privilege — Organizer of Group A modifies Group B's data by guessing IDs | Group endpoints | Ownership/membership check per request, not just role check — this is the most common real-world break in apps like this | TODO |

## 3. Authentication Strategy
- **Type**: Phone-number OTP → JWT (access token ≤ 15 min TTL, refresh token ≤ 7 days, rotated on use with reuse detection)
- **MFA**: Not applicable in the traditional sense (OTP *is* the primary factor here). For **Admin** accounts specifically, require an additional factor (TOTP) given the elevated blast radius of an admin compromise — this is the one place MFA-on-top-of-OTP is worth the friction. Implemented Sprint 5 Batch 1 (story 1.4): RFC 6238 TOTP, 30s step, ±1 step clock skew, secret AES-GCM encrypted at rest (reuses `PII_ENCRYPTION_KEY`). Deliberately **no backup/recovery codes** and no self-service recovery flow — a locked-out admin is recovered via direct DB action by another admin/ops. Accepted MVP limitation, not a gap: admin accounts are few, ops-managed, and the alternative (backup codes) is its own attack surface that isn't worth building until there's evidence of real lockout pain. **Brute-force protection**: both TOTP verify endpoints (setup-activation and login-exchange) share a single failed-attempt counter/lockout on the `users` row (`mfa_failed_attempts`/`mfa_locked_until`) — 5 wrong codes locks further attempts for 15 minutes, mirroring the existing `otp_challenges.attempt_count` pattern. Caught and fixed during Sprint 5 Batch 1 VERIFY, before this had ever shipped: the endpoint that exchanges an MFA-pending token + code for real tokens (`POST /api/v1/auth/mfa/verify`) is public and was initially unthrottled — a 6-digit code (1,000,000 space, 3 valid values at any instant) with zero rate limiting would have undercut the entire point of adding MFA to protect the highest-blast-radius accounts.
- **OTP provider**: 🔶 **Not yet decided** (per your earlier answer — flagged as an explicit open TODO, not assumed). Whichever provider is chosen must support Morocco phone number delivery reliably; evaluate delivery success rate before committing.
- **Session management**: No server-side sessions (stateless JWT per ADR-3). Refresh tokens stored client-side; revocation via short access-token TTL + refresh-token blacklist-on-logout.

## 4. Authorization Model
- **Pattern**: Simple roles (`MEMBER`, `ORGANIZER`, `ADMIN`) + resource-level ownership checks. RBAC alone is insufficient here — nearly every endpoint also needs "is this user actually a member of *this* group" (see STRIDE Elevation-of-Privilege rows above). This is a **relationship check**, not just a role check, even though full ReBAC infrastructure is overkill at this scale (YAGNI) — implement as explicit service-layer checks.
- **Roles defined**:
  - `MEMBER`: default role for any authenticated user; can act within groups they belong to
  - `ORGANIZER`: a per-group attribute (a user can be Organizer of one group and Member of another), not a global role
  - `ADMIN`: platform-level, full visibility, no group-membership requirement
- **Resource-level checks**: Yes — every group-scoped endpoint verifies the requesting user's membership/role *in that specific group*, not just their global role.

## 5. Data Protection
- **PII fields**: phone number, full name, national ID (if collected for KYC — 🔶 confirm whether KYC/national-ID capture is required for MVP or deferred), group financial history
- **Encryption at rest**: PostgreSQL column-level encryption (or pgcrypto) for phone number and national ID fields; full-disk/volume encryption for the database at the infrastructure level
- **Encryption in transit**: HTTPS enforced end-to-end (client ↔ nginx ↔ Spring Boot), HSTS enabled, no plaintext fallback
- **Secrets management**: All secrets (JWT signing key, CMI API keys, OTP provider keys, DB credentials) via environment variables only — never committed to source. `.env.example` documents names/purpose with placeholders (see repo root); real values live in the deployment environment's secret store (see devops doc).

## 6. Regulatory & Compliance Considerations (flagged for legal review — not legal advice)

### 6.1 Payment services / e-money licensing
Morocco regulates payment services and payment institutions through Bank Al-Maghrib (BAM). Whether Tawfir needs a **payment institution license** (or must partner with an already-licensed payment institution / bank) depends heavily on the custody model chosen in System Design SDR-3, **confirmed non-custodial as of 2026-08-01**:
- Tawfir is **non-custodial**: members transfer funds directly to each other or via CMI's existing licensed rails, and Tawfir only *records* that a transfer occurred. The platform is closer to a record-keeping/software tool and regulatory exposure is lower under this model.
- The custodial alternative (holding member funds in an account Tawfir controls, even briefly, before disbursing payouts) was rejected — that model looks much more like operating as a payment institution / e-money issuer, which in most jurisdictions (Morocco included) requires licensing, capital requirements, and ongoing regulatory supervision.
- **Recommendation unchanged**: still get Moroccan legal counsel (fintech/banking specialist) to validate the non-custodial classification itself before any public launch — confirming the *model* doesn't remove the underlying regulatory question of whether even non-custodial record-keeping triggers any BAM notification/registration duty.

### 6.2 AML / KYC
Even in a non-custodial model, if the platform identifies users, tracks money flows, and could be used to move funds between parties, expect baseline AML/KYC expectations (customer identification, suspicious-activity awareness) to apply, especially if/when transaction volumes grow. Recommend at minimum: phone-verified identity (already in scope) + optional national-ID capture for higher-value groups, with a documented internal policy on when enhanced due diligence would apply. **Confirm specific AML obligations with counsel** — Bank Al-Maghrib and Morocco's financial intelligence unit (l'Unité de Traitement du Renseignement Financier, UTRF) publish applicable guidance.

### 6.3 Data protection
Morocco has a data protection framework administered by the CNDP (Commission Nationale de contrôle de la protection des Données à caractère Personnel), covering collection/processing of personal data including phone numbers and financial history. Recommend: data minimization (collect only what's needed for MVP), documented retention policy, and a privacy notice covering what's collected and why. **Confirm registration/notification obligations with counsel** — some data controllers are required to notify or seek authorization from the CNDP depending on data sensitivity and processing purpose.

### 6.4 Consumer protection
Group savings disputes are inherently about members' money. A clear, in-app dispute-resolution process (already in scope, FR-6) and transparent terms of service reduce both user harm and platform liability. Recommend legal review of the Terms of Service / group agreement template before launch.

## 7. Security Requirements for Dev Team
- [ ] All inputs validated server-side (never trust client-side validation alone — applies doubly given two separate frontends)
- [ ] Output encoded for context (HTML escaping in both React/Angular by default, but verify no `dangerouslySetInnerHTML` / Angular `bypassSecurityTrust*` usage without review)
- [ ] No secrets in code, logs, or error messages
- [ ] HTTPS only, security headers configured (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy)
- [ ] Dependencies scanned in CI (SCA) — see devops doc
- [ ] Every group-scoped endpoint has an automated test proving cross-group access is denied (IDOR regression test) — see test strategy doc adversarial checklist
- [ ] CMI webhook signature verification implemented and tested with invalid-signature rejection cases

### Security Validation Checklist
- [x] Threat model completed and top risks addressed (mitigations listed, implementation status TODO — expected at this doc stage)
- [x] Auth strategy chosen and justified
- [x] Authorization model defined with roles + resource-level checks
- [x] PII fields identified with protection plan
- [x] Security requirements handed off to dev team
- [ ] **Legal/regulatory confirmation still outstanding — recommend resolving before Sprint 2 execution begins**, since it affects the database schema (whether Tawfir stores its own ledger of "money held" vs. "money confirmed transferred elsewhere")
