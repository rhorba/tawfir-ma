# DECISIONS — Tawfir.ma



## 2026-07-21 — Stack decisions (Sprint 1, UNDERSTAND phase)
- Backend: Java Spring Boot (Spring Security, Spring Data JPA) replaces the previously-noted Next.js API layer. PostgreSQL retained as the database.
- Frontend: TWO apps — React for member-facing app, Angular for admin panel.
- Deployment: Docker (Docker Compose to start; no k8s yet per YAGNI).
- Git: initialized locally, remote origin = https://github.com/rhorba/tawfir-ma.git, working branch feature/sprint-1-docs.

## 2026-07-21 — BRAINSTORM phase decision
Chosen approach: 🔴 COMPREHENSIVE. Full expert doc chain (PRD, System Design, Architecture, Security, Database, UX, UI, Test Strategy, DevOps, Stories) with detailed edge cases, multi-phase roadmap, Morocco fintech/BAM regulatory compliance deep-dive, full threat model, detailed API contracts.

## 2026-07-21 — Env vars & OTP provider
- .env.example will use placeholder values only (no real secrets yet); user to fill real values before first deploy.
- OTP/SMS provider not yet decided — docs will flag as an open TODO decision rather than assuming Twilio or a local gateway.

## 2026-07-22 — Sprint 2 env vars & mocking (UNDERSTAND phase)
- User: "just do default env vars and mock things like payments." Applies to:
  - All env vars → local Docker Compose dev defaults, written to .env.example (real secrets never committed — .gitignore added for .env).
  - OTP delivery → `OTP_PROVIDER=mock`, a MockOtpService logs the code instead of calling a real SMS gateway. Swappable later behind an interface once a real provider is picked (security-tawfir.md §3 still open).
  - CMI payments → `CMI_PROVIDER=mock`, a MockCmiClient simulates webhook confirmations for dev/test. Real CMI integration stays gated on SDR-3 (custody model) per stories-tawfir.md Sprint 4.
- Both mocks are implemented as interfaces (`OtpProvider`, `PaymentProvider` or similar) with a mock impl active by default, so swapping in a real provider later is a config + one new class, not a rewrite.

## 2026-07-22 — Sprint 2 BRAINSTORM decision
Chosen approach: 🔴 COMPREHENSIVE. Scaffold Spring Boot + React + Angular, stand up Docker Compose dev environment and the full CI pipeline (lint, test w/ 80% coverage gate, Semgrep/Trivy/Gitleaks security scan, build) from devops-tawfir.md BEFORE writing Epic 1/2 feature code — every commit from story 1.1 onward runs through the full gate.

## 2026-07-26 — Batch 2b decision
Admin login (frontend-admin): balanced approach chosen — client-side JWT role-claim decode after otp/verify; non-ADMIN roles are rejected locally (tokens cleared, error shown) rather than reaching the dashboard shell. Explicitly UX-only, not a security boundary — real server-side role enforcement is deferred to Batch 3 @PreAuthorize work on group endpoints.

