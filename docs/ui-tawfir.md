# UI Foundation: Tawfir.ma
**UX Reference**: docs/ux-tawfir.md
**Version**: 1.0 | **Date**: 2026-07-21 | **Author**: UI Designer

## 1. Design Approach
- **Strategy**: Shared design tokens (plain CSS custom properties / a JSON token source), consumed independently by each frontend — Tailwind CSS in the React member app, Angular Material (themed to match tokens) or plain SCSS variables in the Angular admin app.
- **Rationale**: React and Angular don't share a component library out of the box, but they *can* share a token layer (colors, spacing, type scale) so the two apps feel like one product despite being built on different frameworks. Building one shared custom component library for both frameworks would be over-engineering for an MVP with two small apps (YAGNI) — token-sharing gets 80% of the visual consistency for a fraction of the effort.
- **Practical setup**: A single `design-tokens.json` (or CSS file) lives in a shared location (e.g. a small internal package or just a documented reference both frontend teams copy from). Regenerate/sync manually until there's enough drift pain to justify a shared npm package.

## 2. Design Tokens

```css
/* Colors */
--color-primary:      #0F7B5C;  /* deep green — trust, savings, Morocco flag green association */
--color-primary-dark: #0B5F46;  /* hover states */
--color-secondary:    #C89B3C;  /* warm gold accent — used sparingly for payout/reward moments */
--color-background:   #FFFFFF;  /* light */  /* dark: #0F0F0F */
--color-surface:      #F5F5F5;  /* light */  /* dark: #1A1A1A */
--color-text:         #111111;  /* light */  /* dark: #F0F0F0 */
--color-text-muted:   #666666;  /* light */  /* dark: #999999 */
--color-border:       #E0E0E0;  /* light */  /* dark: #333333 */
--color-success:      #16A34A;  /* confirmed contribution/payout */
--color-warning:      #F59E0B;  /* late contribution */
--color-error:        #DC2626;  /* dispute open, failed payout */
--color-info:         #2563EB;  /* informational banners */

/* Typography */
--font-family: 'Inter', system-ui, sans-serif;
--font-family-mono: 'JetBrains Mono', monospace;   /* for ledger/amount alignment */

--font-size-xs:  0.75rem;   /* 12px — captions, timestamps */
--font-size-sm:  0.875rem;  /* 14px — secondary text */
--font-size-md:  1rem;      /* 16px — body (default) */
--font-size-lg:  1.25rem;   /* 20px — subheadings */
--font-size-xl:  1.5rem;    /* 24px — section headings */
--font-size-2xl: 1.875rem;  /* 30px — page headings */

--font-weight-regular: 400;
--font-weight-medium: 500;
--font-weight-semibold: 600;
--line-height-body: 1.5;
--line-height-heading: 1.2;

/* Spacing (4px base grid) */
--spacing-1: 4px;   --spacing-2: 8px;   --spacing-3: 12px;
--spacing-4: 16px;  --spacing-6: 24px;  --spacing-8: 32px;
--spacing-12: 48px; --spacing-16: 64px;

/* Border radius */
--radius-sm: 4px;   /* buttons, badges */
--radius-md: 8px;   /* cards, modals */
--radius-lg: 12px;  /* larger containers */
--radius-full: 9999px; /* pills, avatars */
```

**Rules**: Max 2 brand colors (`primary` green, `secondary` gold) + semantic colors. All text passes WCAG AA (4.5:1). Never use color alone to convey contribution/payout status — always pair with an icon or label (colorblind-safe, per accessibility baseline below).

## 3. Component Inventory
| Component | React (member app) | Angular (admin app) | Notes |
|---|---|---|---|
| Button | Tailwind-based custom component | Angular Material `mat-button` themed to tokens | Primary/secondary/danger variants in both |
| Form input | Tailwind custom | Angular Material `mat-form-field` themed | Labels always visible (no placeholder-only) |
| Status badge (Pending/Confirmed/Late/Disputed) | Custom component | Custom component | Shared color mapping from tokens — this is the one component worth keeping visually identical across apps since it appears in both |
| Ledger table row | Custom component | Angular Material table themed | Monospace font for amounts |
| Card (group summary) | Custom component | Angular Material `mat-card` themed | |
| Modal/dialog | Custom (Headless UI or similar) | Angular Material `mat-dialog` | Used for dispute submission, manual adjustment |

## 4. Responsive Breakpoints
| Breakpoint | Width | Layout Notes |
|---|---|---|
| Mobile | < 768px | Single column, bottom-anchored primary CTA ("Mark as Paid"), card layout for tables (member app is mobile-first — most members will use phones) |
| Tablet | 768–1024px | Two-column where useful (e.g. schedule + ledger side by side) |
| Desktop | > 1024px | Admin app is desktop-first (Angular) — full data tables, side navigation. Member app (React) still supports desktop but optimizes for mobile first. |

## 5. Accessibility Baseline
- Color contrast: AA minimum (4.5:1 normal text, 3:1 large text) — verify both light and dark token sets
- Focus indicators: visible on all interactive elements in both apps (don't rely on framework defaults without checking — Angular Material's default focus ring and a custom Tailwind build can drift out of sync)
- Semantic HTML first; ARIA only where native semantics are insufficient
- Status badges (Pending/Confirmed/Late/Disputed) use icon + text + color, never color alone
- RTL support: 🔶 flagged in PRD NFR-3 as an open question (Arabic/Darija UI for MVP?) — if yes, both Tailwind (via `dir="rtl"` utilities) and Angular Material (built-in RTL support via `Directionality`) support it, but it must be designed for from the start, not retrofitted

### UI Validation Checklist
- [x] Design approach chosen (shared tokens, framework-native components per app — YAGNI justified given two different frontend frameworks)
- [x] Tokens cover all UX wireframe screens
- [x] Component inventory complete — status badge flagged as the one component to keep pixel-identical across apps
- [x] Responsive strategy defined per app (mobile-first member app, desktop-first admin app)
- [x] Accessibility baseline confirmed, RTL flagged as pending product decision
