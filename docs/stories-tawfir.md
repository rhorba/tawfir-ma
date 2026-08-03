# Stories: Tawfir.ma
**PRD**: docs/prd-tawfir.md
**Architecture**: docs/architecture-tawfir.md
**Test Strategy**: docs/test-strategy-tawfir.md

## Epic 1: Authentication
Phone-number OTP login issuing JWT access/refresh tokens, shared by both frontends.

### Story 1.1: Request OTP
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev

As a **user**, I want to request an OTP by phone number, so that I can log in without a password.

**Acceptance Criteria**:
```gherkin
Scenario: OTP request rate limiting
  Given a phone number that has requested 5 OTPs in the last 10 minutes
  When a 6th OTP request is made for the same number
  Then the request is rejected with a rate-limit error (429)
```
**Technical Notes**: `POST /api/v1/auth/otp/request` (architecture doc §5). Stores hashed code in `otp_challenges` (database doc §3), never the raw code. Rate limit per phone + per IP (security doc STRIDE — DoS via OTP spam).
**Dependencies**: None (foundation story).

---

### Story 1.2: Verify OTP and issue tokens
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev

As a **user**, I want to verify my OTP and receive a session, so that I can use the app.

**Acceptance Criteria**:
```gherkin
Scenario: Expired OTP is rejected
  Given an OTP challenge past its expires_at
  When the user submits that code
  Then verification fails and no JWT is issued
```
**Technical Notes**: `POST /api/v1/auth/otp/verify` issues JWT access (≤15min TTL) + refresh (≤7day TTL, rotated on use) per ADR-3 and security doc §3.
**Dependencies**: 1.1.

---

### Story 1.3: Refresh and logout
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev

As a **user**, I want my session to refresh silently and to be able to log out, so that I stay authenticated without re-entering an OTP every 15 minutes, and can end my session on demand.

**Technical Notes**: `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`. Refresh-token reuse detection (adversarial checklist, test-strategy doc §4) — reused-after-rotation token revokes the whole token family.
**Dependencies**: 1.2.

---

### Story 1.4: Admin second factor (TOTP)
**Priority**: Should | **Size**: M | **Specialist**: Backend Dev

As an **Admin**, I want a second factor beyond OTP, so that a compromised phone number alone can't grant platform-wide admin access.

**Technical Notes**: Security doc §3 — MFA required specifically for `ADMIN` role given blast radius. Can ship after MVP member/organizer flows if needed to hit a launch date (flag as negotiable Should, not Must).
**Dependencies**: 1.2.

## Epic 2: Group Lifecycle
Create, configure, and finalize a daret group.

### Story 2.1: Create group (draft)
**Priority**: Must | **Size**: M | **Specialist**: Backend Dev

As an **Organizer**, I want to create a group with name, amount, frequency, cycle count, and member list, so that the group has one shared source of truth. (PRD FR-1)

**Acceptance Criteria**:
```gherkin
Scenario: Invalid group parameters rejected
  Given a group creation request with a negative contribution_amount or zero total_cycles
  When submitted
  Then the request is rejected with a validation error (400)
```
**Technical Notes**: `POST /api/v1/groups` (architecture doc §5). Maps to `groups` + `group_memberships` tables (database doc §3). Status starts `DRAFT`.
**Dependencies**: Epic 1 (auth).

---

### Story 2.2: Configure payout order
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev

As an **Organizer**, I want to set payout order manually or let the system randomize it, so that rotation is fair and members can't dispute the sequence. (PRD FR-1)

**Technical Notes**: `payout_order_mode` on `groups` (`MANUAL`/`RANDOMIZED`). Randomization must be deterministic-once-set (stored, not re-rolled) — assign `payout_position` on `group_memberships` at finalize time.
**Dependencies**: 2.1.

---

### Story 2.3: Finalize group, generate schedule
**Priority**: Must | **Size**: M | **Specialist**: Backend Dev

As an **Organizer**, I want to finalize the group and get a computed payout schedule, so that everyone knows when they contribute and when they receive. (PRD FR-2)

