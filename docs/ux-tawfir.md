# UX Foundation: Tawfir.ma
**PRD Reference**: docs/prd-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: UX Designer

## 1. User Personas

| Persona | Role | Goal | Pain Point |
|---|---|---|---|
| Amina | Group Organizer, runs a monthly daret with 10 coworkers | Keep the group running smoothly without being the sole point of trust/blame | Currently tracks everything in a notebook/WhatsApp; disputes take hours to untangle from memory |
| Youssef | Member, moderate smartphone literacy | Know exactly what he owes, when, and when he's due to receive the payout | Doesn't fully trust the Organizer's memory-based tracking; has no proof of his payment history |
| (Edge case) Fatima | Admin, oversees platform health | Spot at-risk groups (rising defaults/disputes) before they collapse | No visibility into any group unless a complaint escalates to her manually |

Two personas + one edge case, per YAGNI — no need for a full persona set at MVP.

## 2. Information Architecture

### React Member App
```
[App Root]
├── Auth
│   ├── Phone entry
│   └── OTP verification
├── My Groups (home)
│   ├── Group list (active / completed)
│   └── Group detail
│       ├── Schedule (contributions + payouts)
│       ├── Ledger (full history)
│       ├── Disputes
│       └── Members roster
├── Create Group (Organizer flow)
├── Savings History (personal, feeds Kasb later)
└── Profile / Settings
```

### Angular Admin App
```
[App Root]
├── Auth (Admin login — OTP + TOTP, see security doc)
├── Dashboard (platform metrics: active groups, default rate, open disputes)
├── Groups
│   ├── All groups (filterable by status/health)
│   └── Group detail (read-only ledger + intervention actions)
└── Disputes (platform-wide queue)
```

Both apps stay ≤3 levels deep, matching navigation rules (≤7 primary nav items).

## 3. Core User Flows

### Flow 1: Create & finalize a group (Organizer)
```
(Organizer taps "Create Group") → [Enter name, amount, frequency, cycle count]
  → [Add members by phone number] → [Choose payout order: manual or randomize]
  → <All members confirmed?>
      → Yes → [Finalize group] → [Schedule generated] → (Success: group is ACTIVE)
      → No  → [Show pending invites] → [Organizer can nudge or wait]

Error Paths:
[Add members] → <Phone number not registered?> → [Send invite link via SMS] → [Member registers] → back to flow
[Finalize] → <Fewer than 2 members?> → [Error: need at least 2 members] → [Return to member list]
```

### Flow 2: Contribution cycle (Member)
```
(Member opens group) → [Sees "Contribution due [date]"] → [Pays via CMI or agreed method outside app]
  → [Member taps "Mark as paid"] → <Organizer/CMI confirms?>
      → Yes → [Status: Confirmed] → (Ledger updated, visible to all members)
      → No (timeout) → [Status stays "Marked paid, unconfirmed"] → [Member can open a dispute if ignored too long]

Edge Cases:
- Member misses due date → [Status: Late] → [Organizer + Member notified] → [Member can still mark paid late]
- Member is offline when due date passes → [Reconciled on next app open, no penalty for being offline]
```

### Flow 3: Dispute a contribution/payout (Member → Organizer/Admin)
```
(Member views a ledger entry they disagree with) → [Tap "Dispute"] → [Select reason] → [Add note/evidence]
  → [Submit] → (Status: Dispute OPEN, Organizer notified)
  → Organizer reviews → <Accept or Reject?>
      → Accept → [Ledger gets an offsetting ADJUSTMENT entry] → (Dispute closed: Accepted)
      → Reject → [Reason logged] → (Dispute closed: Rejected) → [Member can escalate to Admin]

Error Paths:
[Organizer takes no action > SLA window 🔶] → [Auto-escalate to Admin queue]
```

### Flow 4: Admin oversight (Admin, Angular app)
```
(Admin opens Dashboard) → [Sees active groups, default rate, open disputes count]
  → [Drills into a flagged group] → [Views full ledger read-only] → <Needs to intervene?>
      → Yes → [Manual adjustment with mandatory reason field] → (Logged, attributed to Admin)
      → No  → [Return to dashboard]
```

## 4. Key Screen Wireframes (text-based)

### Screen: Group Detail (React, Member)
```
┌─────────────────────────────────────┐
│ ← Group: "Daret Bureau" · ACTIVE     │
├─────────────────────────────────────┤
│ Next contribution due: 2026-08-01    │
│ [ Mark as Paid ]                     │
│                                       │
│ Payout order: You're #4 of 10        │
│ Estimated payout date: 2026-11-01    │
├─────────────────────────────────────┤
│ Tabs: Schedule | Ledger | Disputes   │
└─────────────────────────────────────┘
```

### Screen: Admin Dashboard (Angular)
```
┌───────────────────────────────────────────┐
│ Tawfir Admin                               │
├───────────────────────────────────────────┤
│ Active Groups: 128   Default Rate: 3.2%    │
│ Open Disputes: 7      At-Risk Groups: 4    │
├───────────────────────────────────────────┤
│ [Table: Group | Status | Members | Health] │
└───────────────────────────────────────────┘
```

## 5. Screen States
| Screen | Empty State | Loading | Error | Success |
|---|---|---|---|---|
| My Groups (Member) | "No groups yet — create or join one" + CTA | Skeleton list | "Couldn't load groups — retry" | Group cards with next-action highlighted |
| Group Ledger | "No activity yet" (new group) | Skeleton rows | "Ledger failed to load — retry" | Chronological entries, newest first |
| Create Group form | N/A (form) | Submit button spinner | Inline field errors (e.g. "add at least 2 members") | Redirect to group detail on finalize |
| Admin Dashboard | N/A (always has platform data) | Skeleton metric cards | "Metrics unavailable — retry" | Live counts |
| Dispute detail | N/A | Skeleton | "Couldn't load dispute" | Status badge (Open/Accepted/Rejected) + timeline |

### UX Validation Checklist
- [x] Personas match PRD target users (2 + 1 edge case — no unnecessary depth)
- [x] All PRD user stories map to a flow (create group, contribute, dispute, admin oversight)
- [x] Wireframes cover happy path + at least one error state
- [x] Navigation hierarchy is clear and shallow (≤3 levels both apps)
