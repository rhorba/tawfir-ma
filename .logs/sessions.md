# SESSIONS — Tawfir.ma



## SESSION_START — 2026-07-21
Sprint 1 kickoff: document-first foundation for Tawfir.ma (new project, first session).
Repo git-initialized (main + feature/sprint-1-docs), remote set to https://github.com/rhorba/tawfir-ma.git.

### [2026-07-21 SESSION_END]
- **Completed**: Batch 1 (PRD, System Design, Architecture) + Batch 2 (Security, Database, UX, UI) drafted — 7/10 foundation docs written to docs/. User reviewed Batch 1 (approved), Batch 2 not yet explicitly approved/rejected.
- **In progress**: Batch 3 not started — Test Strategy, DevOps, Stories still pending.
- **Blocked**: Nothing technical. One open product/legal decision: payment custody model (non-custodial vs custodial — see system-design-tawfir.md SDR-3 and security-tawfir.md §6.1). Docs assume non-custodial as the safer default; user has not confirmed.
- **Next session**: Draft Batch 3 (docs/test-strategy-tawfir.md, docs/devops-tawfir.md, docs/stories-tawfir.md), get final approval on all 10 docs, THEN commit+push per rule 13 (git add docs/ && commit && push origin feature/sprint-1-docs). No push has happened yet — correctly withheld since the full doc chain isn't approved.
- **Open issues**: None logged.
- **Open risks**: Custody-model / BAM licensing decision outstanding (see risks.md).
---

## SESSION_START — 2026-07-22 (resumed)
Resumed from prior SESSION_END: drafting Batch 3 to close out Sprint 1's foundation-doc chain.

### [2026-07-22 SESSION_END]
- **Completed**: Batch 3 drafted (Test Strategy, DevOps, Stories) — all 10/10 foundation docs done. User approved the full chain. Committed (b374ba6) and pushed to origin/feature/sprint-1-docs. Sprint 1 (SHIP phase) closed.
- **In progress**: Nothing — Sprint 1 scope (foundation docs) is fully delivered.
- **Blocked**: Sprint 2 execution can start technically, but two open decisions should be resolved first: (1) payment custody model (SDR-3, non-custodial assumed as default — see risks.md), (2) OTP/SMS provider (unpicked, flagged across security/devops docs).
- **Next session**: Per stories-tawfir.md Sprint Allocation — start Sprint 2 (Epic 1 Auth + Epic 2 Group Lifecycle, stories 1.1-1.3, 2.1-2.4). Before EXECUTE begins: collect real .env values (CLAUDE.md rule 10), confirm custody model if possible, pick OTP provider if possible (otherwise proceed with the manual mark-paid/confirm fallback already designed for this).
- **Open issues**: None logged.
- **Open risks**: Custody-model / BAM licensing decision still outstanding (see risks.md) — now specifically blocks Sprint 4 (stories 3.3, 4.1-4.3, CMI webhook integration) per stories-tawfir.md.
---

## SESSION_START — 2026-07-22 (Sprint 2 kickoff, same-day continuation)
User confirmed start of Sprint 2 (Auth + Group Lifecycle), picked 🔴 COMPREHENSIVE approach (scaffold+Docker+CI before feature code), approved dev-default env vars with OTP/CMI mocked.

