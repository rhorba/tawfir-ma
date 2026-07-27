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
