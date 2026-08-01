# System Design: Tawfir.ma
**PRD Reference**: docs/prd-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: System Designer

## 1. Non-Functional Requirements
| Attribute | Target | Notes |
|---|---|---|
| Availability | 99.9% SLA (8.7 hr/yr downtime) | 🔶 default per YAGNI — payout scheduling is date-based, not real-time-critical; short outages don't lose money, they delay a cron run |
| Latency (p99) | < 300ms API reads, < 800ms writes (incl. ledger append) | Matches PRD NFR-1 |
| Throughput | 🔶 ~50-100 RPS peak at MVP scale (assumes low thousands of DAU) | Re-evaluate when real usage data exists |
| Data Volume | < 1 GB/day at MVP scale | Ledger entries are small structured rows, not media |
| Retention | Indefinite for ledger/savings-history (it's the product's core value — feeds Kasb); 90 days for raw logs | Financial record retention in Morocco typically expects multi-year retention — confirm exact period with legal/compliance, not assumed here |
| Geo | Single region (Morocco-adjacent EU/Africa data center) | No multi-region — YAGNI at this scale |
| Recovery (RTO) | 4 hours | Single-region, restore-from-backup acceptable at this stage |
| Recovery (RPO) | 15 minutes | Postgres WAL-based point-in-time recovery |

## 2. Component Topology

```
[Clients]
  ├── React Member Web App (SPA)
  └── Angular Admin Web App (SPA)
        ↓ HTTPS
[Reverse Proxy / TLS termination]  ←── nginx (Docker container), rate limiting at this layer
        ↓
[Spring Boot API] (single monolith service — see architecture doc for internal layering)
  ├── Auth module        → OTP provider (external SMS API — TBD)
  ├── Group module        → PostgreSQL
  ├── Contribution/Ledger module → PostgreSQL (append-only tables)
  ├── Payout module        → PostgreSQL + CMI webhook consumer
  ├── Dispute module        → PostgreSQL
  └── Kasb export module    → PostgreSQL (read replica or same DB, exposed via internal API)
        ↓
[PostgreSQL] (single primary, MVP scale — see database doc)
        ↓
[External Integrations]
  ├── CMI (mobile money — contribution/payout confirmation webhooks)
  └── OTP/SMS provider (phone auth) — 🔶 provider TODO, see security doc
        ↓
[Observability: Docker container logs → structured JSON → log aggregator (🔶 pick one in devops doc) → alerts]
```

**No message queue, no cache layer, no service mesh at MVP scale** — a single Spring Boot monolith with scheduled jobs (Spring `@Scheduled` or Quartz) covers payout-date processing. Re-introduce a queue only if async volume (e.g. CMI webhook bursts, SMS retries) actually becomes a bottleneck.

## 3. Integration Patterns
| Integration | Pattern | Reason |
|---|---|---|
| CMI (payments) | Webhook (async callback) + REST (initiate) | CMI confirms payment/payout asynchronously; Tawfir can't block a request thread waiting on a bank rail |
| OTP/SMS provider | REST (synchronous send), async delivery | Standard for SMS gateways; short-lived OTP codes stored server-side with TTL |
| React ↔ Spring Boot | REST/JSON over HTTPS | Simple CRUD-shaped domain — no need for GraphQL/BFF layer (YAGNI) |
| Angular ↔ Spring Boot | Same REST API as React app, scoped by role (ADMIN) | One API serving both frontends — avoids duplicating backend logic per frontend |
| Kasb (future, Phase 2) | REST pull or scheduled export | Not built in MVP; API contract reserved so Phase 2 doesn't require a data-model rework |

## 4. Scalability Strategy
- Scaling approach: **Vertical first** — a single Spring Boot container + single Postgres instance handles MVP load comfortably. Horizontal scaling (multiple API replicas behind the reverse proxy) is a config change away when needed, since the API should be stateless (JWT auth, no server-side session).
- Cache strategy: **None at MVP.** Add Redis only if read-heavy endpoints (e.g. group ledger views) show measured latency problems.
- Queue strategy: **None at MVP.** Spring's built-in `@Scheduled` covers payout-date cron jobs at this scale. Revisit (e.g. Spring Cloud Stream + a broker) only if webhook volume or notification fan-out becomes bursty.

## 5. System Design Decision Records

### SDR-1: Monolith vs microservices
- **NFR Driver**: Team size, throughput target (~50-100 RPS), YAGNI principle
- **Options**:
  - 🟢 Simple: Single Spring Boot monolith, modular packages
  - 🟡 Balanced: Monolith now, extract Payment module later if it needs independent scaling/compliance isolation
  - 🔴 Custom: Microservices from day one (group-service, payment-service, dispute-service)
- **Decision**: 🟢 Single Spring Boot monolith, package-by-feature (see architecture doc). Financial-logic isolation is handled at the module/package level, not via network boundaries.
- **Trade-offs**: Less deployment independence; acceptable at this scale. Revisit if the Payment/CMI module needs separate compliance scoping (e.g. PCI-adjacent isolation) as the product grows.
- **Re-evaluate when**: Team splits into multiple independent squads, or the payment module needs a separate compliance/security perimeter.

### SDR-2: Two frontends, one API
- **NFR Driver**: Distinct member vs admin user bases (per user's stack decision: React for members, Angular for admin)
- **Decision**: One Spring Boot REST API serves both apps, differentiated by role-based authorization (`MEMBER`, `ORGANIZER`, `ADMIN`), not separate backend deployments.
- **Trade-offs**: API must carefully scope admin-only endpoints; slightly more auth complexity than a single-frontend app, but far simpler than running two backends.
- **Re-evaluate when**: Admin functionality diverges so much it needs its own data model (unlikely at this scale).

### SDR-3: Payment custody model
- **NFR Driver**: Regulatory risk (flagged in PRD §7 and will be detailed in security doc)
- **Options**:
  - 🟢 Non-custodial: Tawfir never touches funds — members transfer via CMI/bank directly, app only records confirmation
  - 🔴 Custodial: Tawfir holds/moves funds itself via a CMI merchant account it controls
- **Decision**: 🟢 **Confirmed non-custodial (2026-08-01).** Tawfir never holds or moves member funds; members transfer directly via CMI/bank rails and the app only records confirmation. Matches what Sprint 4's webhook/payout implementation already does against `MockCmiClient`.
- **Re-evaluate when**: Business requirements force custody (e.g. to guarantee payout timing) — would require a revision pass on this doc plus security/database docs before any custodial code is written.

## System Design Validation Checklist
- [x] All NFRs captured with measurable targets (some 🔶 placeholders pending real usage data)
- [x] Topology fits current project scale (single monolith, no premature distributed-systems complexity)
- [x] Data flow covers read and write paths
- [x] Integration patterns chosen with justification
- [x] SDRs document all key decisions — SDR-3 confirmed non-custodial 2026-08-01, no longer open