### [2026-07-22 SESSION_END]
- **Completed**: Sprint 2 Batch 1 (Scaffold, Docker, CI) fully built and verified locally — see metrics.md and activity.md for full detail. Backend (Spring Boot 4.1/Java 21), React member app, Angular admin app, docker-compose.yml, .github/workflows/ci.yml, .env.example, .gitignore all created. All three services pass lint + tests + 80% coverage gate locally. Full docker-compose stack smoke-tested end-to-end (then reverted to canonical ports).
- **In progress**: Nothing mid-file — Batch 1 is in a clean, working state.
- **Blocked**: Nothing technical. User paused the session before deciding whether to commit+push Batch 1 now (to validate the GitHub Actions CI pipeline goes green for real) or continue straight to Batch 2 first — **this decision is still open, ask at the start of next session.**
- **Not yet committed**: Everything from this session is uncommitted (`git status` shows: backend/, frontend-member/, frontend-admin/, docker-compose.yml, .github/, .env.example, .gitignore all untracked; .logs/*.md modified). Nothing has been pushed. Do not assume it's safe to discard — this is a full session's work.
- **Next session**:
  1. Ask user: commit+push Batch 1 now to verify CI on GitHub Actions, or proceed to Batch 2 (Auth: stories 1.1-1.3) first?
  2. If pushing: `git add` the new dirs/files + modified .logs, commit, push to `feature/sprint-1-docs` (current branch — confirm this is still the intended branch for Sprint 2 code, or whether a new branch like `feature/sprint-2-auth-groups` should be cut instead, since the branch name references "sprint-1-docs" specifically).
  3. Then continue with Batch 2 (Epic 1 Auth) per the plan logged in activity.md's "Sprint 2 PLAN phase" entry.
- **Open issues**: None logged.
- **Open risks**: Custody-model decision (risks.md) still blocks Sprint 4. No new risks from Batch 1.
---

## SESSION_START — 2026-07-23 (continuation)
Resumed from prior SESSION_END. User confirmed: commit+push Batch 1 now (rather than continue to Batch 2 first), and cut a new branch `feature/sprint-2-auth-groups` instead of continuing on `feature/sprint-1-docs`.

### [2026-07-23 SESSION_END]
- **Completed**:
  - Sprint 2 Batch 1 (scaffold/Docker/CI) committed+pushed to new branch `feature/sprint-2-auth-groups`. CI was red on first push (3 rounds of fixes: `trivy-action` version tag didn't exist → `v0.28.0` → its own `setup-trivy` pin was dead → `v0.36.0` which pins by commit SHA; `backend/mvnw` missing exec bit; real HIGH-severity CVE-2026-54291 in postgresql driver → bumped to 42.7.12). Green as of commit `7da77b3`.
  - Sprint 2 Batch 2a (Epic 1 Auth backend, stories 1.1-1.3): OTP request/verify, JWT access+refresh, refresh rotation with reuse detection, logout. Flyway migrations added (users, otp_challenges, refresh_tokens). jjwt 0.12.6, BCrypt for OTP hashing, SHA-256 for refresh-token hashing. 40 tests, checkstyle + 80% coverage gate all pass. Pushed as commit `ae142e4`, CI green on first try (`9853198` logs it).
- **Document-first deviations logged in database-tawfir.md** (per rule 12): added `refresh_tokens` table (missing from v1.0 schema, needed for rotation/reuse-detection); relaxed `users.full_name` to nullable (auto-created on first OTP verify, no name collected yet). `users.phone_number` encryption-at-rest is explicitly **deferred, not implemented** — flagged in the doc as needing a blind-index design (AES-GCM's random IV breaks the UNIQUE/login-lookup constraint) — must land before any shared/staging environment holds real phone numbers.
- **Bug caught during VERIFY** (fixed before ship): reuse-detected refresh-token family revocation was being silently rolled back by the same `@Transactional` method's own thrown exception. Fixed via `RefreshTokenRevocationService` with `REQUIRES_NEW` propagation. Only the full-stack Testcontainers integration test caught this — worth remembering that mocked-repository unit tests can't see real transaction/rollback behavior.
- **In progress**: Nothing mid-file — Batch 2a is in a clean, shipped state.
- **Not yet done**: Sprint 2 Batch 2b — React member app (phone entry + OTP verify screens) and Angular admin app (login screen) wiring to the now-complete auth API. Backend has no frontend consumer yet.
- **Next session**: Start Batch 2b per the plan in activity.md's "Sprint 2 PLAN phase" entry (2.4 React screens, 2.5 Angular admin login). After that, Batch 3 (Epic 2 Group Lifecycle, stories 2.1-2.4) closes out Sprint 2.
- **Open issues**: None logged.
- **Open risks**: (1) Custody-model / BAM licensing decision (risks.md) still blocks Sprint 4. (2) `users.phone_number` plaintext storage — must be resolved (blind-index encryption) before any shared/staging deploy. (3) Per-IP OTP rate limiting still an open TODO (security-tawfir.md §3) — only phone-based limiting exists.
---

## SESSION_START — 2026-07-26 (continuation)
Resumed from prior SESSION_END (2026-07-23). Working tree clean, branch feature/sprint-2-auth-groups up to date with origin. Picking up Batch 2b: React member app (phone entry + OTP verify screens) and Angular admin app (login screen), wiring to completed auth API.

### [2026-07-27 SESSION_END]
- **Completed**: Sprint 2 Batch 2b — React member app Login screen and Angular admin app login screen both wired to the auth API (otp/request, otp/verify), with loading/error states and token persistence. Admin login adds a client-side JWT role-claim gate (balanced approach, decisions.md) rejecting non-ADMIN accounts locally. Bug caught+fixed in VERIFY: shared apiFetch() mishandled the 202-empty-body response from /otp/request. Committed a41fece, pushed, CI went RED on a real Trivy finding (GHSA-qwww-vcr4-c8h2 in react-router-dom 7.18.1) — fixed by migrating frontend-member from react-router-dom onto react-router@8.3.0 directly (react-router-dom is now a frozen v7 re-export shim). Re-pushed e929327, CI GREEN (all 5 jobs). Sprint 2 Batch 2b SHIP phase closed.
- **In progress**: Nothing mid-file — Batch 2b is in a clean, shipped state.
- **Not yet done**: Sprint 2 Batch 3 — Epic 2 Group Lifecycle (stories 2.1-2.4: create-group draft, payout order config, finalize/schedule generation, GET /groups + /groups/:id, React create-group + group-list/detail screens). This is the last batch of Sprint 2.
- **Next session**: Start Batch 3 per the plan already logged in this file's "Sprint 2 PLAN phase" entry (2026-07-22). No video recording triggered yet (CLAUDE.md rule 9) — Sprint 2 isn't complete until Batch 3 ships; re-evaluate at that point since it will be the first user-facing full vertical slice (auth + groups).
- **Open issues**: None logged.
- **Open risks**: (1) Custody-model / BAM licensing decision (risks.md) still blocks Sprint 4. (2) `users.phone_number` plaintext storage — must be resolved (blind-index encryption) before any shared/staging deploy. (3) Per-IP OTP rate limiting still an open TODO (security-tawfir.md §3). (4) No backend-side ADMIN role enforcement exists yet (@PreAuthorize) — today's admin-app login gate is client-side/UX-only; must land with Batch 3's protected group endpoints before this is a real security boundary.
---

### [2026-07-27 SESSION_END]
- **Completed**: Sprint 2 Batch 3 (Epic 2 Group Lifecycle, stories 2.1-2.4) — POST /api/v1/groups (create draft, auto-registers unregistered member phones), POST /groups/:id/finalize (organizer-only, member-count-must-equal-total-cycles, payout position assignment for both MANUAL/RANDOMIZED, contribution+payout schedule generation), GET /groups + GET /groups/:id (membership-scoped, 403 for non-members, ADMIN bypass). React member app fully wired: Create Group (collects cycles/payout-order/members), Groups list, Group detail (roster + organizer-gated Finalize button). Committed 800a66d, pushed, CI green first try. Log-only follow-up commit 1ca138a also pushed and green (run 30273731336). Sprint 2 is now fully closed — all 4 batches (scaffold/CI, auth backend, auth frontend, group lifecycle) shipped and green.
- **In progress**: Nothing — Batch 3 and Sprint 2 are in a clean, shipped state.
- **Not yet done**: Sprint 3 not yet planned/started (per stories-tawfir.md Sprint Allocation, likely Epic 3 Contributions: mark-paid/confirm workflow, ledger). Angular admin Groups/GroupDetail screens remain placeholders (out of Sprint 2 scope). Contribution/payout schedule display, ledger, and disputes views on the React GroupDetail screen are still "No activity yet." placeholders — the data model exists (contribution_schedules/payout_schedules generated at finalize) but no read endpoint or UI consumes it yet.
- **Next session**: Confirm Sprint 3 scope with user (likely Epic 3 per stories-tawfir.md), UNDERSTAND -> BRAINSTORM -> PLAN per the standard workflow before EXECUTE.
- **Open issues**: None logged.
- **Open risks**: (1) Custody-model / BAM licensing decision (risks.md) still blocks Sprint 4 (CMI webhook integration). (2) `users.phone_number` plaintext storage — must be resolved (blind-index encryption) before any shared/staging deploy. (3) Per-IP OTP rate limiting still an open TODO. (4) Admin-app role gate and React GroupDetail's organizer-gated Finalize button are both client-side/UX-only — real enforcement is server-side (@PreAuthorize / service-layer checks), already correctly implemented on the backend for groups; just noting the client gates are UX sugar, not security boundaries, should either app's client code regress.
---

## 2026-07-27 — SESSION_START (Sprint 3 kickoff, continuation)
Resumed from prior SESSION_END (Sprint 2 fully closed, all batches shipped/green). Presenting Sprint 3 scope per stories-tawfir.md Sprint Allocation for user confirmation before PLAN/EXECUTE.

### [2026-07-28 SESSION_END]
- **Completed**: Sprint 3 Batch 1 backend is functionally built and mostly verified: LedgerEntry + Dispute entities/migrations (V5 ledger_entries + append-only DB trigger per ADR-2, V6 disputes), POST mark-paid, POST confirm (appends ledger entry), GET contributions list, and the LateContributionScheduler job — all with unit (ContributionServiceTest, 9/9 pass), slice (ContributionControllerTest, 7/7 pass), and full-stack Testcontainers (ContributionLifecycleIntegrationTest) tests.
- **In progress / NOT verified**: ContributionLifecycleIntegrationTest was at 5/6 passing. The 6th failure (`memberMarksPaid_organizerConfirms_ledgerEntryAppended` — mark-paid returned HTTP 200 but the response body still showed `status: PENDING` instead of `MARKED_PAID`) was root-caused to Hibernate's persistence-context cache serving a stale entity on the second `findById` within the same transaction, because the `@Modifying` CAS queries bypass the first-level cache. Fix applied: added `clearAutomatically = true` to both `@Modifying` queries in `ContributionScheduleRepository` (compareAndSetStatus, flagOverdueAsLate) — **this fix has not been re-run/confirmed yet**, the test suite was mid-run when the session was stopped.
- **Not yet done**: Confirm the clearAutomatically fix actually resolves the last failing integration test; then run full `mvnw verify` (all tests + Checkstyle + JaCoCo 80% gate) before considering Batch 1 done. Batch 2 (Disputes: 5.1/5.2 endpoints — entities/migrations/repository already exist from this session, service+controller+tests still needed) and Batch 3 (React frontend wiring for contributions/disputes) haven't been started.
- **Nothing committed or pushed** — all Batch 1 work above is uncommitted in the working tree (see `git status` — new files under `ma.tawfir.api.ledger`, `ma.tawfir.api.dispute`, `ma.tawfir.api.group` contribution/scheduler files, V5/V6 migrations, modified TawfirApiApplication.java/GlobalExceptionHandler.java/ContributionScheduleRepository.java). Do not assume it's safe to discard — this is a full session's work in progress.
- **Next session**: 1) Re-run `mvnw -o test -Dtest=ContributionLifecycleIntegrationTest` to confirm the clearAutomatically fix. 2) If green, run full `mvnw verify`, then commit+push+monitor CI to close Batch 1 (VERIFY/SHIP steps of task #20, still pending). 3) Then Batch 2 (Disputes) and Batch 3 (React) per the Sprint 3 plan already logged in activity.md (2026-07-27 PLAN phase entry).
- **Open issues**: None logged beyond the unverified fix above.
- **Open risks**: Unchanged from prior session (custody-model/SDR-3 blocking Sprint 4, phone_number plaintext storage, per-IP OTP rate limiting, client-side-only UX gates on admin role / group finalize).
---
