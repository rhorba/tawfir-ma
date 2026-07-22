# Architecture: Tawfir.ma
**PRD Reference**: docs/prd-tawfir.md
**System Design Reference**: docs/system-design-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: Software Architect

## 1. Overview
A single Spring Boot monolith (package-by-feature, layered architecture) backs two SPA frontends — React for members, Angular for admins — with PostgreSQL as the sole datastore. No microservices, no queue, no cache at MVP scale (see System Design SDR-1).

## 2. Architecture Decision Records

### ADR-1: Layered architecture, package-by-feature
- **Status**: Accepted
- **Context**: Domain (groups, contributions, payouts, disputes) is moderately complex but not deep enough to justify full Clean/Hexagonal or DDD aggregates on day one.
- **Decision**: Standard Spring Boot layered architecture (`controller` → `service` → `repository` → `entity`), organized by feature package (`group/`, `contribution/`, `payout/`, `dispute/`, `auth/`, `user/`) rather than by technical layer.
- **Alternatives**: Hexagonal/ports-and-adapters (rejected — no multiple I/O adapters beyond REST + scheduled jobs, doesn't earn its complexity yet); full DDD aggregates (rejected — revisit if payout/dispute rules grow substantially more complex).
- **Consequences**: + Fast to build, familiar Spring idioms, easy onboarding. − Less isolation if the domain grows a lot; a refactor to Clean Architecture is possible later without a full rewrite since packages are already feature-scoped.
- **Re-evaluate when**: Business rules for payouts/disputes grow complex enough that domain logic is leaking into controllers/services in ways that are hard to test.

### ADR-2: Immutable, append-only ledger
- **Status**: Accepted
- **Context**: PRD FR-7/NFR-4 require an auditable, tamper-evident record of every contribution, payout, and dispute event — this is the product's core trust mechanism.
- **Decision**: `ledger_entry` table is insert-only. Corrections are new offsetting entries referencing the original, never UPDATE/DELETE on financial rows. Enforced at the repository layer (no `update`/`delete` methods exposed for ledger entities) and reinforced with a DB-level trigger or `REVOKE UPDATE, DELETE` grant (see database doc).
- **Alternatives**: Mutable rows with an `updated_at`/audit-log side table (rejected — audit log itself could be tampered with or forgotten; append-only is simpler to reason about and matches financial-ledger best practice).
- **Consequences**: + Strong auditability, dispute resolution has a clear trail. − Slightly more complex queries (must compute "current state" by folding events), mitigated with a materialized/derived `group_balance` view.
- **Re-evaluate when**: Never, unless a fundamentally different consistency model is required.

### ADR-3: Stateless JWT auth, phone OTP
- **Status**: Accepted
- **Context**: PRD FR-3 requires phone-based auth without passwords; System Design requires the API to be stateless for horizontal scaling.
- **Decision**: Spring Security with a custom OTP-verification flow issuing a JWT (access token, short TTL + refresh token). No server-side session store.
- **Alternatives**: Session cookies with server-side store (rejected — adds a stateful dependency the system doesn't need at this scale).
- **Consequences**: + Easy horizontal scaling, simple for two separate SPA frontends to consume. − JWT revocation requires a short-TTL + refresh-rotation strategy (detailed in security doc) since JWTs can't be "deleted" server-side.
- **Re-evaluate when**: N/A for MVP.

### ADR-4: One API, two frontends, role-scoped
- **Status**: Accepted
- **Context**: React (members) and Angular (admin) both consume the same domain — duplicating backends would duplicate business logic and bugs.
- **Decision**: Single REST API; endpoints are role-gated (`MEMBER`/`ORGANIZER`/`ADMIN`) via Spring Security method-level authorization (`@PreAuthorize`).
- **Alternatives**: Separate admin-service (rejected — YAGNI, no scale/compliance reason to isolate yet).
- **Consequences**: + Single source of truth for business logic. − Requires discipline in endpoint authorization tests (covered in test strategy doc's adversarial checklist).

## 3. System Design (component view — detail beyond System Design doc)
```
[React Member App] ──┐
                      ├─→ [nginx reverse proxy] ─→ [Spring Boot Monolith]
[Angular Admin App] ──┘                                   │
                                              ┌────────────┼─────────────┐
                                        [Auth module]  [Domain modules]  [Scheduled jobs]
                                              │               │                │
                                        [OTP provider]   [PostgreSQL]   [Payout cron, dispute SLA checks]
                                                                │
                                                          [CMI webhook consumer]
```

## 4. Data Model (overview — full schema in database doc)
```
User ──1:N──> GroupMembership <──N:1── Group
Group ──1:N──> PayoutSchedule ──1:N──> PayoutEvent
Group ──1:N──> ContributionSchedule ──1:N──> LedgerEntry
LedgerEntry ──0:N──> Dispute
User ──1:N──> SavingsHistorySnapshot   (derived/materialized, feeds Kasb export)
```
- `Group` owns its `ContributionSchedule` and `PayoutSchedule` (aggregate-ish boundary, enforced via ADR-1's package structure even without full DDD).
- `LedgerEntry` is the single append-only source of truth; `PayoutEvent` and contribution status are derived/read views over it plus their own scheduling metadata.

## 5. API Design (detailed contracts)

### Auth
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | /api/v1/auth/otp/request | Request OTP for a phone number | Public (rate-limited) |
| POST | /api/v1/auth/otp/verify | Verify OTP, receive JWT access + refresh token | Public (rate-limited) |
| POST | /api/v1/auth/refresh | Exchange refresh token for new access token | Refresh token required |
| POST | /api/v1/auth/logout | Invalidate refresh token | Authenticated |

### Groups
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | /api/v1/groups | Create group (name, amount, frequency, cycles, members, payout order) | ORGANIZER |
| GET | /api/v1/groups | List groups the current user belongs to | Authenticated |
| GET | /api/v1/groups/:id | Get group detail (schedule, roster, status) | Member of group or ADMIN |
| PATCH | /api/v1/groups/:id | Update group config (before first cycle starts only) | ORGANIZER of group |
| POST | /api/v1/groups/:id/finalize | Lock roster + payout order, generate schedule | ORGANIZER of group |
| GET | /api/v1/groups/:id/ledger | Full append-only ledger for the group | Member of group or ADMIN |

### Contributions
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | /api/v1/groups/:id/contributions/:scheduleId/mark-paid | Member marks their contribution as paid | Member (self only) |
| POST | /api/v1/groups/:id/contributions/:scheduleId/confirm | Organizer/CMI-webhook confirms receipt | ORGANIZER or system (CMI webhook) |
| GET | /api/v1/groups/:id/contributions | List contribution status for all members | Member of group or ADMIN |
| POST | /api/v1/webhooks/cmi/payment-confirmation | CMI async callback confirming a transfer | Signed webhook (HMAC verification, not user auth) |

### Payouts
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | /api/v1/groups/:id/payouts | Payout schedule + status | Member of group or ADMIN |
| POST | /api/v1/groups/:id/payouts/:payoutId/execute | Trigger payout (auto via cron, or manual Organizer override) | System (cron) or ORGANIZER |
| POST | /api/v1/webhooks/cmi/payout-confirmation | CMI async callback confirming payout sent | Signed webhook |

### Disputes
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | /api/v1/groups/:id/disputes | Open a dispute against a ledger entry (with note/evidence) | Member of group |
| GET | /api/v1/groups/:id/disputes | List disputes for a group | Member of group or ADMIN |
| PATCH | /api/v1/disputes/:id/resolve | Resolve (accept/reject) with reason | ORGANIZER of group or ADMIN |

### Savings History / Kasb export (reserved for Phase 2, contract locked now)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | /api/v1/users/:id/savings-history | Completed cycles, on-time rate, dispute count | Self or ADMIN |
| GET | /api/v1/internal/kasb-export | Batch export for Kasb scoring feed (Phase 2) | Internal service-to-service auth (not built in MVP) |

### Admin
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | /api/v1/admin/groups | All groups, filterable by status/health | ADMIN |
| GET | /api/v1/admin/metrics | Default rate, dispute rate, active group count | ADMIN |
| GET | /api/v1/admin/disputes | All open disputes across platform | ADMIN |

## 6. Security Considerations (summary — full detail in security doc)
- Authentication: Phone OTP → JWT (short TTL access + rotating refresh token)
- Authorization: Role + resource-level checks (group membership required for group-scoped endpoints — enforced in service layer, not just controller annotations)
- Data protection: PII (phone numbers, national ID if collected for KYC) encrypted at rest; HTTPS enforced everywhere; CMI webhook signatures verified (HMAC) to prevent spoofed payment confirmations
- Key risks: Payout-confirmation spoofing (mitigated by webhook signature verification), dispute evidence tampering (mitigated by append-only ledger + immutable evidence attachments)

## 7. Infrastructure
- Hosting: 🔶 not yet chosen — Docker Compose works on any VPS/cloud VM for MVP (see devops doc)
- Database: PostgreSQL 16 (single instance, managed or self-hosted — see devops doc)
- CI/CD: 🔶 GitHub Actions assumed (repo is on GitHub) — see devops doc
- Monitoring: 🔶 TBD, see devops doc

## 8. Technical Risks
| Risk | Mitigation | Owner |
|---|---|---|
| JWT refresh-token theft/replay | Refresh token rotation + reuse detection, short access-token TTL | Backend Dev / Security Engineer |
| CMI webhook spoofing | HMAC signature verification on all webhook endpoints, IP allowlist if CMI supports it | Backend Dev / Security Engineer |
| Ledger query performance as history grows | Derived `group_balance`/`current_state` materialized view, indexed by group | DBA |
| Two-frontend auth drift (React/Angular implementing token refresh differently) | Shared, documented auth contract (this doc §5) — both frontends implement against the same spec | Frontend Dev (both apps) |

### Architecture Validation Checklist
- [x] Every PRD requirement has an architectural solution
- [x] ADRs document all significant choices
- [x] Data model supports all user stories
- [x] API design covers all functional requirements
- [x] Security requirements addressed (summary here, full doc next batch)
- [x] NFRs have architectural support
- [x] No over-engineering (YAGNI check) — monolith, no queue, no cache, no microservices
