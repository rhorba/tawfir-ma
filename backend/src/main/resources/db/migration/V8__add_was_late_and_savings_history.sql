-- Story 7.1 (Savings history snapshot). was_late tracks whether a contribution
-- was ever flagged LATE (LateContributionScheduler) even after it later reaches
-- CONFIRMED — the status column alone can't answer "was this ever late", since
-- MARKED_PAID/CONFIRMED overwrite it. Needed to compute on_time_rate below.
ALTER TABLE contribution_schedules
    ADD COLUMN was_late BOOLEAN NOT NULL DEFAULT FALSE;

-- Table: savings_history_snapshots (derived, feeds Kasb export — Phase 2).
-- Schema per database-tawfir.md §3 (v1.0), unchanged. One row is appended per
-- member each time a group cycle fully completes (decisions.md 2026-07-31) —
-- never updated in place, so a member's history is the row set over time.
CREATE TABLE savings_history_snapshots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    group_id          UUID NOT NULL REFERENCES groups(id),
    cycles_completed  SMALLINT NOT NULL,
    on_time_rate      NUMERIC(5,2) NOT NULL,
    disputes_involved SMALLINT NOT NULL DEFAULT 0,
    computed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_savings_history_user ON savings_history_snapshots(user_id);
