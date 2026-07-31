# Database Design: Tawfir.ma
**Architecture Reference**: docs/architecture-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: DBA

## 1. Database Selection
- **Engine**: PostgreSQL 16
- **Rationale**: Structured, relational, transactional data (users, groups, memberships, an append-only financial ledger) — exactly PostgreSQL's sweet spot. No document/graph/time-series needs at this scale (YAGNI).
- **Hosting**: 🔶 Not yet chosen — Docker Compose service for MVP (see devops doc); managed Postgres (e.g. a cloud provider's managed service) recommended once beyond local/staging.

## 2. Entity-Relationship Model
```
User ──1:N──> GroupMembership <──N:1── Group
Group ──1:1──> PayoutOrderConfig
Group ──1:N──> ContributionSchedule
Group ──1:N──> PayoutSchedule
ContributionSchedule ──1:N──> LedgerEntry (type=CONTRIBUTION)
PayoutSchedule ──1:N──> LedgerEntry (type=PAYOUT)
LedgerEntry ──0:N──> Dispute
User ──1:N──> SavingsHistorySnapshot (derived, one row per completed group cycle)
User ──1:1──> AuthIdentity (phone number, OTP state)
```

## 3. Schema Design

```sql
-- Table: users
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number  VARCHAR(20) NOT NULL UNIQUE,   -- 🔶 stored plaintext for Sprint 2 — encryption-at-rest
                                                -- (§7) deferred: AES-GCM's random IV breaks the UNIQUE
                                                -- constraint/equality lookup needed for login, so this
                                                -- needs a blind-index design (separate HMAC lookup column),
                                                -- not a drop-in converter. Must land before any shared/staging
                                                -- environment holds real phone numbers.
  full_name     VARCHAR(255),                  -- nullable (changed from v1.0): OTP verify auto-creates the
                                                -- user record on first login, before any name is collected;
                                                -- filled in later via Profile/Settings (ux-tawfir.md IA)
  national_id   VARCHAR(50),                    -- nullable; 🔶 confirm if required for MVP KYC
  role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'ADMIN')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  totp_secret     VARCHAR(255),                -- added V7 (Sprint 5 Batch 1, story 1.4): AES-GCM
                                                  -- encrypted+base64 TOTP secret (AesGcmEncryptor,
                                                  -- app-layer, reuses PII_ENCRYPTION_KEY); null until
                                                  -- an admin calls POST /api/v1/admin/mfa/setup
  totp_enabled_at TIMESTAMPTZ,                   -- added V7; null until the first successful
                                                  -- POST /api/v1/admin/mfa/verify — a stored-but-
                                                  -- unverified secret alone never satisfies MFA
  mfa_failed_attempts SMALLINT NOT NULL DEFAULT 0, -- added V7; shared brute-force counter for both
                                                  -- MFA verify endpoints (setup-activation + login-
                                                  -- exchange), mirrors otp_challenges.attempt_count
  mfa_locked_until    TIMESTAMPTZ                -- added V7; set once mfa_failed_attempts reaches
                                                  -- MAX_MFA_ATTEMPTS (5); cleared on next correct code
);

-- Table: otp_challenges
CREATE TABLE otp_challenges (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number  VARCHAR(20) NOT NULL,
  code_hash     VARCHAR(255) NOT NULL,          -- never store raw OTP
  expires_at    TIMESTAMPTZ NOT NULL,
  consumed_at   TIMESTAMPTZ,
  attempt_count SMALLINT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table: refresh_tokens (added Sprint 2 Batch 2 — not in the original v1.0 schema;
-- required for refresh-token rotation + reuse detection per ADR-3 / security doc §3,
-- which "stateless JWT" alone can't provide)
CREATE TABLE refresh_tokens (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(id),
  family_id       UUID NOT NULL,                  -- shared across a rotation chain; reuse of a
                                                    -- revoked/replaced token revokes the whole family
  token_hash      VARCHAR(255) NOT NULL UNIQUE,    -- SHA-256 of the raw refresh token, never the raw value
  issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at      TIMESTAMPTZ NOT NULL,
  revoked_at      TIMESTAMPTZ,
  replaced_by_id  UUID REFERENCES refresh_tokens(id)
);

-- Table: groups
CREATE TABLE groups (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                VARCHAR(255) NOT NULL,
  organizer_id        UUID NOT NULL REFERENCES users(id),
  contribution_amount NUMERIC(12,2) NOT NULL CHECK (contribution_amount > 0),
  currency            VARCHAR(3) NOT NULL DEFAULT 'MAD',
  frequency           VARCHAR(20) NOT NULL CHECK (frequency IN ('WEEKLY', 'MONTHLY')),
  total_cycles        SMALLINT NOT NULL CHECK (total_cycles > 0),
  payout_order_mode   VARCHAR(20) NOT NULL CHECK (payout_order_mode IN ('MANUAL', 'RANDOMIZED')),
  status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'FINALIZED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table: group_memberships
CREATE TABLE group_memberships (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id      UUID NOT NULL REFERENCES groups(id),
  user_id       UUID NOT NULL REFERENCES users(id),
  role_in_group VARCHAR(20) NOT NULL DEFAULT 'MEMBER' CHECK (role_in_group IN ('MEMBER', 'ORGANIZER')),
  payout_position SMALLINT,                     -- assigned once group is finalized
  joined_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (group_id, user_id)
);

-- Table: contribution_schedules
CREATE TABLE contribution_schedules (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id      UUID NOT NULL REFERENCES groups(id),
  cycle_number  SMALLINT NOT NULL,
  user_id       UUID NOT NULL REFERENCES users(id),
  due_date      DATE NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'MARKED_PAID', 'CONFIRMED', 'LATE', 'DISPUTED')),
  was_late      BOOLEAN NOT NULL DEFAULT FALSE,  -- added V8 (Sprint 5 Batch 3, story 7.1): set once by
                                                  -- LateContributionScheduler, never cleared — `status`
                                                  -- alone can't answer "was this ever late" once it
                                                  -- reaches MARKED_PAID/CONFIRMED; needed for savings
                                                  -- history's on_time_rate below.
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (group_id, cycle_number, user_id)
);

-- Table: payout_schedules
CREATE TABLE payout_schedules (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id      UUID NOT NULL REFERENCES groups(id),
  cycle_number  SMALLINT NOT NULL,
  recipient_id  UUID NOT NULL REFERENCES users(id),
  scheduled_date DATE NOT NULL,
  amount        NUMERIC(12,2) NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'FAILED', 'MANUAL_OVERRIDE')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (group_id, cycle_number)
);

-- Table: ledger_entries (APPEND-ONLY — see ADR-2 in architecture doc)
CREATE TABLE ledger_entries (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id                UUID NOT NULL REFERENCES groups(id),
  entry_type              VARCHAR(20) NOT NULL CHECK (entry_type IN ('CONTRIBUTION', 'PAYOUT', 'ADJUSTMENT')),
  contribution_schedule_id UUID REFERENCES contribution_schedules(id),
  payout_schedule_id      UUID REFERENCES payout_schedules(id),
  actor_user_id           UUID NOT NULL REFERENCES users(id),  -- who triggered this entry
  amount                  NUMERIC(12,2) NOT NULL,
  source                  VARCHAR(20) NOT NULL CHECK (source IN ('MEMBER_REPORTED', 'ORGANIZER_CONFIRMED', 'CMI_WEBHOOK', 'ADMIN_ADJUSTMENT')),
  reversal_of_entry_id    UUID REFERENCES ledger_entries(id),  -- for offsetting corrections, never UPDATE/DELETE
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
  -- No updated_at — this table is append-only. See §7 for DB-level enforcement.
);

-- Table: disputes
CREATE TABLE disputes (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id          UUID NOT NULL REFERENCES groups(id),
  ledger_entry_id   UUID NOT NULL REFERENCES ledger_entries(id),
  raised_by_user_id UUID NOT NULL REFERENCES users(id),
  reason            TEXT NOT NULL,
  evidence_note     TEXT,
  status            VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED')),
  resolved_by_user_id UUID REFERENCES users(id),
  resolution_reason TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved_at       TIMESTAMPTZ
);

-- Table: savings_history_snapshots (derived, feeds Kasb export — Phase 2)
-- Implemented V8 (Sprint 5 Batch 3, story 7.1): one row appended per member
-- each time a group cycle fully completes (all that cycle's contributions
-- CONFIRMED) — see decisions.md 2026-07-31 for why "cycle completion" is a
-- proxy signal here rather than real payout execution (Epic 4 not built yet).
CREATE TABLE savings_history_snapshots (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL REFERENCES users(id),
  group_id          UUID NOT NULL REFERENCES groups(id),
  cycles_completed  SMALLINT NOT NULL,
  on_time_rate      NUMERIC(5,2) NOT NULL,   -- percentage
  disputes_involved SMALLINT NOT NULL DEFAULT 0,
  computed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## 4. Index Strategy
| Table | Index Name | Columns | Query Pattern |
|---|---|---|---|
| group_memberships | idx_group_memberships_user | user_id | "list groups I belong to" |
| group_memberships | idx_group_memberships_group | group_id | "list members of a group" |
| contribution_schedules | idx_contrib_group_cycle | (group_id, cycle_number) | Payout/contribution schedule views |
| contribution_schedules | idx_contrib_user_status | (user_id, status) | "my pending contributions" |
| ledger_entries | idx_ledger_group_created | (group_id, created_at) | Ledger view, paginated/ordered |
| ledger_entries | idx_ledger_contribution | contribution_schedule_id | Deriving current contribution state |
| ledger_entries | idx_ledger_payout | payout_schedule_id | Deriving current payout state |
| disputes | idx_disputes_group_status | (group_id, status) | "open disputes for this group" |
| otp_challenges | idx_otp_phone_expiry | (phone_number, expires_at) | OTP verification lookup |
| refresh_tokens | idx_refresh_family | family_id | Reuse detection: revoke whole family |
| refresh_tokens | idx_refresh_user | user_id | Logout-all / list active sessions |

No index on `users.role` or other low-cardinality booleans/enums with small tables (YAGNI — table sizes at MVP scale don't justify it).

## 5. Migration Plan
| Migration File | Description | Reversible |
|---|---|---|
| 001_create_users_and_auth.sql | users, otp_challenges | Yes |
| 001a_create_refresh_tokens.sql | refresh_tokens (added Sprint 2 Batch 2) | Yes |
| 002_create_groups_and_memberships.sql | groups, group_memberships | Yes |
| 003_create_schedules.sql | contribution_schedules, payout_schedules | Yes |
| 004_create_ledger.sql | ledger_entries + REVOKE UPDATE/DELETE grant (see §7) | Yes (down migration re-grants, but should never be run against real data) |
| 005_create_disputes.sql | disputes | Yes |
| 006_create_savings_history.sql | savings_history_snapshots | Yes |

Per DBA convention: add columns nullable first → backfill → add NOT NULL constraint in a follow-up migration, if schema evolves after initial data exists.

## 6. Access Patterns
| Use Case | Query Pattern | Index Coverage |
|---|---|---|
| Member views their groups | SELECT groups JOIN group_memberships WHERE user_id = ? | idx_group_memberships_user |
| Member views group ledger | SELECT ledger_entries WHERE group_id = ? ORDER BY created_at | idx_ledger_group_created |
| Organizer checks late contributions | SELECT contribution_schedules WHERE group_id = ? AND status = 'PENDING' AND due_date < NOW() | idx_contrib_group_cycle |
| Admin views platform dispute rate | SELECT COUNT(*) FROM disputes GROUP BY status | idx_disputes_group_status (partial coverage; full scan acceptable at MVP row counts) |
| Payout cron job | SELECT payout_schedules WHERE status = 'PENDING' AND scheduled_date <= CURRENT_DATE | Consider a partial index `WHERE status = 'PENDING'` if this table grows large |

## 7. Sensitive Data
- **Columns requiring encryption**: `users.phone_number`, `users.national_id` (encrypt at rest via pgcrypto or application-layer encryption — see security doc §5)
- **Row-level security**: 🔶 Not implemented at MVP — authorization is enforced at the Spring Boot service layer (see architecture doc). **Recommend revisiting Postgres RLS** as a defense-in-depth layer once the schema stabilizes, specifically for `ledger_entries` and `group_memberships`, so a bug in application-layer authorization isn't the *only* thing preventing cross-group data leaks.
- **Append-only enforcement**: `ledger_entries` should have `UPDATE` and `DELETE` privileges revoked for the application's DB role, e.g.:
  ```sql
  REVOKE UPDATE, DELETE ON ledger_entries FROM tawfir_app_role;
  ```
  This makes the append-only guarantee a database-enforced fact, not just an application convention that a future bug could violate.

### Database Validation Checklist
- [x] Engine choice justified (YAGNI — PostgreSQL, no premature polyglot persistence)
- [x] All PRD entities modeled with relationships
- [x] Schema in 3NF (no denormalization without a measured bottleneck)
- [x] Indexes on foreign keys + frequent WHERE columns only
- [x] Migration files include rollback
- [x] Sensitive columns identified for encryption
- [ ] Row-level security — flagged as a recommended follow-up, not blocking MVP
