# RISKS — Tawfir.ma



### [2026-07-21] REGULATORY — Payment custody model undecided
- **Specialist**: Security Engineer
- **Summary**: Whether Tawfir holds/moves member funds (custodial) vs only records external transfers (non-custodial) determines Bank Al-Maghrib payment-institution licensing exposure. Docs assume non-custodial as MVP default; not yet confirmed by user/legal counsel.
- **Probability**: Medium (depends on product decision, not yet made)
- **Mitigation**: Confirm with Moroccan legal counsel before Sprint 2 execution; non-custodial default keeps regulatory exposure low in the meantime.
- **Status**: closed — 2026-08-01, user confirmed non-custodial model as final (SDR-3 updated in system-design-tawfir.md, security-tawfir.md §6.1). No code changes needed; Sprint 4's webhook/payout implementation already matches this model. Residual item: BAM notification/registration duty for non-custodial record-keeping itself is still worth a legal check before public launch — tracked as a recommendation in security-tawfir.md §6.1, not a blocking risk.
- **Impact**: high
---
