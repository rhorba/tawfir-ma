# DevOps Foundation: Tawfir.ma
**Architecture**: docs/architecture-tawfir.md
**Security**: docs/security-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-22 | **Author**: DevOps/DevSecOps

## 1. Environment Strategy
| Environment | Purpose | Deploy Trigger |
|---|---|---|
| local | Development (Docker Compose: Spring Boot + Postgres + nginx) | Manual (`docker compose up`) |
| staging | QA / preview, mirrors prod topology at smaller scale | Auto on PR merge to `main` |
| production | Live users | Manual tag/approval after staging verification |

Single-region deployment (per System Design §1 — no multi-region at MVP). 🔶 Cloud/VPS provider not yet chosen — Docker Compose is portable to any VM host; revisit only if scale requires managed orchestration (YAGNI, per SDR-1 in system design doc).

## 2. CI Pipeline (GitHub Actions — repo remote is GitHub)
```yaml
# .github/workflows/ci.yml (to be created in Sprint 2 execution — contract defined here)
stages:
  - lint            # Backend: Checkstyle/Spotless (Spring Boot); Frontend: ESLint (React), ESLint (Angular)
  - test            # Unit + integration (JUnit/Testcontainers, Jest, Jasmine/Karma)
                     # FAILS BUILD if combined unit + integration coverage < 80% (CLAUDE.md rule 6 / test-strategy doc §2)
  - security-scan   # SAST (Semgrep), SCA (Trivy or Dependabot), secrets (Gitleaks) — see §4
  - build           # Docker images: spring-api, react-member, angular-admin
  - deploy-staging  # auto on merge to main
  - deploy-prod     # manual approval gate, tag-triggered
```
CI runs on every push and PR. Per CLAUDE.md rule 11: if CI is RED after any push, all other work stops until it's diagnosed, fixed, and pushed again to GREEN — no task is "done" while CI is red.

## 3. Infrastructure
- **Hosting**: 🔶 Not yet chosen — any Docker-capable VPS/cloud VM works for MVP (Docker Compose, single host). Recommend a Morocco-adjacent EU or Africa region provider to satisfy System Design §1's single-region/latency target.
- **Compute**: Docker containers via Docker Compose — `spring-api`, `postgres`, `nginx` (reverse proxy/TLS termination), plus static hosting or containers for the two SPA builds (React, Angular) served through nginx.
- **Database**: PostgreSQL 16, containerized for local/staging; 🔶 managed Postgres recommended for production once beyond MVP validation (reduces backup/patching burden — see database doc §1).
- **Secrets**: Environment variables only, per security doc §5. Local/staging use a `.env` file (gitignored, populated from `.env.example`); production secrets live in the hosting provider's secret store or injected via CI/CD deploy step — never committed, never baked into Docker images.
- **Monitoring**: 🔶 Tool not yet chosen. Minimum viable baseline: container stdout/stderr → structured JSON logs → any log aggregator (e.g. self-hosted Loki or a managed provider) + uptime check on the public endpoint. Defer APM/tracing until real usage data justifies the cost (YAGNI).

## 4. Security Scanning Gates
| Scanner | Scan Type | Fail Threshold |
|---|---|---|
| Semgrep | SAST — code vulnerabilities (Java + TS/JS rulesets) | Critical/high findings block merge |
| Trivy (or GitHub Dependabot) | SCA — dependency CVEs (Maven + npm x2 frontends) | Critical CVEs block merge |
| Gitleaks | Secrets detection (all commits in PR diff) | Any secret found blocks merge |

These map directly to security doc §7's dev-team requirements ("Dependencies scanned in CI") and CLAUDE.md rule 3 (verify-before-ship).

## 5. Docker Setup

```dockerfile
# spring-api/Dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/tawfir-api.jar app.jar
EXPOSE 8080
USER 1000:1000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```dockerfile
# Frontend build pattern (react-member and angular-admin), served via nginx
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

`docker-compose.yml` (local/staging) wires: `nginx` (reverse proxy, TLS termination, rate limiting per system design §2) → `spring-api` → `postgres`, plus the two frontend containers served as static assets behind nginx.

## 6. Monitoring Baseline
| Signal | Tool | Alert Threshold |
|---|---|---|
| Logs | 🔶 TBD (e.g. Loki/Grafana or managed) | Error rate spike (define exact threshold once baseline traffic is known) |
| Metrics | 🔶 TBD | API p99 latency > 300ms reads / 800ms writes (matches PRD NFR-1 / system design §1) |
| Uptime | 🔶 TBD (e.g. simple external HTTP check) | Any failed health check → page on-call (or, at this stage, notify the founder directly) |
| Payout cron | Custom check (query `payout_schedules` for stuck `PENDING` past `scheduled_date`) | Any stuck payout > 1 hour past schedule → alert (financial-impact signal, not just infra) |

## 7. Environment Variables (contract — see repo root `.env.example`)
All values below are placeholders per CLAUDE.md rule 10 — never real secrets in this doc or in `.env.example`.

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string |
| `DATABASE_USER` / `DATABASE_PASSWORD` | DB credentials (app role, with `ledger_entries` UPDATE/DELETE revoked per database doc §7) |
| `JWT_SIGNING_KEY` | Signs access/refresh tokens (ADR-3) |
| `JWT_ACCESS_TTL_MINUTES` | Access token TTL (default 15, per security doc §3) |
| `JWT_REFRESH_TTL_DAYS` | Refresh token TTL (default 7, per security doc §3) |
| `OTP_PROVIDER_API_KEY` | 🔶 SMS/OTP provider credential (provider itself still undecided — security doc §3) |
| `CMI_WEBHOOK_SECRET` | HMAC key for verifying CMI webhook signatures (security doc STRIDE row: webhook spoofing) |
| `CMI_API_KEY` | CMI merchant/API credential for initiating payment requests |
| `CORS_ALLOWED_ORIGINS` | Allowed origins for React/Angular frontends |
| `PII_ENCRYPTION_KEY` | Column-level encryption key for `phone_number`/`national_id` (database doc §7) |

### DevOps Validation Checklist
- [x] All 3 environments defined with deploy triggers
- [x] CI pipeline covers lint + test (with 80% coverage gate) + security scan + build + deploy
- [x] Coverage gate configured (< 80% fails CI) — enforced per test-strategy doc §2 and CLAUDE.md rule 6
- [x] Secrets management strategy confirmed (env vars only, no hardcoded secrets, `.env.example` documents names/purpose)
- [x] Monitoring baseline defined — several 🔶 tool choices deferred, thresholds tied back to PRD/system-design NFRs where known
