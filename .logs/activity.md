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
