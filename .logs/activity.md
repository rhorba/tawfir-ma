# ACTIVITY — Tawfir.ma



## 2026-07-21 — PLAN phase (Sprint 1)
Comprehensive doc chain planned in 3 batches (see decisions.md / chat). docs/ directory created.

## 2026-07-21 — Batch 1 drafted (EXECUTE phase)
MILESTONE: docs/prd-tawfir.md drafted (pending user approval)
MILESTONE: docs/system-design-tawfir.md drafted (pending user approval)
MILESTONE: docs/architecture-tawfir.md drafted (pending user approval)
Open decision flagged to user: payment custody model (non-custodial default assumed, see SDR-3).

## 2026-07-21 — Batch 2 drafted (EXECUTE phase)
MILESTONE: docs/security-tawfir.md drafted (pending user approval) — includes regulatory flags (BAM licensing, AML/KYC, CNDP data protection) for legal review, not legal advice
MILESTONE: docs/database-tawfir.md drafted (pending user approval) — full schema, append-only ledger enforcement plan
MILESTONE: docs/ux-tawfir.md drafted (pending user approval)
MILESTONE: docs/ui-tawfir.md drafted (pending user approval) — shared token strategy across React + Angular

## 2026-07-22 — SESSION_START (resumed)
Resumed per .logs/sessions.md last SESSION_END. Continuing Batch 3 (Test Strategy, DevOps, Stories) — the 3 remaining foundation docs.

## 2026-07-22 — Batch 3 drafted (EXECUTE phase)
MILESTONE: docs/test-strategy-tawfir.md drafted (pending user approval) — risk-tiered test plan, ATDD scenarios for every FR, adversarial checklist (ledger/auth/IDOR/webhook), 80% coverage gate, release criteria
MILESTONE: docs/devops-tawfir.md drafted (pending user approval) — 3-env strategy, CI pipeline contract (lint/test/security-scan/build/deploy), Docker setup, env var contract for .env.example
MILESTONE: docs/stories-tawfir.md drafted (pending user approval) — 7 epics / 17 stories mapping every PRD FR to a story with acceptance criteria; Sprint 4 (CMI integration) flagged as blocked on SDR-3 custody-model decision
All 10 foundation docs now drafted. Awaiting final user approval before commit+push per CLAUDE.md rule 13.

## 2026-07-22 — SHIP phase (Sprint 1 close)
MILESTONE: User approved full 10-doc foundation chain.
PUSH: commit b374ba6 "docs: foundation documents for Tawfir.ma" pushed to origin/feature/sprint-1-docs (new branch on remote). No code shipped this sprint — docs only, so 80% coverage gate and video-recording rules (CLAUDE.md rules 6/9) don't apply yet; they activate starting Sprint 2 once code exists.

## 2026-07-22 — Sprint 2 PLAN phase
Comprehensive approach chosen (see decisions.md). Plan: Batch 1 Scaffold+CI+Docker → Batch 2 Epic 1 Auth → Batch 3 Epic 2 Group Lifecycle. Snapshot below; awaiting user confirmation before EXECUTE starts (gate per CLAUDE.md rule 5).

📋 BATCH 1: Scaffold, Docker, CI
  1.1 Spring Boot skeleton (Maven, package-by-feature per ADR-1, Spring Security/Data JPA deps, application.yml reading env vars)
  1.2 React member app skeleton (Vite/CRA, Tailwind, routing shell per ux-tawfir.md IA)
  1.3 Angular admin app skeleton (Angular CLI, Material, routing shell)
  1.4 Docker Compose dev environment (postgres, spring-api, nginx, react-member, angular-admin) wired to .env
  1.5 GitHub Actions CI pipeline (lint -> test+coverage-gate -> Semgrep/Trivy/Gitleaks -> build) per devops-tawfir.md
  1.6 MockOtpService + MockCmiClient interfaces wired in (per Sprint 2 mocking decision)

📋 BATCH 2: Epic 1 - Auth (stories 1.1-1.3)
  2.1 POST /auth/otp/request (rate-limited, MockOtpService) + tests
  2.2 POST /auth/otp/verify -> JWT issue + tests
  2.3 POST /auth/refresh, POST /auth/logout (rotation + reuse detection) + tests
  2.4 React: phone entry + OTP verify screens wired to API
  2.5 Angular: admin login screen wired to API (TOTP/story 1.4 deferred - Should, not Must)

📋 BATCH 3: Epic 2 - Group Lifecycle (stories 2.1-2.4)
  3.1 POST /groups (create draft) + validation + tests
  3.2 Payout order config (manual/randomized) + tests
  3.3 POST /groups/:id/finalize -> schedule generation + tests
  3.4 GET /groups, GET /groups/:id (membership-scoped auth) + tests
  3.5 React: create-group flow + group list/detail screens wired to API

Each batch ends with VERIFY (tests + coverage check) before moving on; CI monitored red->green on every push per rule 11.

## 2026-07-22 — Sprint 2 Batch 1 EXECUTE + VERIFY
MILESTONE: Spring Boot 4.1.0/Java 21 backend scaffolded (backend/) — package-by-feature (config/, otp/, payment/), Spring Security baseline (deny-by-default except /actuator/health), TawfirProperties config binding, JaCoCo 80% coverage gate, Checkstyle (google_checks) lint gate, Testcontainers-based Postgres integration test.
MILESTONE: MockOtpProvider + MockCmiClient implemented behind OtpProvider/CmiClient interfaces, active by default via tawfir.otp.provider=mock / tawfir.payment.provider=mock, unit tested.
MILESTONE: React member app scaffolded (frontend-member/) — Vite+React 19+Tailwind 4, routing shell (Login/GroupsList/GroupDetail/CreateGroup/Profile) matching ux-tawfir.md IA, Vitest+Testing Library, 80% coverage gate.
MILESTONE: Angular admin app scaffolded (frontend-admin/) — Angular 22 (zoneless, signals), Material, routing shell (Login/Dashboard/GroupsList/GroupDetail/Disputes), Vitest-based `ng test`, 80% coverage gate, ESLint.
MILESTONE: docker-compose.yml (postgres, backend, frontend-member, frontend-admin) — verified end-to-end locally with a temporary port override (other unrelated projects on this machine occupy 8080/3000/4200/5432); reverted to canonical ports before commit.
MILESTONE: .github/workflows/ci.yml — per-service lint -> test+coverage-gate -> build, plus a security-scan job (Semgrep/Gitleaks/Trivy) and a final docker-build-images gate job depending on all of the above.
VERIFY: all three services pass lint + tests + coverage gate locally (see metrics.md). CI workflow not yet exercised on GitHub Actions — pending push.
Deviation from test-strategy-tawfir.md §2: frontend tooling is Vitest (not Jest) for both React and Angular — Angular 22 defaults to Vitest now, and Vitest is the natural fit for the Vite-based React app; functionally equivalent, noted here rather than silently diverging from the approved doc.

## 2026-07-23 — Sprint 2 Batch 1 SHIP phase
Branch cut: feature/sprint-2-auth-groups (from feature/sprint-1-docs tip), per user decision — Sprint 2+ code now lives on its own branch rather than the docs-named branch.
PUSH: committing Batch 1 (backend/, frontend-member/, frontend-admin/, docker-compose.yml, .github/workflows/ci.yml, .env.example, .gitignore, CLAUDE.md, README.md, .claude/) and pushing to origin/feature/sprint-2-auth-groups per CLAUDE.md rule 7. CI monitoring (rule 11) follows immediately after push.

## 2026-07-23 — CI monitoring (rule 11)
CI run 29989911979 on push (commit d48248c): RED. Two failures:
1. Security scan job: `aquasecurity/trivy-action@0.24.0` does not exist (tags use `v` prefix, e.g. `v0.28.0`) — action failed to resolve entirely.
2. Backend Lint (Checkstyle) job: `./mvnw: Permission denied` (exit 126) — mvnw wrapper script was committed without the executable bit (100644 instead of 100755), likely lost on Windows checkout/commit.
Fix: pinned trivy-action to `@v0.28.0`; ran `git update-index --chmod=+x backend/mvnw` to restore the exec bit in the index. Re-pushing; monitoring for green per rule 11 (stop-the-line until resolved).

