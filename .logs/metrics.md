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
