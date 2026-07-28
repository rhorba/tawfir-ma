# METRICS — Tawfir.ma

## 2026-07-22 — Sprint 2 Batch 1 (Scaffold, Docker, CI)
- Backend: 5/5 tests pass. JaCoCo coverage gate ≥80% (instruction ratio), Checkstyle (google_checks) clean.
- frontend-member: 12/12 tests pass. Coverage 96.3% stmts / 91.7% branch / 94.1% funcs / 96.3% lines (gate: 80/70/80/80). oxlint clean.
- frontend-admin: 17/17 tests pass. Coverage 100% stmts / 97.1% branch / 91.7% funcs / 100% lines (gate: 80/70/80/80). ESLint clean.
- Docker Compose stack verified end-to-end (temporary port override for local smoke test, reverted): postgres healthy, backend /actuator/health UP against real Postgres, both SPAs serve + SPA-fallback routing confirmed via curl.


## 2026-07-26 — Sprint 2 Batch 2b coverage
frontend-member: 93.05% statements / 88.88% branches / 88.46% functions (gate: 80% stmts/lines, 70% branches, 80% functions) — PASS
frontend-admin: 98.67% statements / 94.05% branches / 95% functions (gate: 80% stmts/lines, 70% branches, 80% functions) — PASS

## 2026-07-27 — Sprint 2 Batch 3 coverage
backend: 98% instructions / 98% branches (JaCoCo gate: 80%) — PASS. 18 new tests (GroupServiceTest 14, GroupControllerTest 8 minus overlap... see activity.md for exact split; GroupLifecycleIntegrationTest 4).
frontend-member: 93.1% statements / 80.23% branches / 92.72% functions (gate: 80% stmts/lines, 70% branches, 80% functions) — PASS. 23/23 tests total (6 new group-screen tests).

## 2026-07-28 — Sprint 3 Batch 1 coverage (Ledger + Contributions backend)
backend: 89/89 tests pass (`mvnw verify`, JDK 21). JaCoCo: 97.35% instructions (1543/1585) / 97.3% branches (72/74) — well above 80% gate. Checkstyle: 0 violations. New suites: ContributionServiceTest (10), ContributionControllerTest (7), ContributionLifecycleIntegrationTest (6, full-stack Testcontainers covering mark-paid/confirm/ledger-append/late-flagging/append-only-trigger/non-member-403).

## 2026-07-28 — Sprint 3 Batch 2 coverage (Disputes backend)
backend: 109/109 tests pass (`mvnw verify`, JDK 21). JaCoCo: 96.0% instructions (1781/1855) / 95.65% branches (88/92) — well above 80% gate. Checkstyle: 0 violations. New suites: DisputeServiceTest (9), DisputeControllerTest (6), DisputeLifecycleIntegrationTest (5, full-stack Testcontainers covering open-dispute/non-member-403/organizer-resolve/non-organizer-403/concurrent-resolve-race).

## 2026-07-28 — Sprint 3 Batch 3 coverage (ledger/dispute list endpoints + React wiring)
backend: 118/118 tests pass (`mvnw verify`, JDK 21). Checkstyle: 0 violations, JaCoCo gate met. New: LedgerServiceTest (3), LedgerControllerTest (2), 2 new DisputeService/Controller tests for the new list-for-group endpoint.
frontend-member: 31/31 tests pass. Coverage 86.81% statements / 74.5% branches / 85.71% functions / 88.55% lines (gate: 80% stmts/lines, 70% branches, 80% functions) — PASS. oxlint clean, `tsc -b && vite build` succeeds.
