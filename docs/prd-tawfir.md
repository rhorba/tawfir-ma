# PRD: Tawfir.ma — Digitized Rotating Savings (Daret/ROSCA)
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: PM | **Status**: Draft — pending user approval

> ⚠️ Assumptions flagged with 🔶 are placeholders (no real data provided yet). Confirm or correct during review — nothing here blocks Batch 2 from starting, but numbers should be tightened before Stories are cut.

## 1. Problem Statement
Morocco's traditional "daret" rotating savings groups (ROSCA — Rotating Savings and Credit Association) serve an estimated ~4M participants, operating entirely on paper, spreadsheets, or trust-based memory. There is no digital infrastructure for group creation, contribution tracking, or payout rotation. Defaults and disputes are common and largely unresolvable once they happen — there's no record to arbitrate from. Members who reliably contribute for years have no portable proof of that reliability; it doesn't count toward anything outside the group.

## 2. Goals & Success Metrics
| Goal | Metric | Target 🔶 | Baseline |
|---|---|---|---|
| Digitize group lifecycle | % of a group's contribution cycles completed fully in-app (no off-app tracking) | 90% by month 6 post-launch | 0% (paper-based) |
| Reduce disputes | Disputes opened per 100 active groups/month | < 5 | Unknown (no baseline exists — first release must instrument this) |
| Build savings history | Members with ≥ 3 completed cycles recorded | 10,000 by month 12 | 0 |
| Drive Kasb feed | Members opted into credit-score data sharing | 40% of active members | 0 |
| Payout reliability | % of payouts executed on scheduled date | ≥ 98% | N/A |

🔶 Targets above are placeholders — replace with real numbers once you have a launch cohort size and business plan figures.

## 3. User Stories
As a **Group Organizer**, I want to create a daret group with a defined contribution amount, frequency, and member list, so that the group has one shared source of truth instead of a notebook.

As a **Group Organizer**, I want to define (or let the app randomize) the payout order, so that rotation is fair and members can't dispute the sequence.

As a **Member**, I want to see my contribution schedule and payment history, so that I know what I owe and when I'm due to receive the payout.

