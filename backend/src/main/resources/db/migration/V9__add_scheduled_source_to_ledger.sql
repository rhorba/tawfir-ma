-- Story 4.1: cron-auto-executed payouts need a ledger source distinct from a
-- human organizer action (ORGANIZER_CONFIRMED) or a CMI callback (CMI_WEBHOOK).
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_source_check;
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_source_check
    CHECK (source IN ('MEMBER_REPORTED', 'ORGANIZER_CONFIRMED', 'CMI_WEBHOOK', 'ADMIN_ADJUSTMENT', 'SYSTEM_SCHEDULED'));