**Acceptance Criteria**:
```gherkin
Scenario: Organizer creates and finalizes a group
  Given an authenticated user with role ORGANIZER-eligible
  When they create a group with amount, frequency, cycle count, and member roster
  And they finalize the group
  Then a payout schedule is generated for every cycle
  And every member has a contribution_schedule row per cycle
  And the group status becomes ACTIVE

Scenario: Finalization rejected with incomplete roster
  Given a DRAFT group with fewer members than payout positions require
  When the Organizer attempts to finalize
  Then the request is rejected with a validation error
  And the group status remains DRAFT
```
**Technical Notes**: `POST /api/v1/groups/:id/finalize`. Populates `contribution_schedules` + `payout_schedules` (database doc §3, §5 migrations 003). Group config becomes immutable after this point (ADR context: PATCH only allowed pre-finalize per architecture doc §5).
**Dependencies**: 2.1, 2.2.

---

### Story 2.4: List and view group detail
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev + Frontend Dev (React)

As a **Member**, I want to see my groups and drill into a group's schedule/ledger, so that I know what I owe and when I'm due to receive the payout. (PRD FR-2, user story §3)

**Acceptance Criteria**:
```gherkin
Scenario: Member cannot view another group's ledger
  Given a user who is not a member of Group B
  When they call GET /api/v1/groups/{B}/ledger
  Then the response is 403 Forbidden
```
**Technical Notes**: `GET /api/v1/groups`, `GET /api/v1/groups/:id`. Resource-level membership check on every group-scoped endpoint (security doc §4 — this is the pattern every later group-scoped story reuses). UX: Group Detail screen (ux-tawfir.md §4).
**Dependencies**: 2.3.

## Epic 3: Contributions
Mark-paid / confirm workflow, plus automated CMI confirmation.

### Story 3.1: Member marks contribution paid
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev + Frontend Dev (React)

As a **Member**, I want to mark my contribution as paid, so that the Organizer and other members know without me messaging them directly. (PRD FR-4)

**Technical Notes**: `POST /api/v1/groups/:id/contributions/:scheduleId/mark-paid`, self-only (security doc). Sets `contribution_schedules.status = MARKED_PAID`.
**Dependencies**: Epic 2.

---

### Story 3.2: Organizer confirms contribution
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev

As an **Organizer**, I want to confirm a member's contribution was received, so that the ledger reflects reality. (PRD FR-4)

**Acceptance Criteria**:
```gherkin
Scenario: Member marks a contribution paid, Organizer confirms
  Given a member with a PENDING contribution due
  When the member marks it as paid
  Then the contribution status becomes MARKED_PAID
  When the Organizer confirms receipt
  Then a ledger_entry of type CONTRIBUTION is appended with source ORGANIZER_CONFIRMED
  And the contribution status becomes CONFIRMED
```
**Technical Notes**: `POST /api/v1/groups/:id/contributions/:scheduleId/confirm`. First ledger-append story — enforces ADR-2 append-only rule end to end (no update path exists, ever).
**Dependencies**: 3.1.

---

### Story 3.3: CMI webhook auto-confirms contribution
**Priority**: Must | **Size**: L | **Specialist**: Backend Dev

As the **system**, I want to auto-confirm a contribution when CMI reports a matching payment, so that Organizers don't have to manually confirm every transaction. (PRD FR-4, Architecture ADR-2/§6)

**Acceptance Criteria**:
```gherkin
Scenario: CMI webhook confirms a contribution automatically
  Given a MARKED_PAID contribution awaiting confirmation
  When a validly-signed CMI webhook reports the matching payment
  Then a ledger_entry is appended with source CMI_WEBHOOK
  And the contribution status becomes CONFIRMED
  And no manual Organizer action was required

Scenario: CMI webhook with invalid signature is rejected
  Given a webhook payload with a missing or invalid HMAC signature
  When it is submitted to /api/v1/webhooks/cmi/payment-confirmation
  Then the request is rejected with 401/403
  And no ledger_entry is created
```
**Technical Notes**: `POST /api/v1/webhooks/cmi/payment-confirmation`, HMAC-signed (security doc §2). Idempotency required — replayed webhook for an already-CONFIRMED contribution must not double-append (test-strategy adversarial checklist §4). ⚠️ Blocked on SDR-3 (custody model) and OTP/payment provider selection being confirmed — flag before starting.
**Dependencies**: 3.2.

---

### Story 3.4: Auto-flag late contributions
**Priority**: Should | **Size**: S | **Specialist**: Backend Dev

