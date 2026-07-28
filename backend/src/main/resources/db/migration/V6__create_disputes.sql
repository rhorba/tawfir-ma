CREATE TABLE disputes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID NOT NULL REFERENCES groups(id),
    ledger_entry_id     UUID NOT NULL REFERENCES ledger_entries(id),
    raised_by_user_id   UUID NOT NULL REFERENCES users(id),
    reason              TEXT NOT NULL,
    evidence_note       TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED')),
    resolved_by_user_id UUID REFERENCES users(id),
    resolution_reason   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_disputes_group_status ON disputes (group_id, status);