Re-run 29990663338 (commit 3190987): Backend, Frontend Member, Frontend Admin all GREEN. Security scan still RED — different cause: trivy-action@v0.28.0's own action.yaml pins `aquasecurity/setup-trivy@v0.2.1`, a tag that no longer exists upstream (deleted/renamed). Verified aquasecurity/trivy-action@v0.36.0 pins setup-trivy by commit SHA instead of a tag, avoiding this class of breakage going forward. Updated to v0.36.0; verified semgrep/semgrep-action@v1 and gitleaks/gitleaks-action@v2 tags both resolve before re-pushing.

Re-run 29992241374 (commit 6a865d0): Backend, Frontend Member, Frontend Admin all GREEN. Security scan job itself ran correctly this time (Semgrep, Gitleaks, Trivy all executed) but Trivy found a real HIGH-severity finding: CVE-2026-54291 in org.postgresql:postgresql 42.7.11 (SCRAM-SHA-256-PLUS downgrade MITM), fixed in 42.7.12. This is a genuine vuln, not a CI config bug. Fix: added `<postgresql.version>42.7.12</postgresql.version>` property to backend/pom.xml to override Spring Boot 4.1.0's managed version; verified locally via `mvnw dependency:tree -Dincludes=org.postgresql` that it now resolves to 42.7.12.

Run 29996490529 (commit 9b4554b): GREEN — all 5 jobs pass (Backend, Security scan, Frontend Member, Frontend Admin, Build Docker images). Sprint 2 Batch 1 SHIP phase complete: scaffold+CI+Docker pushed to origin/feature/sprint-2-auth-groups and verified green on GitHub Actions per rule 11. Deprecation warnings noted (actions forced onto Node 24 runtime) — non-blocking, no action needed now.

