CREATE TABLE ledger_entries (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                 UUID NOT NULL REFERENCES groups(id),
    entry_type               VARCHAR(20) NOT NULL CHECK (entry_type IN ('CONTRIBUTION', 'PAYOUT', 'ADJUSTMENT')),
    contribution_schedule_id UUID REFERENCES contribution_schedules(id),
    payout_schedule_id       UUID REFERENCES payout_schedules(id),
    actor_user_id            UUID NOT NULL REFERENCES users(id),
    amount                   NUMERIC(12,2) NOT NULL,
    source                   VARCHAR(20) NOT NULL CHECK (source IN ('MEMBER_REPORTED', 'ORGANIZER_CONFIRMED', 'CMI_WEBHOOK', 'ADMIN_ADJUSTMENT')),
    reversal_of_entry_id     UUID REFERENCES ledger_entries(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_group_created ON ledger_entries (group_id, created_at);
CREATE INDEX idx_ledger_contribution ON ledger_entries (contribution_schedule_id);
CREATE INDEX idx_ledger_payout ON ledger_entries (payout_schedule_id);

-- ADR-2: ledger_entries is append-only. A REVOKE UPDATE/DELETE grant has no
-- effect here because the app connects as the same role that owns this table
-- (docker-compose defines a single `tawfir` Postgres role for migrations and
-- runtime) — table owners bypass REVOKE'd privileges in Postgres. A trigger
-- enforces this regardless of role/ownership (see decisions.md 2026-07-27).
CREATE FUNCTION reject_ledger_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_no_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER ledger_entries_no_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
