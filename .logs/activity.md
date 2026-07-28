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