As an **Organizer**, I want overdue contributions auto-flagged, so that I don't have to manually track due dates. (PRD FR-5)

**Technical Notes**: Scheduled job (Spring `@Scheduled`) transitions `PENDING` → `LATE` past `due_date`; notifies Organizer + member via `NotificationProvider` (mock-first, same pattern as `OtpProvider`/`CmiClient` — swap to a real SMS/push implementation pre-launch).
**Dependencies**: 3.1.

## Epic 4: Payouts

### Story 4.1: Automatic payout execution
**Priority**: Must | **Size**: L | **Specialist**: Backend Dev

As the **system**, I want to execute a payout automatically when all contributions for a cycle are confirmed and the date arrives, so that payouts don't require manual triggering every cycle. (PRD FR-10)

**Acceptance Criteria**:
```gherkin
Scenario: Scheduled payout executes automatically when contributions are confirmed
  Given all contributions for a cycle are CONFIRMED and the scheduled_date has arrived
  When the payout cron runs
  Then the payout_schedule status becomes EXECUTED
  And a ledger_entry of type PAYOUT is appended

Scenario: Payout flagged for manual intervention when funds aren't confirmed
  Given a cycle with at least one non-CONFIRMED contribution and scheduled_date has arrived
  When the payout cron runs
  Then the payout is NOT auto-executed
  And the Organizer is notified for manual override
```
**Technical Notes**: Scheduled job, `PATCH`-equivalent on `payout_schedules.status`. Monitored per devops doc §6 (stuck-payout alert).
**Dependencies**: Epic 3.

---

### Story 4.2: Organizer manual payout override
**Priority**: Must | **Size**: M | **Specialist**: Backend Dev

As an **Organizer**, I want to manually trigger or override a payout when auto-execution didn't fire, so that the group isn't blocked by an edge case. (PRD FR-10)

**Technical Notes**: `POST /api/v1/groups/:id/payouts/:payoutId/execute` (ORGANIZER-authorized path, per architecture doc §5). Sets status `MANUAL_OVERRIDE`, still appends a `ledger_entry`.
**Dependencies**: 4.1.

---

### Story 4.3: CMI webhook confirms payout sent
**Priority**: Must | **Size**: M | **Specialist**: Backend Dev

As the **system**, I want to receive CMI confirmation that a payout was actually sent, so that the ledger reflects real-world money movement, not just an internal scheduling decision.

**Technical Notes**: `POST /api/v1/webhooks/cmi/payout-confirmation`, same HMAC-verification pattern as 3.3. ⚠️ Same SDR-3 dependency as 3.3.
**Dependencies**: 4.1, 3.3 (shares webhook signature-verification infrastructure).

## Epic 5: Disputes

### Story 5.1: Open a dispute
**Priority**: Must | **Size**: S | **Specialist**: Backend Dev + Frontend Dev (React)

As a **Member**, I want to open a dispute against a ledger entry with a reason, so that disagreements have a record instead of relying on memory. (PRD FR-6)