## 2026-07-27 — Batch 3 decisions
1. total_cycles must equal member count exactly at finalize (classic tontine: every member contributes every cycle, receives exactly one payout across the rotation). Finalize rejects otherwise. Chosen over "cycles <= member count" to avoid an unspecified half-built case (extra contributing-only members with no payout slot).
2. Manual payout order is expressed by the order of the `members` array in the create-group request (position = array index + 1) — no extra staging column needed beyond what database-tawfir.md already defines; RANDOMIZED mode shuffles and assigns payout_position at finalize time (per stories-tawfir.md 2.2's "deterministic-once-set" requirement). MANUAL assigns from the stored array order at finalize too, so config stays mutable pre-finalize (architecture doc §5 PATCH note) without a redundant field.
3. Organizer is included as a regular entry in the `members` array (must supply their own phone number like everyone else); if omitted, they're auto-appended as the last member with role_in_group=ORGANIZER. Every other listed phone number gets role_in_group=MEMBER, auto-creating a User row if the phone hasn't registered yet (same auto-create-on-first-touch pattern as OTP verify in Batch 2a).

## 2026-07-27 — Sprint 3 decisions
1. Story 3.4 (auto-flag late): status flip only (PENDING/LATE scheduled job), no notification delivery — no SMS/push/email channel exists or is decided; matches this project's convention of flagging TODOs rather than half-building undecided infra.
2. Story 5.2 (resolve dispute): no auto-generated reversal ledger entry. PATCH /disputes/:id/resolve only sets status/resolver/reason. A financial correction, if the resolution needs one, is a manual follow-up (ledger ADJUSTMENT entries aren't built this sprint) — matches the story's own conditional wording ("if the resolution changes the financial record"), which isn't automatic by definition.
3. ADR-2 append-only enforcement: using a DB-level BEFORE UPDATE/DELETE trigger on ledger_entries that raises an exception, NOT a REVOKE UPDATE/DELETE grant. Reason: docker-compose/.env define a single Postgres role (`tawfir`) that both owns the table (runs migrations) and is what the app connects as — REVOKE has no effect on a table's owner in Postgres, so REVOKE would silently not enforce anything in this deployment topology. ADR-2 explicitly allows either mechanism; the trigger is the one that actually works here.
4. Race-condition safety (test-strategy §4 "Race conditions" + "Ledger & financial integrity"): mark-paid, confirm, and dispute-resolve all use an atomic conditional UPDATE ... WHERE status = <expected> (compare-and-swap at the SQL level) rather than read-then-write, so concurrent requests can't both succeed and double-append a ledger entry or double-resolve a dispute.

## 2026-07-28 — Sprint 3 Batch 2 decisions (Disputes, Story 5.1/5.2)
1. Opening a dispute (5.1) is scoped to CONFIRMED contributions only, matching the Gherkin scenario ("Given a CONFIRMED contribution...") and the `disputes.ledger_entry_id NOT NULL` constraint already migrated in V6 (Batch 1) — a ledger entry only exists once a contribution is organizer-confirmed, since `ContributionService.confirm()` is the only place a LedgerEntry gets created. The story's Technical Note ("a member can dispute another member's marked-paid claim") would need disputes to reference a contribution_schedule directly instead of only a ledger_entry_id; not implemented — flagged as a TODO/schema-change rather than silently disputing a MARKED_PAID contribution with no ledger entry to point at.
2. Resolving a dispute (5.2) only updates the Dispute row itself (status/resolved_by_user_id/resolution_reason/resolved_at) via the same CAS pattern as item 4 above (OPEN -> ACCEPTED/REJECTED). It does not automatically revert the related contribution_schedule's DISPUTED status — consistent with the already-logged decision that any financial correction is a manual follow-up (no auto-reversal ledger entry this sprint); the schedule stays DISPUTED until a manual admin/organizer follow-up, which is out of this sprint's scope.

## 2026-07-31 — Sprint 5 Batch 2 decisions (Admin dashboard, Story 6.1)
1. Authorization: `AdminController` uses the same manual `requireAdmin(Authentication)` check already established in `MfaController`/`GroupController.isAdmin(...)` (throws `ForbiddenException` -> 403), not `@PreAuthorize` — this codebase has no `@EnableMethodSecurity` configured anywhere, so `@PreAuthorize` would silently be a no-op. story-6.1's technical notes mention `@PreAuthorize` loosely; the existing precedent takes priority.
2. `defaultRatePercent` (PRD FR-9 "platform-wide default rate") = share of past-due `contribution_schedules` currently in `LATE` status, out of all past-due schedules regardless of final status (LATE / (LATE + CONFIRMED + MARKED_PAID among rows with due_date < today)); 0.0 when there are no past-due rows yet, not a divide-by-zero.
3. `atRiskGroups` — no formal definition exists anywhere in the docs (only qualitative: ux-tawfir.md's Fatima persona wants to "spot at-risk groups (rising defaults/disputes)"). Defined here as: the count of distinct groups with at least one `LATE` contribution_schedule OR at least one `OPEN` dispute (union, de-duplicated by group id). Simplest heuristic that matches the qualitative goal; revisit with a weighted/threshold-based score if real usage shows this is too coarse.
4. `GET /api/v1/admin/groups`'s `cyclesCompleted` counts a cycle as completed once every member's `contribution_schedule` row for that cycle is `CONFIRMED` — consistent with story 7.1's Batch 3 "cycle completion" proxy definition (also gated on all-contributions-confirmed, not real payout execution, since Epic 4 doesn't exist yet).

## 2026-07-31 — Sprint 5 Batch 3 decisions (Savings history snapshot, Story 7.1)
1. "Cycle completion" trigger: `ContributionService.confirm()` checks, after each successful CONFIRMED transition, whether every `contribution_schedule` row for that cycle number is now CONFIRMED; if so, records one `savings_history_snapshots` row per group member. This is a proxy for real payout execution (Epic 4, blocked on SDR-3) — same precedent as Batch 2's `cyclesCompleted`/`atRiskGroups` — revisit once Epic 4 lands and a real "payout executed" event exists.
2. `on_time_rate` needed a way to know whether a now-CONFIRMED contribution was ever LATE, which the `status` column alone can't answer (MARKED_PAID/CONFIRMED overwrite LATE). Added `contribution_schedules.was_late` (V8, boolean, set once by `LateContributionScheduler`, never cleared) rather than trying to reconstruct it from ledger timestamps (the ledger entry's `created_at` is when the organizer confirmed, not when the member actually paid, so it can't answer this either).
3. `disputes_involved` counts disputes in the group whose underlying contribution's payer is that member (Dispute -> LedgerEntry.contributionScheduleId -> ContributionSchedule.userId) — i.e. how many times a member's own contribution has been disputed, matching FR-8's "portable reliability record" framing. Computed in-memory per snapshot run (small MVP group sizes) rather than a multi-table JPQL join, since no JPA `@ManyToOne` associations exist between these entities (this codebase uses plain UUID FK columns throughout).
4. `GET /api/v1/users/:id/savings-history` is self-or-ADMIN, not membership-scoped like group endpoints — a user's savings history spans every group they've ever been in, including ones the requester (if not the user themself or an admin) has no relationship to at all.

## 2026-07-31 — Sprint 4 decisions (CMI webhooks + payouts, stories 3.3/4.1-4.3)
1. Unblocked from SDR-3: build the full webhook/payout code path against `MockCmiClient` (already scaffolded, Sprint 2 Batch 1) with real HMAC signature verification — only real money movement (real CMI credentials, real custody model) stays gated on SDR-3, same precedent as OTP being mocked throughout Sprints 2-5.
2. Webhook signature contract (undocumented in architecture/security docs beyond "HMAC verification" — no real CMI API spec available): `X-Cmi-Signature` header = hex(HMAC-SHA256(raw request body, CMI_WEBHOOK_SECRET)), verified via `MessageDigest.isEqual` (constant-time). Invalid/missing -> 401 (matches this codebase's existing 401-not-403 convention for auth-adjacent failures, security-tawfir.md §4 adversarial checklist).
3. Contribution webhook confirm (3.3) is allowed to transition from {PENDING, LATE, MARKED_PAID} -> CONFIRMED, not just MARKED_PAID like the organizer-confirm path — a real CMI payment confirmation is stronger evidence than a member's self-reported "I paid," so it doesn't need the member to have marked-paid first. Idempotent: replaying a webhook for an already-CONFIRMED contribution is a no-op, not an error (test-strategy §4).
4. New `LedgerSource.SYSTEM_SCHEDULED` (migration V9, widens the `ledger_entries.source` CHECK constraint) for the payout cron's auto-executions (story 4.1) — no existing value fit "nobody, the system did this on schedule." Reused `ORGANIZER_CONFIRMED` for the organizer's manual payout override (4.2, same semantic family: an organizer took the action) and `CMI_WEBHOOK` for the payout-confirmation webhook (4.3, already fits) rather than adding two more near-duplicate enum values.
5. Payout cron (4.1) leaves a not-yet-ready payout PENDING with no notification delivery — same accepted-TODO precedent as story 3.4's late-contribution flagging (no SMS/push/email channel decided). The organizer discovers it needs manual override (4.2) by checking the group, not by being proactively notified.
6. `PayoutStatus.FAILED` is set if `CmiClient.initiateTransfer` throws; logged, no ledger entry appended, cron continues to the next payout in the batch rather than aborting. No retry queue — accepted MVP limitation, matching this project's general pattern of flagging rather than half-building undecided infra.
