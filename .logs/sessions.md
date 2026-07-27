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