**Acceptance Criteria**:
```gherkin
Scenario: Member opens a dispute against a ledger entry
  Given a CONFIRMED contribution the member believes is wrong
  When the member opens a dispute with a reason
  Then a dispute row is created with status OPEN
  And the related contribution_schedule status becomes DISPUTED
```
**Technical Notes**: `POST /api/v1/groups/:id/disputes`. Any group member, not self-only (a member can dispute another member's marked-paid claim).
**Dependencies**: Epic 3.

---

### Story 5.2: Resolve a dispute
**Priority**: Must | **Size**: M | **Specialist**: Backend Dev + Frontend Dev (React)

As an **Organizer** (or Admin), I want to accept or reject a dispute with a reason, so that resolution is attributable and auditable. (PRD FR-6, NFR-5)

**Acceptance Criteria**:
```gherkin
Scenario: Organizer resolves a dispute
  Given an OPEN dispute
  When the Organizer resolves it as ACCEPTED with a reason
  Then the dispute status becomes ACCEPTED, resolved_by_user_id and resolution_reason are recorded
  And a corrective ledger_entry (reversal_of_entry_id set) is appended if the resolution changes the financial record
```
**Technical Notes**: `PATCH /api/v1/disputes/:id/resolve`. Concurrent double-resolution must be prevented (test-strategy adversarial checklist §4 — race conditions).
**Dependencies**: 5.1.

## Epic 6: Admin Oversight

### Story 6.1: Admin group + metrics dashboard
**Priority**: Must | **Size**: M | **Specialist**: Backend Dev + Frontend Dev (Angular)

As an **Admin**, I want to view platform-wide group health, so that I can intervene before a group collapses. (PRD FR-9)

**Acceptance Criteria**:
```gherkin
Scenario: Member cannot call an Admin-only endpoint
  Given an authenticated user with role MEMBER only
  When they call GET /api/v1/admin/metrics
  Then the response is 403 Forbidden
```
**Technical Notes**: `GET /api/v1/admin/groups`, `GET /api/v1/admin/metrics`, `GET /api/v1/admin/disputes`. `@PreAuthorize` role check tested explicitly, not just frontend-hidden (security doc STRIDE — Elevation of Privilege).
**Dependencies**: Epic 4, Epic 5 (needs real data to aggregate).

**Sprint 5 Batch 2 implementation note (2026-07-31, see .logs/decisions.md)**: built against Epic 2/3/5 data already in place rather than waiting on Epic 4 (still blocked on SDR-3) — `defaultRatePercent`/`atRiskGroups` computed from contribution lateness + open disputes, no payout-execution data involved. Role check uses this codebase's established manual `requireAdmin(Authentication)` pattern (see `MfaController`/`GroupController`), not `@PreAuthorize` — no `@EnableMethodSecurity` is configured anywhere in this project.

## Epic 7: Savings History (API contract only — Phase 2 consumption deferred)

### Story 7.1: Record savings history snapshot
**Priority**: Should | **Size**: S | **Specialist**: Backend Dev

As the **system**, I want to record a savings-history snapshot when a member completes a group cycle, so that reliable members build a portable record. (PRD FR-8)

**Technical Notes**: Populates `savings_history_snapshots` (database doc §3) on cycle completion. `GET /api/v1/users/:id/savings-history` exposed now; `GET /api/v1/internal/kasb-export` contract reserved but not required to function in MVP (architecture doc §5 — explicitly Phase 2).
**Dependencies**: Epic 4 (a completed cycle requires payouts to have executed).

**Sprint 5 Batch 3 implementation note (2026-07-31, see .logs/decisions.md)**: "cycle completion" is a proxy — every member's cycle contribution reaching CONFIRMED — since Epic 4 (real payout execution) is still blocked on SDR-3. `on_time_rate` needed a new `contribution_schedules.was_late` column (V8) since the current `status` alone loses late-history once a contribution reaches CONFIRMED.

## Sprint Allocation 🔶 (indicative — re-plan once Sprint 2 actually starts, this is a first pass)

| Sprint | Stories | Notes |
|---|---|---|
| Sprint 2 | 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4 | Auth + group lifecycle — nothing financial moves yet |
| Sprint 3 | 3.1, 3.2, 3.4, 5.1, 5.2 | Manual contribution + dispute flow, no CMI dependency — unblocks a demo without SDR-3 resolved |
| Sprint 4 | 3.3, 4.1, 4.2, 4.3 | CMI webhook integration + payouts — **blocked until SDR-3 (custody model) is confirmed** (system-design doc, risks.md) |
| Sprint 5 | 1.4, 6.1, 7.1 | Admin MFA, admin dashboard, savings history — can run in parallel with Sprint 4 if staffed separately |

### Story Validation Checklist
- [x] Every PRD requirement (FR-1–FR-10) maps to at least one story above
- [x] Every story has testable acceptance criteria (pulled directly from test-strategy doc's Gherkin scenarios where a scenario exists)
- [x] Dependencies identified and ordered (Epic 1 → 2 → 3/5 → 4 → 6/7)
- [x] Sizes are S/M/L only — nothing oversized; 3.3/4.1 (L) are the CMI-integration stories, appropriately the biggest unknowns
- [x] Architecture decisions referenced in technical notes (ADR-2 append-only, ADR-3 auth, endpoint contracts)
- [x] Security requirements reflected (resource-level checks, webhook HMAC, rate limiting, MFA for admin)
- [x] Sprint 4 explicitly flagged as blocked on the open SDR-3 custody-model decision — not silently assumed
