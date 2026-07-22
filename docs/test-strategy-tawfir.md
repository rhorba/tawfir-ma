# Test Strategy: Tawfir.ma
**Architecture Reference**: docs/architecture-tawfir.md
**Security Reference**: docs/security-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-22 | **Author**: Test Architect

> Stories doc (docs/stories-tawfir.md) is drafted alongside this doc in the same batch — acceptance scenarios below map to its epics/stories by ID once both are approved.

## 1. Risk Assessment
| Component | Impact | Frequency | Complexity | Test Level |
|---|---|---|---|---|
| Ledger append (contribution/payout/adjustment entries) | H | H | M | Maximum |
| Group-scoped authorization (membership/role checks) | H | H | M | Maximum |
| CMI webhook consumption (payment/payout confirmation) | H | M | H | Maximum |
| OTP auth (request/verify, rate limiting) | H | H | L | High |
| Payout scheduling & execution (cron) | H | L | M | High |
| Dispute open/resolve workflow | M | M | L | High |
| Group creation/finalization (roster, payout order) | M | M | M | Standard |
| Admin oversight dashboard (read-only aggregates) | L | L | L | Minimal |
| Savings history snapshot (derived, Phase 2 feed) | L | L | L | Minimal |

## 2. Test Pyramid Targets
| Layer | Coverage Target | Tooling |
|---|---|---|
| Unit | ≥ 60% of business logic | JUnit 5 + Mockito (Spring Boot); Jest (React); Jasmine/Karma (Angular) |
| Integration | ≥ 40% of API + DB layer | Spring Boot Test + Testcontainers (real PostgreSQL, not H2 — schema uses Postgres-specific `REVOKE`/constraints) |
| E2E | Critical happy paths only | Playwright (covers both React member app and Angular admin app) |
| **Combined gate** | **≥ 80%** — non-negotiable | CI blocks merge if below (see devops doc §2) |

## 3. ATDD Acceptance Scenarios (critical paths)

```gherkin
Feature: Group creation and finalization

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

Feature: Contribution tracking

  Scenario: Member marks a contribution paid, Organizer confirms
    Given a member with a PENDING contribution due
    When the member marks it as paid
    Then the contribution status becomes MARKED_PAID
    When the Organizer confirms receipt
    Then a ledger_entry of type CONTRIBUTION is appended with source ORGANIZER_CONFIRMED
    And the contribution status becomes CONFIRMED

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

Feature: Ledger integrity

  Scenario: Ledger entries are never updated or deleted
    Given an existing ledger_entry
    When any code path attempts an UPDATE or DELETE against ledger_entries
    Then the database rejects it (REVOKE UPDATE, DELETE — see database doc §7)
    And a correction can only be made via a new entry with reversal_of_entry_id set

Feature: Disputes

  Scenario: Member opens a dispute against a ledger entry
    Given a CONFIRMED contribution the member believes is wrong
    When the member opens a dispute with a reason
    Then a dispute row is created with status OPEN
    And the related contribution_schedule status becomes DISPUTED

  Scenario: Organizer resolves a dispute
    Given an OPEN dispute
    When the Organizer resolves it as ACCEPTED with a reason
    Then the dispute status becomes ACCEPTED, resolved_by_user_id and resolution_reason are recorded
    And a corrective ledger_entry (reversal_of_entry_id set) is appended if the resolution changes the financial record

Feature: Authorization boundaries

  Scenario: Member cannot view another group's ledger
    Given a user who is not a member of Group B
    When they call GET /api/v1/groups/{B}/ledger
    Then the response is 403 Forbidden

  Scenario: Member cannot call an Admin-only endpoint
    Given an authenticated user with role MEMBER only
    When they call GET /api/v1/admin/metrics
    Then the response is 403 Forbidden

Feature: OTP authentication

  Scenario: OTP request rate limiting
    Given a phone number that has requested 5 OTPs in the last 10 minutes
    When a 6th OTP request is made for the same number
    Then the request is rejected with a rate-limit error (429)

  Scenario: Expired OTP is rejected
    Given an OTP challenge past its expires_at
    When the user submits that code
    Then verification fails and no JWT is issued

Feature: Payout execution

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

## 4. Adversarial Checklist (high-risk components only)

### Ledger & financial integrity
- [ ] Attempt direct UPDATE/DELETE on `ledger_entries` via app DB role — must fail at the database level
- [ ] Submit duplicate CMI webhook (replay) for an already-confirmed contribution — must be idempotent, no double-entry
- [ ] Submit a ledger correction without `reversal_of_entry_id` pointing to a real prior entry — must be rejected

### Auth abuse
- [ ] Unauthenticated access to any group-scoped or admin endpoint — must be 401
- [ ] Expired/tampered JWT (signature mismatch, alg confusion `none`) — must be rejected
- [ ] Refresh-token reuse after rotation (stolen + reused old token) — must be detected and both tokens revoked
- [ ] Privilege escalation: MEMBER attempts ORGANIZER-only action (finalize group, resolve dispute) on a group they don't organize — must be 403

### Cross-group / IDOR
- [ ] Every `/groups/:id/*` endpoint tested with a valid JWT for a *different* group's member — must be 403, not 404 (avoid leaking existence via status-code difference — decide and test consistently)
- [ ] Guess sequential/adjacent UUIDs — must not be enumerable in practice (UUIDv4 mitigates but authorization check is the real control)

### Input abuse
- [ ] Empty/oversized/malformed group creation payload (negative amount, zero cycles, non-MAD currency) — must fail validation server-side regardless of client-side checks
- [ ] Unicode/emoji in group name, dispute reason, evidence note — must not break rendering or storage (UTF-8 throughout)
- [ ] OTP endpoint with malformed phone number formats — must be rejected before reaching the SMS provider (cost control)

### Race conditions
- [ ] Two concurrent "mark paid" + "confirm" requests for the same contribution — must not produce two ledger entries
- [ ] Concurrent dispute-resolution requests on the same dispute — must not allow double-resolution (OPEN → ACCEPTED and OPEN → REJECTED racing)
- [ ] Concurrent payout-cron and manual-override execution for the same payout — must not double-execute

### Webhook abuse (CMI)
- [ ] Missing signature header — rejected
- [ ] Signature computed over tampered body — rejected
- [ ] Webhook flood (DoS) — rate-limited at reverse-proxy layer, doesn't starve real traffic

## 5. Release Gate Criteria
- [ ] All acceptance scenarios above pass in CI
- [ ] Combined unit + integration coverage ≥ 80% (see devops doc §2 for CI enforcement)
- [ ] No critical/high security findings open (SAST/SCA/secrets scan — devops doc §4)
- [ ] Adversarial checklist above fully executed with no unresolved findings for Ledger, Auth, and Cross-group/IDOR sections (these three are release-blocking; others are strongly recommended but not blocking for MVP)
- [ ] E2E happy paths (group creation → contribution → payout → dispute) pass via Playwright, recorded per CLAUDE.md rule 9 at version completion

### Test Strategy Validation Checklist
- [x] Every PRD functional requirement (FR-1–FR-10) maps to at least one acceptance scenario above
- [x] Coverage gate ≥ 80% confirmed and CI-enforced (see devops doc)
- [x] Adversarial review planned for high-risk components (ledger, auth, IDOR, webhook)
- [x] Release gate criteria documented — pending user agreement