## 2026-07-23 — Sprint 2 Batch 2a EXECUTE + VERIFY (Epic 1 Auth, backend — stories 1.1-1.3)
Document-first per rule 12: added `refresh_tokens` table to database-tawfir.md (needed for rotation/reuse-detection, missing from v1.0 schema) and relaxed `users.full_name` to nullable (account auto-created on first OTP verify, before any name is collected). `users.phone_number` encryption-at-rest (§7) explicitly deferred — flagged in the doc as needing a blind-index design (AES-GCM's random IV breaks the UNIQUE/equality lookup needed for login), not implemented this batch; must land before any shared/staging environment holds real numbers.

MILESTONE: Flyway migrations (V1 users+otp_challenges, V2 refresh_tokens) — `spring-boot-starter-flyway` + `flyway-database-postgresql` added; `ddl-auto: validate` now actually validates against a real, migrated schema.
MILESTONE: POST /api/v1/auth/otp/request, /otp/verify, /refresh, /logout implemented (ma.tawfir.api.auth) — jjwt 0.12.6 for JWT (HS256 only, alg-confusion-proof by construction), BCrypt for OTP code hashing, SHA-256 for refresh-token hashing (high-entropy secret, not a low-entropy human code — BCrypt would be pointless cost there). Phone-based OTP rate limiting (5/10min, DB-backed) implemented; per-IP limiting intentionally left as the still-open TODO security-tawfir.md §3 already flags it as, rather than half-building an in-memory limiter that wouldn't survive horizontal scaling anyway.
MILESTONE: Refresh-token rotation + reuse detection (RefreshToken entity: family_id/replacedById/revokedAt) with family-wide revocation on reuse.
MILESTONE: SecurityConfig wired to JwtAuthenticationFilter; /api/v1/auth/** public, everything else authenticated; explicit 401 AuthenticationEntryPoint (Spring Security's undecorated default is 403, which conflicts with test-strategy-tawfir.md §4's adversarial checklist requiring 401).
BUG CAUGHT IN VERIFY (fixed before ship): reuse-detected family revocation was being silently undone — `AuthService.refresh()` is `@Transactional`, and throwing `InvalidTokenException` after calling `revokeFamily()` rolled back that same transaction, erasing the revocation it was supposed to enforce. Fixed by extracting revocation into `RefreshTokenRevocationService` with `@Transactional(propagation = REQUIRES_NEW)`, so it commits independently of the caller's outcome. Caught by AuthFlowIntegrationTest's full-stack reuse scenario (a mocked-repository unit test could not have caught this — real transactional behavior only shows up against a real DB).
VERIFY: 40 tests pass (unit: JwtService, AuthService, JwtAuthenticationFilter, RefreshTokenRevocationService; slice: AuthController; full-stack Testcontainers: AuthFlowIntegrationTest covering rate-limit/expired-OTP/reuse-detection/logout/unauthenticated-401 per test-strategy-tawfir.md §4 adversarial checklist). Checkstyle + 80% JaCoCo coverage gate both pass locally (`mvnw verify`).
Not yet done: React/Angular login screens wiring (Batch 2b) — backend auth API is complete and tested but has no frontend consumer yet.

## 2026-07-23 — Sprint 2 Batch 2a SHIP phase
PUSH: commit ae142e4 pushed to origin/feature/sprint-2-auth-groups. CI run 30015988321: GREEN on first try (all 5 jobs) — no fixes needed this time, unlike Batch 1.

## 2026-07-26 — Sprint 2 Batch 2b PLAN
Scope: wire existing Login screen scaffolds (React member app, Angular admin app) to the completed auth API (POST /api/v1/auth/otp/request, /otp/verify). Refresh/logout wiring deferred — no other screen needs a session yet.

Tasks:
  2.4a React: authClient (requestOtp/verifyOtp, token persistence in localStorage: tawfir_access_token/tawfir_refresh_token)
  2.4b React: Login.tsx wired — loading/error states (429 rate-limit, 401 invalid code, 400 bad phone), navigate to /groups on success
  2.4c React: update Login.test.tsx with mocked fetch
  2.5a Angular: provideHttpClient in app.config.ts; AuthService (requestOtp/verifyOtp, token persistence, JWT role-claim decode)
  2.5b Angular: login.ts/login.html wired — loading/error states, ADMIN-role client gate (balanced approach, see decisions.md), navigate to /dashboard on success
  2.5c Angular: update login.spec.ts with mocked HttpClient
  VERIFY: run both test suites + coverage gates

## 2026-07-26 — Sprint 2 Batch 2b EXECUTE + VERIFY
MILESTONE: React member app Login.tsx wired to POST /api/v1/auth/otp/request + /otp/verify (frontend-member/src/lib/authClient.ts) — loading state, 429/401/400 error surfacing, tokens persisted to localStorage, navigates to /groups on success.
MILESTONE: Angular admin app login.ts wired to same endpoints (frontend-admin/src/app/core/auth.service.ts, provideHttpClient added to app.config.ts) — client-side JWT role-claim decode gates non-ADMIN accounts locally (balanced approach per decisions.md), navigates to /dashboard on success for ADMIN.
BUG CAUGHT IN VERIFY (fixed before ship): shared apiClient.ts apiFetch() only special-cased HTTP 204 as an empty body; the real /otp/request endpoint returns 202 with an empty body, so response.json() threw on parse and every successful OTP request was surfaced to the user as a generic error. Fixed by reading response.text() first and treating any empty body as undefined rather than gating on status 204 specifically. Caught by the new Login.test.tsx OTP-request test, not by any unit test that mocked the client directly.
VERIFY: frontend-member — 14/14 tests pass, coverage 93.05% stmts / 88.88% branches (>= 80% gate). frontend-admin — 20/20 tests pass, coverage 98.67% stmts / 94.05% branches (>= 80% gate). oxlint (member) and ng lint (admin) both clean; admin lint required switching AuthService/Login to inject() over constructor injection per this repo's eslint config (@angular-eslint/prefer-inject).
Not yet done: refresh/logout wiring — deferred, no other screen consumes a session yet (GroupsList/Profile/Dashboard still placeholders).
## 2026-07-27 — Sprint 2 Batch 2b CI monitoring (rule 11)
Push a41fece: CI run 30258195408 RED. Trivy filesystem scan found a real HIGH-severity finding: GHSA-qwww-vcr4-c8h2 (React Router RSC-mode CSRF bypass) in react-router-dom 7.18.1, fixed in react-router 8.3.0. Not a CI config bug. react-router-dom's own latest release is still 7.18.1 (npm registry) — it is now just a thin re-export shim over react-router (1 dependency, 5.4kB), and has not been bumped past v7 while react-router itself moved to 8.x. Fix: migrated frontend-member off react-router-dom onto react-router@8.3.0 directly (package.json + all 8 import sites), consistent with the React Router team's own v7-to-v8 migration path. Verified locally first: 14/14 tests pass, coverage unchanged (93.05%), oxlint clean, `tsc -b && vite build` succeeds.
Push e929327: CI run 30260154176 GREEN — all 5 jobs pass (Frontend Member, Frontend Admin, Backend, Security scan, Build Docker images). Sprint 2 Batch 2b SHIP phase complete.

## 2026-07-27 — Sprint 2 Batch 3 EXECUTE + VERIFY (Epic 2 Group Lifecycle)
MILESTONE: Group domain (ma.tawfir.api.group) — Group/GroupMembership/ContributionSchedule/PayoutSchedule JPA entities, Flyway V3 (groups, group_memberships) + V4 (contribution_schedules, payout_schedules), matching database-tawfir.md §3 exactly.
MILESTONE: POST /api/v1/groups — creates a DRAFT group, auto-creates User rows for unregistered member phone numbers (same pattern as OTP-verify auto-create), organizer auto-appended if omitted from the members list, MANUAL payout_position assigned from array order at creation time.
MILESTONE: POST /api/v1/groups/:id/finalize — ORGANIZER-of-group + DRAFT-status + (member count == total_cycles) checks, RANDOMIZED payout_position shuffle-assignment at finalize time, full contribution_schedules + payout_schedules generation (every member owes every cycle; one payout recipient per cycle by payout_position), group -> ACTIVE.
MILESTONE: GET /api/v1/groups (mine), GET /api/v1/groups/:id — membership-scoped 403 (ForbiddenException, new common/ exception alongside ValidationException, both added to GlobalExceptionHandler) reusing the IDOR-check pattern security-tawfir.md §4 flagged as reusable; ADMIN bypasses the membership check per architecture-tawfir.md §5.
MILESTONE: React member app — groupsClient.ts; CreateGroup.tsx now collects totalCycles/payoutOrderMode/members (dynamic phone-number rows) and POSTs; GroupsList.tsx fetches and renders real groups; GroupDetail.tsx fetches group + roster, shows a Finalize button gated client-side to the organizer (JWT sub-claim decode, same UX-only pattern as the admin-app role gate from Batch 2b).
VERIFY: backend — `mvnw verify` full suite green (unit: GroupServiceTest 14 tests; slice: GroupControllerTest 8 tests; full-stack Testcontainers: GroupLifecycleIntegrationTest 4 tests covering stories-tawfir.md Epic 2's Gherkin scenarios — create+finalize happy path, incomplete-roster rejection, non-member 403). JaCoCo: 98% instructions / 98% branches (>= 80% gate). Checkstyle clean.
VERIFY: frontend-member — 23/23 tests pass (6 new: CreateGroup member-row add/remove + create/error paths, GroupDetail roster/finalize-gate/finalize-success, GroupsList empty/populated/error). Coverage 93.1% stmts / 80.23% branches (>= 80%/70% gate). oxlint clean, `tsc -b && vite build` succeeds.
Bug caught during test-writing (fixed before ship): GroupControllerTest's `Authentication` controller parameter resolved to null under @WebMvcTest with security autoconfiguration excluded — HttpServletRequest.getUserPrincipal() is normally populated by SecurityContextHolderAwareRequestFilter, which is part of the excluded chain. Fixed by setting the principal directly via MockHttpServletRequestBuilder#principal(...) instead of relying on spring-security-test's authentication() RequestPostProcessor (which needs that filter in the chain to take effect).
Scope note: Angular admin Groups screens remain placeholders (out of this epic's scope per stories-tawfir.md — Story 2.4 is Backend + React only). Contribution/payout schedule display, ledger, and disputes are Epic 3+ and stay as the existing "No activity yet." placeholders.

## 2026-07-27 — Sprint 2 Batch 3 SHIP phase
PUSH: commit 800a66d pushed to origin/feature/sprint-2-auth-groups. CI run 30266687060: GREEN on first try (all 5 jobs — Frontend Member, Backend, Security scan, Frontend Admin, Build Docker images). Sprint 2 Batch 3 (Epic 2 Group Lifecycle) SHIP phase complete. Sprint 2 is now fully done: Batch 1 (scaffold/CI/Docker), Batch 2a (auth backend), Batch 2b (auth frontend), Batch 3 (group lifecycle) all shipped and green.

## 2026-07-27 — Sprint 3 PLAN phase
User confirmed Sprint 3 scope (Epic 3 partial: 3.1/3.2/3.4, Epic 5: 5.1/5.2 — no CMI dependency, unblocked despite SDR-3). Plan: Batch 1 Ledger+Contributions domain -> Batch 2 Disputes -> Batch 3 React frontend. Decisions logged in decisions.md (no notification delivery for late-flagging, no auto-reversal on dispute resolve, append-only enforced via DB trigger not REVOKE). Starting Batch 1.

## 2026-07-28 — Sprint 3 Batch 1 VERIFY (resumed)
Resumed from prior session's unverified fix: `clearAutomatically = true` added to the two `@Modifying` CAS queries in `ContributionScheduleRepository` (compareAndSetStatus, flagOverdueAsLate) to stop Hibernate's first-level cache serving a stale entity after a bulk update within the same transaction.

Environment blockers hit and resolved before the fix could even be tested (both pre-existing local-machine drift, not code issues):
1. Machine's default JDK had been auto-upgraded to Temurin 25 since the last session (JAVA_HOME now `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`) — JaCoCo 0.8.12 cannot instrument class file major version 68/69 (JDK 24/25 bytecode), so every test run failed at the instrumentation step before any test logic ran. Project's `java.version` is still pinned to 21 per pom.xml; rather than bumping the project's target JDK (an undiscussed scope change), ran the build pinned to the JDK 21 install still present at `C:\Program Files\Java\jdk-21`. Maven itself auto-resolved jacoco-maven-plugin to 0.8.13 for this JDK, which handles this correctly — no pom.xml change needed.
2. Docker Desktop was not running, so Testcontainers' `postgresContainer` bean failed with "Could not find a valid Docker environment" — every `@SpringBootTest` (all 6 ContributionLifecycleIntegrationTest cases) errored at ApplicationContext load, not at the specific mark-paid assertion. Started Docker Desktop and waited for the daemon.

With both resolved: `ContributionLifecycleIntegrationTest` — 6/6 pass, confirming the `clearAutomatically` fix resolved `memberMarksPaid_organizerConfirms_ledgerEntryAppended`. Full `mvnw verify` (JDK 21) — BUILD SUCCESS, 89/89 tests pass across all suites, Checkstyle 0 violations, JaCoCo coverage gate met (97.35% instructions / 97.3% branches, well above the 80% gate). Sprint 3 Batch 1 (Ledger + Contributions backend: mark-paid, confirm, GET list, LateContributionScheduler, append-only ledger trigger) is now fully verified.

## 2026-07-28 — Sprint 3 Batch 1 SHIP phase
PUSH: commit 5a148a6 pushed to origin/feature/sprint-2-auth-groups. CI run 30354076144: GREEN on first try (all 5 jobs — Backend, Security scan, Frontend Member, Frontend Admin, Build Docker images). Sprint 3 Batch 1 (Ledger + Contributions backend) SHIP phase complete. Next: Batch 2 (Disputes — service+controller+tests; entities/migrations/repository already landed this batch) per the Sprint 3 plan.

## 2026-07-28 — Sprint 3 Batch 2 EXECUTE + VERIFY (Disputes, stories 5.1/5.2)
Document-first per rule 12: two scope decisions logged in decisions.md before writing code — (1) opening a dispute is scoped to CONFIRMED contributions only, matching the Gherkin scenario and the pre-existing `disputes.ledger_entry_id NOT NULL` constraint (a ledger entry only exists post-confirm); (2) resolving a dispute only updates the Dispute row, does not auto-revert the contribution_schedule's DISPUTED status (consistent with the no-auto-reversal decision already on record).

MILESTONE: POST /api/v1/groups/:id/disputes (ma.tawfir.api.dispute.DisputeService/DisputeController) — any group member (ADMIN bypass) opens a dispute against a CONFIRMED contribution's ledger entry; atomic CAS (reusing ContributionScheduleRepository#compareAndSetStatus) flips the contribution_schedule CONFIRMED -> DISPUTED, dispute row created OPEN.
MILESTONE: PATCH /api/v1/disputes/:id/resolve — group organizer (or ADMIN) resolves an OPEN dispute as ACCEPTED/REJECTED with a reason. New `DisputeRepository#resolveIfOpen` CAS query (same pattern as contribution CAS, `clearAutomatically = true`) sets status/resolvedByUserId/resolutionReason/resolvedAt atomically, so concurrent resolve attempts can't both succeed (test-strategy §4 race-condition item).
VERIFY: full `mvnw verify` (JDK 21, Docker running) — 109/109 tests pass (20 new: DisputeServiceTest 9, DisputeControllerTest 6, DisputeLifecycleIntegrationTest 5 full-stack Testcontainers covering open/non-member-403/organizer-resolve/non-organizer-403/concurrent-resolve-race). JaCoCo 96.0% instructions / 95.65% branches (>= 80% gate). Checkstyle: 0 violations.
Not yet done: Batch 3 (React frontend wiring for contributions + disputes) — backend for both Epic 3 (partial) and Epic 5 is now complete and tested but has no frontend consumer yet.

## 2026-07-28 — Sprint 3 Batch 3 EXECUTE + VERIFY (React frontend + two backend gaps closed)
Two backend read endpoints were missing and blocked the frontend entirely: no GET to list a group's ledger entries (needed for the ledger tab and to find a CONFIRMED contribution's ledgerEntryId to dispute), and no GET to list a group's disputes (needed for the organizer to see OPEN disputes to resolve). Both reuse the existing membership-scoped 403 pattern (ADMIN bypass):
MILESTONE: GET /api/v1/groups/:id/ledger (ma.tawfir.api.ledger.LedgerService/LedgerController, new LedgerEntryResponse DTO).
MILESTONE: GET /api/v1/groups/:id/disputes (DisputeService.listForGroup + DisputeController, reusing the existing DisputeResponse DTO).

MILESTONE: frontend-member — contributionsClient.ts, ledgerClient.ts, disputesClient.ts added. GroupDetail.tsx rewired: schedule tab lists contributions with cycle/member/status and contextual actions (Mark paid for the owner on PENDING/LATE, Confirm for the organizer on MARKED_PAID, Dispute for any member on a CONFIRMED contribution with an inline reason field); ledger tab lists entries (type/amount/source, flags corrections); disputes tab lists disputes with inline Accept/Reject + reason controls for the organizer on OPEN disputes.
VERIFY: backend — full `mvnw verify` (JDK 21, Docker) — 118/118 tests pass (7 new: LedgerServiceTest 3, LedgerControllerTest 2, plus 2 new DisputeService/Controller list-endpoint tests), Checkstyle clean, JaCoCo gate met. frontend-member — 31/31 tests pass (15 new/updated in GroupDetail.test.tsx, switched to a URL-routing fetch mock since GroupDetail now fires 4 concurrent requests), coverage 86.81% stmts / 74.5% branches / 85.71% funcs / 88.55% lines (>= 80/70/80/80 gate), oxlint clean, `tsc -b && vite build` succeeds.
Bug caught during test-writing (fixed before ship): a dispute-submission test's fetch-mock override matched the `/groups/g-1/disputes` URL for both the POST (open) and the GET (list reload) that follows it, returning a single dispute object where the reload expected an array and crashing `disputes.map`. Fixed by also checking the HTTP method in the mock router. Separately, `findByRole('button', { name: /dispute/i })` was ambiguously matching both the always-present "disputes" nav tab and the async-loaded row-level "Dispute" button, letting one test pass on a false positive (it grabbed the nav tab before the row button had even mounted) — fixed by matching the exact button name.
Scope note: Angular admin frontend untouched — stories 3.1/3.2/3.4/5.1/5.2 are Backend + React only (stories-tawfir.md), same precedent as Sprint 2 Batch 3. Sprint 3 is now fully done: Batch 1 (ledger+contributions backend), Batch 2 (disputes backend), Batch 3 (ledger/dispute read endpoints + React wiring) all built and verified.

## 2026-07-28 — Sprint 3 Batch 3 SHIP phase
PUSH: commit ce5cab3 pushed to origin/feature/sprint-2-auth-groups. CI run 30365504304: GREEN on first try (all 5 jobs — Frontend Member, Backend, Security scan, Frontend Admin, Build Docker images). Sprint 3 Batch 3 SHIP phase complete. Sprint 3 is now fully closed: all 3 batches (ledger+contributions backend, disputes backend, ledger/dispute read endpoints + React frontend) shipped and green.

## 2026-07-28 — Rule 9 video recording (in progress, session paused)
User chose to set up the CLAUDE.md rule 9 Playwright recording now rather than defer again. Scaffolded a new `e2e/` workspace (package.json, playwright.config.ts, tests/critical-flows.spec.ts, tests/helpers.ts) covering the full vertical slice: OTP login -> create+finalize group -> mark-paid -> confirm -> open dispute -> resolve dispute, run against the real stack (Postgres + backend + frontend-member), video recording enabled in the Playwright config.

Since OTP delivery is mocked and only ever logged (MockOtpProvider), not exposed via any endpoint, the test reads the code back from the backend container's own logs rather than adding a test-only retrieval endpoint (would be a real attack-surface regression per security-tawfir.md §6).

This machine runs many unrelated projects in Docker simultaneously (dars-ma, mizan-ma, sana3-ma, baridi-ma, atlas-events, etc.) occupying the canonical ports (5432/8080/3000) — worked around by running postgres+backend as a separate, unpublished-postgres-port stack under docker-compose project name `tawfir-e2e` (ephemeral host port for backend) via a temporary compose file kept OUTSIDE the repo (scratchpad, not committed), and running frontend-member via `vite --port 3001` with `VITE_API_BASE_URL` pointed at the backend's ephemeral port, rather than editing the checked-in docker-compose.yml.

Bug found and fixed: the helper that reads the mocked OTP code back from `docker logs` originally matched containers by the substring "-backend-", which also matched an unrelated project's container (`dars-ma-backend-1`) also running on this machine, silently reading the wrong container's logs. Fixed by anchoring the name filter on this project's "tawfir" prefix (`^tawfir.*-backend-`).

Session paused here at user's request before the first full test run against the fix. Nothing in `e2e/` is committed yet — it's new, not-yet-verified code. Docker containers and the dev server used for this were torn down cleanly before pausing.

## 2026-07-28 — Sprint 3 Batch 2 SHIP phase
PUSH: commit f9fa39a pushed to origin/feature/sprint-2-auth-groups. CI run 30357558927: GREEN on first try (all 5 jobs — Backend, Security scan, Frontend Member, Frontend Admin, Build Docker images). Sprint 3 Batch 2 (Disputes backend) SHIP phase complete. Next: Batch 3 (React frontend wiring for contributions + disputes) per the Sprint 3 plan — the last batch of Sprint 3.

## 2026-07-30 — Rule 9 video recording, resumed and completed
Resumed from the 2026-07-28 pause. Brought up an isolated `tawfir-e2e` compose stack (postgres + backend only, postgres port unpublished, backend on host port 8081 — 5432/5433/8080 were all already taken by other projects' running containers on this machine) via a scratch override file (kept outside the repo, `!override` YAML tag used to fully replace the base compose's `ports` lists rather than concatenate onto them). Ran frontend-member separately via `vite --port 3001` with `VITE_API_BASE_URL=http://localhost:8081`.

First run reproduced the same `No mock OTP code found` failure the prior session paused on — but the container-name fix from that session was confirmed correct (verified `docker ps --filter 'name=^tawfir.*-backend-'` resolves to exactly `tawfir-e2e-backend-1`). Root cause was different and new: `loginViaOtp` in `e2e/tests/helpers.ts` clicked "Send code" and read the backend's Docker logs immediately after, racing the async `otp/request` call — the page snapshot on failure showed the button still in its disabled "Sending…" state. Fixed by waiting for the code-entry field (`getByLabel(/code/i)`) to render before reading logs, which only happens after `otp/request` resolves.

Second run got further (auth, group create/finalize, mark-paid, confirm all passed) then failed on `getByRole('button', { name: 'Dispute' })` — a strict-mode violation, since Playwright's default non-exact name matching made it ambiguous against the always-present "disputes" nav tab button. This is the same class of ambiguity already caught and fixed in `GroupDetail.test.tsx` during Sprint 3 Batch 3 (2026-07-28 entry above) but it had crept back into the new e2e spec. Fixed with `{ exact: true }`.

Third run passed clean. Investigated why `test-results/` had no `.webm` despite `video: 'on'` in `playwright.config.ts`: that project-level option only auto-wires into the built-in `context`/`page` fixtures (confirmed by reading `_contextFactory` in `node_modules/playwright/lib/index.js`) — since this spec drives two independent logged-in users via manual `browser.newContext()` calls, video was silently never being recorded, on this run or the original 2026-07-28 failing run either (its `test-results/` folder had a `trace.zip` but no video, because tracing is wired at the connection level and isn't subject to the same limitation). Fixed by passing `recordVideo: { dir: 'test-results/videos' }` explicitly on both `newContext()` calls, then copying each context's finished video out to `.recordings/` after `context.close()`.

Final run: 1 passed, both organizer- and member-perspective videos produced (`.recordings/v3-2026-07-30-organizer.webm`, `.recordings/v3-2026-07-30-member.webm` — two files instead of rule 9's singular naming since the test necessarily uses two separate browser contexts/users and there is no single native recording that covers both simultaneously). `e2e/` (minus `node_modules/` and `test-results/`, both newly added to `.gitignore`) and `.recordings/` committed. Docker stack and the `vite` dev server used for this were torn down cleanly afterward. Rule 9 is now fully satisfied for Sprint 3 — this closes out the last open item from Sprint 3, which is fully shipped and closed.

## 2026-07-31 — Sprint 3 close: outstanding push + CI monitoring (rule 7 / rule 11)
Resumed session; prior session had stopped before pushing commits `5431ebf` and `0724b76` (rule 9 completion). PUSH: `git push origin feature/sprint-2-auth-groups` — `ce5cab3..61945cb`. CI run 30609342346: GREEN on first try (all 5 jobs — Backend, Frontend Admin, Security scan, Frontend Member, Build Docker images; only non-blocking Node 20 deprecation annotations). Rule 7 now satisfied. Sprint 3 is 100% closed with nothing outstanding.

## 2026-07-31 — Sprint 5 UNDERSTAND + BRAINSTORM
User picked Sprint 5 (1.4 Admin MFA, 6.1 Admin dashboard, 7.1 Savings snapshot) over Sprint 4 (still blocked on SDR-3). Flagged that stories-tawfir.md lists Epic 4 (payout execution) as a dependency for both 6.1 and 7.1, and Epic 4 is itself blocked on SDR-3 — user confirmed: build against data that already exists now (contributions/ledger/disputes), rather than waiting or skipping.

Brainstorm — user picked the simplest (🟢) option for all three, per YAGNI:
- 1.4: TOTP only (RFC 6238), no backup codes; lockout recovery is DB-level ops action, not self-service.
- 6.1: GET /admin/groups, /admin/metrics, /admin/disputes aggregating existing group/contribution/dispute data + Angular dashboard page. Explicitly closes the long-open risk (flagged since Sprint 2 Batch 2b) that ADMIN role enforcement was client-side/UX-only — this adds real backend `@PreAuthorize("hasRole('ADMIN')")`.
- 7.1: savings_history_snapshot recorded when a cycle's last contribution reaches CONFIRMED (proxy for "cycle completion" since Epic 4's real payout-executed event doesn't exist yet); GET /users/:id/savings-history exposed now. Flagged as a documented proxy to revisit once Epic 4 lands.

## 2026-07-31 — Sprint 5 PLAN phase
Env vars (rule 10): none new needed — reusing existing `PII_ENCRYPTION_KEY` (already reserved in .env.example for deferred phone-number encryption) for TOTP-secret app-layer AES-GCM encryption, since a secret only needs encrypt/decrypt, not the equality-lookup blind-index that phone_number needs.

📋 BATCH 1: Admin MFA (Story 1.4)
  1.1 V7 migration: users.totp_secret (nullable, AES-GCM encrypted at app layer), users.totp_enabled_at (nullable)
  1.2 POST /api/v1/admin/mfa/setup (ADMIN-only) — generates TOTP secret, returns provisioning URI, stores encrypted+inactive until first verify
  1.3 POST /api/v1/admin/mfa/verify — confirms first code, sets totp_enabled_at
  1.4 Login flow change: after /otp/verify, ADMIN accounts with totp_enabled_at set get a short-lived mfa-pending token instead of full JWT; POST /api/v1/auth/mfa/verify exchanges a valid TOTP code + pending token for the real access/refresh pair
  1.5 Tests: setup, verify-activates, login-requires-mfa-when-enabled, wrong-code-rejected, non-admin-403

📋 BATCH 2: Admin dashboard (Story 6.1)
  2.1 GET /api/v1/admin/groups — all groups, status, member count, cycle progress
  2.2 GET /api/v1/admin/metrics — group counts by status, contribution completion/late rate, open dispute count
  2.3 GET /api/v1/admin/disputes — all disputes platform-wide (unscoped)
  2.4 `@PreAuthorize("hasRole('ADMIN')")` on all three + explicit 403-for-MEMBER test — closes the open risk (logged since Sprint 2 Batch 2b) that admin-role enforcement was client-side only
  2.5 Angular: admin dashboard page wired to the 3 endpoints, replacing the current placeholder
  2.6 Tests: controller slice + full-stack 403 check

📋 BATCH 3: Savings history snapshot (Story 7.1)
  3.1 V8 migration: savings_history_snapshots (per database-tawfir.md §3, schema already specified)
  3.2 On contribution confirm: if it's the group's last PENDING/LATE→CONFIRMED transition for that cycle, insert one snapshot row per member (cycles_completed/on_time_rate/disputes_involved) — documented as a proxy for real cycle-completion pending Epic 4
  3.3 GET /api/v1/users/:id/savings-history — self or ADMIN only
  3.4 Tests: snapshot fires only on last confirm, self/admin access-scoping enforced

Each batch: VERIFY (tests + 80% coverage gate) before moving on; CI monitored red→green per rule 11; PUSH at sprint end per rule 7. Awaiting user confirmation before EXECUTE (gate per rule 5).

## 2026-07-31 — Sprint 5 Batch 1 EXECUTE + VERIFY (Admin TOTP MFA, story 1.4)
MILESTONE: V7 migration (`users.totp_secret`, `totp_enabled_at`, `mfa_failed_attempts`, `mfa_locked_until`). `AesGcmEncryptor` (AES-256-GCM, SHA-256-derived key from `PII_ENCRYPTION_KEY`, random IV per encryption) — first consumer of that previously-reserved env var. `TotpGenerator` — hand-rolled RFC 6238 TOTP (HMAC-SHA1, Base32 per RFC 4648) rather than adding a third-party dependency; verified against the official RFC 6238 Appendix B test vector. `POST /api/v1/admin/mfa/setup` + `/verify` (ADMIN-only, two-phase: a stored-but-unverified secret never satisfies MFA on its own). Login flow: `POST /api/v1/auth/otp/verify` now returns an `mfa_pending`-claimed short-lived token instead of real tokens for MFA-enabled admins (rejected by `JwtAuthenticationFilter` for all normal endpoints); new `POST /api/v1/auth/mfa/verify` exchanges pending-token + code for the real access/refresh pair. Non-MFA users/admins completely unaffected.

SECURITY FINDING CAUGHT DURING VERIFY (fixed before ship, not deferred): neither MFA verify endpoint had any brute-force protection on the 6-digit code — `POST /api/v1/auth/mfa/verify` in particular is public (`/api/v1/auth/**`) and needs only the 5-minute pending token, so it was open to unlimited code-guessing. Since this feature exists specifically to protect the highest-blast-radius accounts, shipping it unthrottled would have defeated the point. Fixed by adding a shared `mfa_failed_attempts`/`mfa_locked_until` pair to `users` (5 wrong attempts → 15 min lockout), mirroring the existing `otp_challenges.attempt_count` pattern already in this codebase; applied to both `MfaService.verify` and `AuthService.verifyMfaLogin`. Documented in database-tawfir.md and security-tawfir.md §3.

VERIFY: `mvnw verify` (JDK 21, Docker running) — BUILD SUCCESS, 162/162 tests pass (41 new: AesGcmEncryptorTest, TotpGeneratorTest, MfaServiceTest incl. lockout, MfaControllerTest, MfaFlowIntegrationTest full-stack Testcontainers, plus AuthService/AuthController/JwtService/JwtAuthenticationFilter updates incl. a login-lockout regression test), 0 Checkstyle violations, JaCoCo gate met.
Not yet done: Batch 2 (Admin dashboard, story 6.1) and Batch 3 (Savings snapshot, story 7.1) — the remaining two Sprint 5 batches.

## 2026-07-31 — Sprint 5 Batch 1 SHIP phase + CI monitoring (rule 11)
PUSH: commit f0f56df pushed to origin/feature/sprint-2-auth-groups. CI run 30617580779: RED — Security scan job, Gitleaks step. Finding: `generic-api-key` rule matched `TotpGeneratorTest.RFC_TEST_SECRET` ("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", the Base32 encoding of RFC 6238 Appendix B's published test vector). False positive, not a real secret — it's a spec constant used to verify TOTP-code generation against the RFC's own worked example. Fix: added `.gitleaks.toml` (none existed before) with `[extend] useDefault = true` (keeps every default rule active) plus one narrowly-scoped allowlist regex matching only this exact literal — not a path exemption or a disabled rule, so Gitleaks still fully scans this file and the rest of the repo for anything else.

Push a34e12f: CI run 30617704318 still RED — config bug, not the original finding: gitleaks 8.24.3 rejected `[[allowlist]]` (array-of-tables) with "'Allowlist' expected a map, got 'slice'" — this gitleaks version's schema wants a single `[allowlist]` table, not an array. Fixed the TOML section header.

Push 30f123c: CI run 30617836861 GREEN — all 5 jobs pass (Frontend Admin, Security scan, Frontend Member, Backend, Build Docker images). Sprint 5 Batch 1 (Admin TOTP MFA, story 1.4) SHIP phase complete, including the security-engineer-domain fix (MFA brute-force lockout) caught and closed before ship, and the Gitleaks false-positive resolved without weakening the scan. Next: Batch 2 (Admin dashboard, story 6.1).

## 2026-07-31 — Sprint 5 Batch 2 EXECUTE + VERIFY (Admin dashboard, story 6.1)
Two subagent runs hit external interruptions mid-task (first an API stream stall, then the session's monthly spend limit) — picked up and finished the remaining work directly rather than re-delegating. What survived from the agent runs: `GroupRepository.countByStatus`, `ContributionScheduleRepository` (`countByDueDateBeforeAndStatus[In]`, `findDistinctGroupIdsByStatus`), `DisputeRepository` (`countByStatus`, `findDistinctGroupIdsByStatus`), `DisputeService.listAll()`, and the full `admin/` package (`AdminService`, `AdminController`, DTOs, `AdminServiceTest`) — all reviewed and found solid, following the established manual `requireAdmin(Authentication)` pattern from `MfaController`/`GroupController` rather than `@PreAuthorize` (this codebase has no `@EnableMethodSecurity` configured anywhere, confirmed by inspecting `SecurityConfig.java`; story 6.1's technical notes mention `@PreAuthorize` loosely but the existing precedent takes priority — documented in stories-tawfir.md next to the story and in decisions.md).

MILESTONE: `GET /api/v1/admin/groups` (all groups, memberCount, cyclesCompleted — a cycle counts once every member's contribution for it is CONFIRMED), `GET /api/v1/admin/metrics` (activeGroups, defaultRatePercent, openDisputes, atRiskGroups), `GET /api/v1/admin/disputes` (platform-wide, unscoped) — all ADMIN-only, 403 for MEMBER. `defaultRatePercent`/`atRiskGroups` had no formal definition anywhere in the docs (only qualitative, ux-tawfir.md's Fatima persona); defined and documented in decisions.md 2026-07-31 (LATE-past-due share; distinct groups with a LATE contribution or OPEN dispute).

Completed the remaining work myself: `AdminControllerTest` (403/200 slice tests — first run failed with a missing-bean `ApplicationContext` error, `JwtAuthenticationFilter` needs a `JwtService` bean even under `@WebMvcTest`'s excluded security autoconfig, same requirement `MfaControllerTest` already has; fixed by adding the same `@MockitoBean JwtService`), `AdminFlowIntegrationTest` (full-stack Testcontainers, all 4 endpoints incl. the story's Gherkin 403 scenario), Angular `AdminApiService` (new — this admin app has no HTTP interceptor, so the bearer token is attached manually per-call, matching `AuthService`'s existing style) and `dashboard.ts`/`.html`/`.spec.ts` wired to `GET /api/v1/admin/metrics` with loading/error states. `GET /admin/groups`/`/admin/disputes` built (per the story's technical notes) but intentionally left unconsumed by any Angular UI this batch — same precedent as Sprint 2 Batch 3's "Angular admin Groups screens remain placeholders."

VERIFY: backend `mvnw verify` (JDK 21, Docker) — BUILD SUCCESS, all tests pass (14 new: AdminServiceTest 4, AdminControllerTest 6, AdminFlowIntegrationTest 4), 0 Checkstyle violations, JaCoCo gate met. frontend-admin — 22/22 tests pass (3 new/updated in dashboard.spec.ts), coverage 86.25% stmts / 89.47% branches / 92% funcs / 87.87% lines (>= 80/70/80/80 gate), ESLint clean, `ng build` succeeds (one pre-existing, non-blocking bundle-size budget warning, +10.55kB over the 500kB initial-bundle budget — not treated as a build failure, not addressed this batch).
Not yet done: Batch 3 (Savings history snapshot, story 7.1) — the last Sprint 5 batch.

## 2026-07-31 — Sprint 5 Batch 2 SHIP phase
PUSH: commit 731dbff pushed to origin/feature/sprint-2-auth-groups. CI run 30651958039: GREEN on first try (all 5 jobs — Frontend Member, Backend, Security scan, Frontend Admin, Build Docker images). Sprint 5 Batch 2 (Admin dashboard, story 6.1) SHIP phase complete. Next: Batch 3 (Savings history snapshot, story 7.1) — the last batch of Sprint 5.

## 2026-07-31 — Sprint 5 Batch 3 EXECUTE + VERIFY (Savings history snapshot, story 7.1)
Built directly (not delegated) given the two prior external agent interruptions this session. Document-first per rule 12: two scope decisions logged in decisions.md before writing code — (1) "cycle completion" is a proxy (every member's cycle contribution CONFIRMED), same precedent as Batch 2, since Epic 4/real payout execution is still blocked on SDR-3; (2) needed a new `contribution_schedules.was_late` column (V8) since the current `status` column loses late-history once MARKED_PAID/CONFIRMED overwrite it — considered and rejected reconstructing lateness from ledger-entry timestamps (those record confirm time, not payment time, so they can't answer it either).

MILESTONE: V8 migration (`contribution_schedules.was_late`, `savings_history_snapshots` table per database-tawfir.md §3's already-specified schema). `ContributionScheduleRepository.flagOverdueAsLate` now also sets `was_late = true` alongside the existing LATE status flip. New `ma.tawfir.api.savings` package: `SavingsHistorySnapshot` entity, `SavingsHistoryService` (`recordSnapshotsIfCycleComplete` — computes `cyclesCompleted`/`onTimeRate`/`disputesInvolved` per member and inserts one snapshot row each; `getHistoryForUser`), wired into `ContributionService.confirm()` right after the ledger entry is appended. `disputesInvolved` resolves via an in-memory join (Dispute -> LedgerEntry.contributionScheduleId -> ContributionSchedule.userId) since this codebase has no JPA `@ManyToOne` associations, just plain UUID FK columns — acceptable at MVP group sizes.
MILESTONE: `GET /api/v1/users/:id/savings-history` (new `UserController`) — self-or-ADMIN only (not membership-scoped, since a user's history spans groups the requester may have no relationship to).

VERIFY: `mvnw verify` (JDK 21, Docker) — BUILD SUCCESS, 186/186 tests pass (10 new: SavingsHistoryServiceTest 5, SavingsHistoryFlowIntegrationTest 2 full-stack Testcontainers incl. "snapshot only fires once the cycle's last contribution confirms, not the first", UserControllerTest 3), 0 Checkstyle violations, JaCoCo gate met. `ContributionServiceTest` updated for the new constructor dependency + confirm()-calls-savingsHistoryService assertions.
Sprint 5 is now fully built: Batch 1 (Admin MFA), Batch 2 (Admin dashboard), Batch 3 (Savings history) all done — only SHIP (push+CI) remains for Batch 3.

## 2026-07-31 — Sprint 5 Batch 3 SHIP phase
PUSH: commit c62105f pushed to origin/feature/sprint-2-auth-groups. CI run 30655968515: GREEN on first try (all 5 jobs — Backend, Frontend Admin, Security scan, Frontend Member, Build Docker images). Sprint 5 Batch 3 (Savings history snapshot, story 7.1) SHIP phase complete. Sprint 5 is now fully closed: all 3 batches (Admin MFA, Admin dashboard, Savings history) shipped and green.

## 2026-07-31 — Sprint 4 UNDERSTAND + BRAINSTORM + PLAN
User confirmed: unblock Sprint 4 (stories 3.3, 4.1, 4.2, 4.3 — CMI webhook integration + payouts) by building against the existing `MockCmiClient`/`CMI_WEBHOOK_SECRET` (both already scaffolded since Sprint 2 Batch 1) — real HMAC webhook signature verification is built for real, but money movement itself stays simulated until SDR-3 (custody-model/BAM licensing) is resolved, same precedent as OTP being mocked throughout. This unblocks the code paths without needing the legal decision first.

Plan (2 batches, dependency-ordered since 4.3 explicitly shares 3.3's webhook infra):
📋 BATCH 1 — Story 3.3 (CMI webhook auto-confirms contribution) + shared webhook infra
  - `CmiSignatureVerifier`: HMAC-SHA256 over the raw request body using `CMI_WEBHOOK_SECRET` (already bound via `TawfirProperties.Payment.webhookSecret`), constant-time compare (`MessageDigest.isEqual`), header `X-Cmi-Signature`
  - `POST /api/v1/webhooks/cmi/payment-confirmation` — invalid/missing signature -> 401; valid -> confirms the contribution (CAS from {PENDING, LATE, MARKED_PAID} -> CONFIRMED, stronger than organizer-confirm which requires MARKED_PAID first, since a real payment confirmation is stronger evidence); LedgerSource.CMI_WEBHOOK; idempotent (already-CONFIRMED replay is a no-op, not an error); triggers the same savings-history snapshot hook as organizer-confirm
  - `/api/v1/webhooks/**` added to SecurityConfig's permitAll (system-to-system, no JWT — authenticated by signature instead)

📋 BATCH 2 — Stories 4.1 (auto payout cron), 4.2 (organizer manual override), 4.3 (CMI payout-confirmation webhook)
  - New `LedgerSource.SYSTEM_SCHEDULED` value (migration, widens the `source` CHECK constraint) for cron-auto-executed payouts — reusing `ORGANIZER_CONFIRMED` for 4.2's manual override (an organizer action, same semantic family) and `CMI_WEBHOOK` for 4.3 (already fits)
  - `PayoutScheduler` (mirrors `LateContributionScheduler`'s `@Scheduled` pattern): for PENDING payouts past `scheduled_date` with every contribution for that group+cycle CONFIRMED, calls `CmiClient.initiateTransfer`, sets EXECUTED, appends a PAYOUT ledger entry (SYSTEM_SCHEDULED). Not-ready payouts are left PENDING, no notification delivery (no channel decided — same accepted-TODO precedent as story 3.4) — organizer sees it's still PENDING and can manually override.
  - `POST /api/v1/groups/:id/payouts/:payoutId/execute` (ORGANIZER-only) — same execute logic, target status MANUAL_OVERRIDE, LedgerSource.ORGANIZER_CONFIRMED
  - `POST /api/v1/webhooks/cmi/payout-confirmation` — same HMAC pattern as Batch 1; idempotent if already EXECUTED/MANUAL_OVERRIDE; otherwise executes PENDING -> EXECUTED, LedgerSource.CMI_WEBHOOK
  - `PayoutStatus.FAILED`: set if `CmiClient.initiateTransfer` throws, logged, no ledger entry, cron continues to the next payout (no retry queue — accepted MVP limitation)

No new env vars (rule 10) — `CMI_PROVIDER`/`CMI_WEBHOOK_SECRET`/`CMI_API_KEY` already in .env.example since Sprint 2 Batch 1. Each batch: VERIFY (tests + 80% coverage gate) before moving on; CI monitored red→green per rule 11; push at batch end per rule 7 precedent.

## 2026-07-31 — Sprint 4 Batch 1 EXECUTE + VERIFY (CMI webhook infra + story 3.3)
Document-first per rule 12: webhook signature contract and the WEBHOOK_CONFIRMABLE_STATUSES/idempotency design logged in decisions.md before writing code (no real CMI API spec exists — this is a from-scratch, documented contract).

MILESTONE: `CmiSignatureVerifier` (hex HMAC-SHA256 over the raw body, `MessageDigest.isEqual` constant-time compare), `InvalidWebhookSignatureException` -> 401, `CmiWebhookController` (`POST /api/v1/webhooks/cmi/payment-confirmation`, public in SecurityConfig — system-authenticated by signature, not JWT). `ContributionService.confirmViaWebhook` — transitions from {PENDING, LATE, MARKED_PAID} (broader than organizer-confirm's MARKED_PAID-only, since a real payment confirmation is stronger evidence than a self-report), validates the webhook's reported amount against the group's contribution amount, idempotent on replay (already-CONFIRMED is a no-op), triggers the same savings-history snapshot hook as organizer-confirm.

BUGS CAUGHT DURING VERIFY (both fixed before ship, neither is a real CMI issue — pure Spring/Maven plumbing gaps):
1. `com.fasterxml.jackson.databind` wasn't resolvable at compile scope — `spring-boot-starter-webmvc` in this project only pulls `jackson-databind` in transitively at *runtime* scope (via `jjwt-jackson`), so any main-source class importing Jackson directly failed to compile. Fixed by adding an explicit `jackson-databind` dependency to `backend/pom.xml` (version managed by the parent BOM).
2. Even after that fix, `@WebMvcTest`'s default auto-configuration set doesn't expose a Spring-managed `ObjectMapper` bean for constructor injection (a `NoSuchBeanDefinitionException` in the slice test, `JacksonAutoConfiguration` being present in the customizer list notwithstanding). Sidestepped rather than chasing Boot's autoconfiguration internals further: `CmiWebhookController` now owns a private `new ObjectMapper()` instance directly instead of injecting the shared bean — this controller's parsing needs are narrow and don't benefit from the app-wide Jackson customization anyway.
3. (Recurring class of issue, now the third time) `@WebMvcTest` controller slice tests need a `@MockitoBean JwtService` even when the controller itself doesn't use it, because `JwtAuthenticationFilter` is a real `@Component` picked up regardless of excluded security autoconfiguration — added to `CmiWebhookControllerTest` matching the same fix applied to `MfaControllerTest`/`AdminControllerTest`.

VERIFY: `mvnw verify` (JDK 21, Docker) — BUILD SUCCESS, 202/202 tests pass (16 new: CmiSignatureVerifierTest 6, CmiWebhookControllerTest 4, CmiPaymentWebhookIntegrationTest 2 full-stack Testcontainers covering both Gherkin scenarios — valid-signature auto-confirm + replay-idempotency, invalid-signature-rejected-no-ledger-entry — plus 4 new ContributionServiceTest cases for confirmViaWebhook), 0 Checkstyle violations, JaCoCo gate met.
Not yet done: Batch 2 (stories 4.1 auto payout cron, 4.2 organizer manual override, 4.3 CMI payout-confirmation webhook) — the last batch of Sprint 4.

## 2026-07-31 — Sprint 4 Batch 1 SHIP phase
PUSH: commit 52f53ef pushed to origin/feature/sprint-2-auth-groups. CI run 30667827679: GREEN on first try (all 5 jobs — Backend, Frontend Member, Frontend Admin, Security scan, Build Docker images). Sprint 4 Batch 1 (CMI webhook infra + story 3.3) SHIP phase complete. User asked to end the session here. Next session: start Batch 2 (stories 4.1/4.2/4.3, plan already logged above) per the confirmed Sprint 4 plan.

## 2026-07-31 — Sprint 4 Batch 2 EXECUTE + VERIFY (payout cron/override/webhook, stories 4.1-4.3)
Reconciled a plan conflict before EXECUTE (decisions.md item 8): the pre-logged Batch 2 plan already specified the 4.3 webhook can itself drive PENDING -> EXECUTED, not just confirm an already-executed payout — kept that (consistent with 3.3 precedent) over a narrower alternative drafted mid-session.

MILESTONE: `PayoutScheduleService` (new — greenfield, no service existed for payouts before this batch) with three entry points converging on one CAS-then-ledger helper: `executeIfReady` (4.1, called per-payout by new `PayoutScheduler` @Scheduled hourly job, checks every contribution in the group+cycle is CONFIRMED via new `countByGroupIdAndCycleNumberAndStatusNot`), `executeManualOverride` (4.2, `POST /api/v1/groups/:id/payouts/:payoutId/execute`, organizer-only), `confirmViaWebhook` (4.3, `POST /api/v1/webhooks/cmi/payout-confirmation`, same HMAC infra as Batch 1). `PayoutStatus.FAILED` set (no ledger entry) if `CmiClient.initiateTransfer` throws; cron continues to the next payout rather than aborting the batch. New `LedgerSource.SYSTEM_SCHEDULED` (migration V9, widens ledger_entries.source CHECK). Added `GET /api/v1/groups/:id/payouts` (not in the original plan, logged as necessary infra in decisions.md item 9 — otherwise 4.2's payoutId is undiscoverable via the API).

VERIFY: `mvnw verify` (JDK 21 pinned, Docker) — BUILD SUCCESS, 0 Checkstyle violations, JaCoCo coverage gate met. New tests: PayoutScheduleServiceTest (13), PayoutControllerTest (6), PayoutSchedulerTest (3), 2 new CmiWebhookControllerTest cases (payout-confirmation valid/invalid signature), PayoutLifecycleIntegrationTest (6 full-stack Testcontainers cases covering manual override, non-organizer-forbidden, webhook auto-execute + idempotent replay, invalid signature rejected, cron auto-execute when ready, cron leaves not-ready payouts PENDING — the cron tests backdate `scheduled_date` via a native UPDATE since a freshly-finalized cycle-1 payout is always scheduled in the future).
BUG CAUGHT DURING VERIFY (fixed before ship, test-only): first draft of PayoutLifecycleIntegrationTest asserted ledger-entry-list size directly, not accounting for the CONTRIBUTION-type entries that confirmAllCycleOneContributions() itself appends to the same group — 6/6 new integration tests failed ("expected 1 but was 3"). Fixed by filtering to entryType == PAYOUT before asserting.
Sprint 4 is now fully implemented (Batch 1 + Batch 2, all 4 stories: 3.3, 4.1, 4.2, 4.3). Not yet done: commit + push + CI monitoring (SHIP phase, rule 7/11).

## 2026-07-31 — Sprint 4 Batch 2 SHIP phase + CI monitoring (rule 7/11)
PUSH: commit 777b812 pushed to origin/feature/sprint-2-auth-groups. CI run 30669618409: GREEN on first try (all jobs). Sprint 4 is now 100% closed — both batches (webhook infra + 3.3, then 4.1/4.2/4.3) shipped and green. User asked to end the session here.

## 2026-08-01 — SDR-3 (payment custody model) closed
UNDERSTAND→PLAN→EXECUTE→VERIFY: confirmed with user that SDR-3 (system-design-tawfir.md) is decided non-custodial, not just an MVP-default assumption. EXECUTE: updated system-design-tawfir.md SDR-3 decision + validation checklist, security-tawfir.md §6.1, closed the open risk in .logs/risks.md, logged the decision in .logs/decisions.md. VERIFY: docs-only change, no tests/coverage/CI impact — confirmed no code paths reference "not yet decided" language elsewhere. No SHIP push yet (not a sprint boundary); holding for user confirmation on whether to commit now.

## 2026-08-01 — CI monitoring after SDR-3 push
Push of dd7ec06 (SDR-3 closure) triggered CI run 30688379488 — GREEN, all jobs passed (Backend lint/test/coverage, Frontend Admin, Frontend Member, Security scan, Docker build). Also retroactively checked the prior unmonitored push (01683c2, session-end log commit): its CI run 30669875966 showed a Backend job failure, root-caused to a transient `wget` fetch failure downloading the Maven wrapper distribution from Maven Central (network flake on the runner, not a Checkstyle/test violation — command never actually executed). Confirmed non-recurring since the very next run against near-identical backend code passed cleanly. No fix/re-push needed; logging per rule 11 for the record.

## 2026-08-01 — phone_number blind-index encryption shipped
UNDERSTAND->BRAINSTORM->PLAN->EXECUTE->VERIFY completed for the phone_number plaintext risk (2nd item tackled after SDR-3). EXECUTE: added PhoneNumberCodec (HMAC-SHA256 blind index + AES-GCM reversible, keyed off existing PII_ENCRYPTION_KEY with domain separation), migration V10 (users.phone_number -> phone_number_hash+phone_number_encrypted; otp_challenges.phone_number -> phone_number_hash only), updated User/UserRepository/OtpChallenge/OtpChallengeRepository/AuthService/GroupService/MfaService and ~13 test files (12 originally scoped + MfaServiceTest found during a follow-up grep sweep). VERIFY: full `mvnw verify` on JDK 21 - 232/232 tests pass, 0 Checkstyle violations, JaCoCo 95.9% instructions (gate 80%). Docs updated: security-tawfir.md §5, database-tawfir.md §3/§4/§7. Risk closed in .logs/risks.md, decisions logged in .logs/decisions.md.

## 2026-08-01 — CI monitoring after phone_number encryption push
Push of e0edf59 triggered CI run 30692421669 - RED on Backend job: JwtServiceTest.parseAndValidate_rejectsTamperedToken failed intermittently (unrelated to the phone_number change - root-caused to a pre-existing flaky test: a 32-byte HS256 signature's last base64url character has 2 unused padding bits, so flipping only that character can occasionally decode to a byte-identical signature). Per rule 11, fixed by tampering the second-to-last character instead (always significant), verified with 5 sequential local runs + full mvnw verify, committed e79ed11, pushed. CI run 30693779497: GREEN, all jobs passed (Backend, Security scan, Frontend Admin, Frontend Member, Docker build).

## 2026-08-01 — per-IP OTP rate limiting shipped
UNDERSTAND->BRAINSTORM->PLAN->EXECUTE->VERIFY completed for the no-per-IP-rate-limiting risk (3rd item tackled, after SDR-3 and phone_number encryption). Confirmed with user: reuse the existing DB-backed per-phone pattern rather than in-memory/Redis; threshold 20 requests/10min (vs 5/10min per-phone) to tolerate NAT/shared-IP traffic. EXECUTE: migration V11 (otp_challenges.ip_address + index), OtpChallenge/OtpChallengeRepository/AuthService/AuthController updated, 1 new test + updates across AuthServiceTest/AuthControllerTest/9 fixture-only integration tests (13 files total, matching the plan). VERIFY: full `mvnw verify` on JDK 21 - 233/233 tests pass, 0 Checkstyle violations, JaCoCo 96.0%. Docs updated: security-tawfir.md STRIDE table, database-tawfir.md schema/index tables. Risk closed in .logs/risks.md, decisions logged in .logs/decisions.md.

## 2026-08-01 — CI monitoring after per-IP rate limiting push
Push of 7b4c7ba triggered CI run 30697638370 - GREEN on first try, all jobs passed (Backend, Security scan, Frontend Admin, Frontend Member, Docker build).

## 2026-08-03 — Notification channel EXECUTE + VERIFY (4th risk item: story 3.4 + story 4.1 TODOs)
Resumed from 2026-08-01's paused, uncommitted batch (NotificationProvider/MockNotificationProvider, NOTIFICATION_PROVIDER env var wiring, LateContributionScheduler + PayoutScheduler notification calls, ContributionScheduleRepository.findByStatusAndDueDateBefore). Ran `mvnw verify` (JDK 21) for the first time on this batch: full suite failed (43 errors) — root-caused to one real bug, the rest cascading noise. Real bug: `LateContributionSchedulerTest` constructed its `scheduler` field before `MockitoExtension` injected the `@Mock` fields (field initializer runs before extension post-processing), so the scheduler captured null repositories/mocks — NPE on first use. Fixed by moving construction into `@BeforeEach`, matching `PayoutSchedulerTest`'s already-correct pattern. Confirmed the fix by re-running the previously-failing `CmiPaymentWebhookIntegrationTest` standalone (passed, 2/2) before re-running the full suite to verify the NPE fix cleared all 43 cascading errors, not just its own 2. Full `mvnw verify`: 236/236 tests pass, 0 Checkstyle violations, JaCoCo 96.09% instructions / 93.93% branches (gate: 80%) — PASS. Docs updated: stories-tawfir.md story 3.4 ("channel TBD" -> NotificationProvider), risks.md (4th risk item closed), decisions.md (notification design + the test-bug fix logged).

## 2026-08-03 — Notification channel SHIP phase + CI monitoring
Committed 60911d2, pushed to origin/feature/sprint-2-auth-groups. CI run 30798231950 — GREEN on first try, all 5 jobs passed (Frontend Member, Frontend Admin, Backend, Security scan, Docker build). All 4 items from the 2026-08-01 risk-remediation list (SDR-3, phone_number encryption, per-IP rate limiting, notification channel) are now closed and shipped.