As a **Member**, I want to confirm a contribution was received (or flag that it wasn't), so that disagreements have a record instead of relying on memory.

As a **Member**, I want to raise a dispute with evidence (e.g. "I paid but it's not showing"), so that an Organizer or Admin can resolve it without the group falling apart.

As a **Group Organizer**, I want to see the full group ledger (who paid, who's late, who's received payouts), so that I can manage the group without a separate spreadsheet.

As a **Member**, I want my completed cycles to count toward a savings history, so that it can feed into a Kasb credit score later.

As an **Admin**, I want to view platform-wide group health (active groups, default rates, disputes), so that I can intervene before a group collapses.

As a **Member**, I want to authenticate with just my phone number (OTP), so that I don't need to remember another password.

## 4. Scope
### In Scope (MVP — Sprint 1 documentation covers this; later phases below)
- Group creation & configuration (amount, frequency, duration, member roster, payout order — manual or randomized)
- Phone-number OTP authentication
- Contribution tracking (mark paid / confirm received)
- Automatic payout rotation scheduling and status tracking
- Dispute flagging + Organizer/Admin resolution workflow
- Member savings history (internal record, exposed via API for future Kasb integration)
- Admin oversight dashboard (Angular admin app)
- Member-facing app (React) for group participation
- CMI mobile money integration for contribution/payout money movement 🔶 *(confirm: does the platform actually hold/move funds, or only record that an off-platform transfer happened? This materially changes the regulatory posture — see security/compliance doc.)*

### Out of Scope (MVP)
- Kasb credit-score computation itself (Tawfir only exposes the data feed)
- In-app chat/messaging between members
- Multi-currency support (MAD only)
- Group discovery / public marketplace of groups (groups are invite-only)
- Automated fraud/AML scoring beyond basic KYC capture
- Native mobile apps (web-responsive React only for MVP)

### Phase Roadmap 🔶
| Phase | Focus | Target |
|---|---|---|
| Phase 1 (this doc chain) | Core daret digitization: groups, contributions, payouts, disputes | MVP |
| Phase 2 | Kasb credit-score integration (real-time data feed, scoring hooks) | Post-MVP |
| Phase 3 | Native mobile apps, in-app messaging, group discovery | Growth |
| Phase 4 | Multi-tenant B2B (banks/MFIs white-labeling Tawfir for their savings-circle members) | Scale |

## 5. Requirements

### Functional
- FR-1: Organizer can create a group with: name, contribution amount, currency (MAD), frequency (weekly/monthly), number of cycles, member list, payout order (manual or system-randomized)
- FR-2: System computes and displays the full payout schedule once a group is finalized
- FR-3: Members authenticate via phone number + OTP (provider TBD — see security doc)
- FR-4: Member can mark a contribution as paid; Organizer/system can confirm receipt (via CMI webhook or manual confirmation)
- FR-5: System auto-flags late/missed contributions and notifies Organizer + member
- FR-6: Member can open a dispute against a specific contribution/payout event, attach a note, and the Organizer or Admin can resolve it (accept/reject) with a reason logged
- FR-7: System maintains an immutable ledger per group (append-only contribution/payout/dispute events)
- FR-8: Member savings history is queryable (internally, and via a defined API contract for Kasb — see architecture doc §5)
- FR-9: Admin can view all groups, drill into any group's ledger, and see platform-wide default/dispute rates
- FR-10: Payout execution is automatic on schedule if funds are confirmed collected; otherwise flagged for manual Organizer intervention

### Non-Functional
- NFR-1: Performance — API p99 < 300ms for read endpoints under expected MVP load (see system design doc for load assumptions)
- NFR-2: Security — phone-number OTP auth, JWT session tokens, all financial-adjacent endpoints require authorization checks scoped to group membership (see security doc)
- NFR-3: Accessibility — WCAG 2.1 AA for both React member app and Angular admin app; must support Arabic (RTL) and French at minimum 🔶 *(confirm: is Darija/Arabic UI required for MVP, or French/English only?)*
- NFR-4: Data integrity — contribution/payout ledger entries are append-only; corrections happen via new offsetting entries, never edits/deletes
- NFR-5: Auditability — every dispute resolution and manual override must be attributable to a specific admin/organizer user and timestamped

## 6. Constraints & Assumptions
- Constraint: Morocco-specific payment rail (CMI) — no other payment processor assumed for MVP
- Constraint: Regulatory exposure depends heavily on whether Tawfir *touches* money (custodial) vs only *records* transfers members make peer-to-peer or via CMI directly (non-custodial). This is a legal question, not just technical — flagged again in the security doc's compliance section. **Recommend confirming with Moroccan legal counsel before committing to the custodial model.**
- Assumption: Groups are private/invite-only; no public discovery in MVP
- Assumption: MAD-only, no FX
- 🔶 Assumption: Initial launch targets urban Morocco (Casablanca/Rabat) with smartphone + mobile data access — confirm if that matches your actual go-to-market.

## 7. Risks
| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Platform classified as a Payment Institution under Moroccan law, requiring Bank Al-Maghrib licensing | Med | High | Legal review before enabling custodial fund flows; consider non-custodial "record-only" MVP first (members pay each other directly via CMI/bank transfer, app just tracks confirmation) |
| Organizer collusion or fraud (fake payout confirmations) | Med | High | Immutable ledger, dispute workflow, Admin audit visibility, multi-party confirmation for payout release |
| Low digital literacy among target users slows adoption | Med | Med | UX simplicity as a first-class requirement (see UX doc), phone-OTP instead of passwords |
| CMI integration delays (merchant onboarding, sandbox access) | Med | Med | Design contribution-confirmation to work with manual "I paid" / "I received" flow as a fallback if CMI isn't ready at launch |
| Dispute volume overwhelms manual resolution | Low | Med | Structured dispute categories + evidence requirements to speed triage |

## 8. Timeline 🔶
| Milestone | Target Date |
|---|---|
| PRD Approved | 2026-07-21 |
| Foundation docs (this doc chain) complete & pushed | 2026-07-21 |
| Architecture/Stories → Sprint 2 execution start | 🔶 TBD — pick a date |
| MVP Ready | 🔶 TBD — suggest 8-12 weeks from execution start given 2-frontend + Spring backend scope |

### PRD Validation Checklist
- [x] Problem clearly stated (not a solution disguised as a problem)
- [ ] Success metrics are measurable — 🔶 placeholder targets need real numbers
- [x] Scope has explicit "out of scope" items
- [x] User stories follow As a/I want/So that format
- [x] Requirements are testable (can write acceptance criteria)
- [x] Risks identified with mitigations — **top risk (regulatory classification) needs a decision before Sprint 2**
