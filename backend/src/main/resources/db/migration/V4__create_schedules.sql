CREATE TABLE contribution_schedules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id     UUID NOT NULL REFERENCES groups(id),
    cycle_number SMALLINT NOT NULL,
    user_id      UUID NOT NULL REFERENCES users(id),
    due_date     DATE NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'MARKED_PAID', 'CONFIRMED', 'LATE', 'DISPUTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, cycle_number, user_id)
);

CREATE TABLE payout_schedules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID NOT NULL REFERENCES groups(id),
    cycle_number   SMALLINT NOT NULL,
    recipient_id   UUID NOT NULL REFERENCES users(id),
    scheduled_date DATE NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'FAILED', 'MANUAL_OVERRIDE')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, cycle_number)
);

CREATE INDEX idx_contrib_group_cycle ON contribution_schedules (group_id, cycle_number);
CREATE INDEX idx_contrib_user_status ON contribution_schedules (user_id, status);
