# METRICS — Tawfir.ma

## 2026-07-22 — Sprint 2 Batch 1 (Scaffold, Docker, CI)
- Backend: 5/5 tests pass. JaCoCo coverage gate ≥80% (instruction ratio), Checkstyle (google_checks) clean.
- frontend-member: 12/12 tests pass. Coverage 96.3% stmts / 91.7% branch / 94.1% funcs / 96.3% lines (gate: 80/70/80/80). oxlint clean.
- frontend-admin: 17/17 tests pass. Coverage 100% stmts / 97.1% branch / 91.7% funcs / 100% lines (gate: 80/70/80/80). ESLint clean.
- Docker Compose stack verified end-to-end (temporary port override for local smoke test, reverted): postgres healthy, backend /actuator/health UP against real Postgres, both SPAs serve + SPA-fallback routing confirmed via curl.

