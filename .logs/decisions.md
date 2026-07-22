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
