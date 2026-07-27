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

CREATE TABLE group_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES groups(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    role_in_group   VARCHAR(20) NOT NULL DEFAULT 'MEMBER' CHECK (role_in_group IN ('MEMBER', 'ORGANIZER')),
    payout_position SMALLINT,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_memberships_user ON group_memberships (user_id);
CREATE INDEX idx_group_memberships_group ON group_memberships (group_id);
