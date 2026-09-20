# Demo conversion gap matrix

**Date:** 2026-09-20  
**Purpose:** Gaps between *current* experience and *accounting-firm demo conversion* expectations.  
**Severity:** P0 demo breaker · P1 conversion friction · P2 polish · P3 validate · P4 do not build now

| AREA | CURRENT EXPERIENCE | EXPECTED EXPERIENCE | BUSINESS IMPACT | DEMO IMPACT | SEVERITY | EFFORT | CODE/UX/DATA/CONFIG | RECOMMENDED ACTION | DEMO OR PILOT | VALIDATION REQUIRED | EVIDENCE |
|------|-------------------|---------------------|-----------------|-------------|----------|--------|---------------------|-------------------|-----------------|---------------------|----------|
| A. Portfolio / month-end hero | Login → Dashboard; portfolio on **My work** with Ready/Blocked only | September banner: N clients, ready/blocked/attention + per-client blocker bullets | Partner cannot grasp firm-wide close in 60s | HIGH | P1 | M | UX (+ small API DTO optional) | Default post-login to close summary or embed firm readiness on Dashboard; expose top blockers per client | DEMO | No | `app.routes.ts`, `work.page.ts`, `PracticeWorkQueueService.portfolio()` |
| B. Default categories | None on register | Starter pack for café/SME | Pilot onboarding delay | MEDIUM (from-scratch only) | P1 | S | DATA/CONFIG | Seed template on firm create or “Apply demo categories” button | PILOT | Optional | `RegistrationService`, `FROM_SCRATCH` doc |
| C. New-firm empty dashboard | Zeros, no CTA to create client + categories | Guided “Add first client → categories → invite owner” | Trial abandonment | HIGH if showing registration | P1 | S | UX | Empty state CTAs + checklist card | DEMO (if showing signup) | No | `dashboard.page.ts` |
| D. Client creation | Works in UI | Same | — | LOW | P2 | XS | — | — | — | No | Clients page |
| E. Staff creation | Works | Same | — | LOW | — | — | — | — | — | No | `users.page.ts` |
| F. Business-owner creation | Works; password at create | Invite link optional | Security perception | MEDIUM | P2 | M | UX/BACKEND | Defer; founder sets password in pilot | PILOT | Yes | Users API |
| G. User → client access UI | **Missing** — API `PUT .../client-access` only | Assign clients in Users | Owner portal empty — looks broken | CRITICAL (from-scratch demo) | P0/P1 | S | UX | Multi-select clients on user create/edit | PILOT | No | `users.page.ts` grep no clientIds |
| H. Document upload/review | Strong | Same | Core wedge | — | — | — | — | Keep in demo | DEMO | No | e2e rehearsal |
| I. Draft → approval | Works | Same | Commodity but needed | MEDIUM | P2 | — | — | Minimize clicks in script | DEMO | No | Expenses page |
| J. Bank account creation | Works (seeded) | Same | — | LOW | — | — | — | — | DEMO | No | seed_demo |
| K. CSV bank import | Works | Same | CSV-only honest | LOW | — | — | — | Say CSV not live feeds | DEMO | No | Banking page |
| L. Reconciliation | Works + blocks close | Same | Differentiator vs folders | HIGH | — | — | — | Show in demo | DEMO | No | Close checks |
| M. Document requests | Works; blocks close | Same | Chase reduction story | HIGH | — | — | — | Core demo | DEMO | No | OpenDocumentRequestCheck |
| N. Email notifications | Needs SMTP | Owner notified | Chase reliability | LOW in live demo | P2 | CONFIG | EXTERNAL | Use in-app for demo | PILOT | No | Email provider |
| O. Business-owner portal | Good request UX | Same | Adoption | MEDIUM | P1 | S | UX | Hide ledger nav for owner-only firms | DEMO | Yes | `shell.component.ts` |
| P. Owner upload | Works | Same | WOW when tied to readiness | HIGH | — | — | — | Demo after accountant view | DEMO | No | owner.page |
| Q. Request completion | **Mark complete** required | Obvious CTA | Close blocked silently | HIGH | P1 | XS | UX | Toast + highlight on period page | DEMO | No | period-detail |
| R. Close readiness | Strong engine + links | Same | Trust | VERY HIGH | — | — | — | Hero | DEMO | No | `CloseReadinessService` |
| S. Close confirmation | Works | Same | — | MEDIUM | — | — | — | — | DEMO | No | Period close API |
| T. Closed-period protection | Tested golden path | Same | Trust WOW | HIGH | — | — | — | Show briefly | DEMO | No | Integration tests |
| U. Reports | P&L etc. | Optional | Commodity | MEDIUM distraction | P2 | — | — | Skip in 15 min | DEMO | No | Reports routes |
| V. Audit | Works | Same | Trust optional | MEDIUM | — | — | — | 1 min at end | DEMO | No | audit.page |
| W. Navigation | Operations before Close in mental model | Close/evidence first | Positions vs Xero | HIGH | P1 | S | UX | Reorder nav groups / demo deep-link | DEMO | No | shell nav groups |
| X. Mobile owner | Not audited live | Upload from phone | Owner adoption | MEDIUM | P3 | — | VALIDATE | Smoke on phone | PILOT | Yes | — |
| Y. Error states | Toasts on many actions | Clear recovery | Trust | MEDIUM | P2 | S | UX | Audit empty 500s | DEMO | No | — |
| Z. Loading states | Present on work queue | Consistent | Polish | LOW | P2 | XS | UX | — | — | No | loading-state component |
| Multi-client demo data | 1 client seeded | 5–8 clients mixed readiness | Portfolio story | VERY HIGH | P1 | M | DATA | Extend seed (doc only this task) | DEMO | No | `seed_demo.py` |
| Portfolio row actions | Table read-only | Click → period workspace | Friction | HIGH | P1 | XS | UX | Link client name to close detail | DEMO | No | work.page.ts |
| Staff workload | API only | Partner sees accountant load | Delegation | MEDIUM | P3 | S | UX | Admin widget | PILOT | Yes | `WorkController` staff-workload |
| Blocker detail on portfolio | Backend has findings; portfolio DTO does not | “2 docs, 3 bank lines” on row | Partner scan | HIGH | P1 | S | BACKEND+UX | Add `topBlockers[]` to portfolio response | DEMO | No | readiness evaluate in portfolio |
| Xero / QBO | None | Coexistence story | Commercial | HIGH objection | P4 build | — | VALIDATE | OPTION B narrative + scope doc | DEMO | **Yes** | PILOT_LEDGER doc |
| Hosted demo URL | localhost | Stable HTTPS staging | Professionalism | HIGH | P1 | CONFIG | INFRA | Staging deploy for prospects | DEMO | No | — |
| AI default on new firms | aiEnabled true | Off for firms | Trust / surprise | MEDIUM | P2 | XS | CONFIG | Default off or register toggle | PILOT | No | FIRM_SETTINGS |

---

## Quick wins (XS/S + HIGH/VERY HIGH demo impact)

1. **Demo script opens `/app/close`** instead of dashboard (CONFIG/process — zero code).  
2. **Portfolio table row links** to period workspace (XS UX).  
3. **Users client-access multi-select** (S UX) — fixes P0 from-scratch story.  
4. **Dashboard empty state CTAs** (S UX).  
5. **Owner nav trim** (S UX) — requests-first.  
6. **Multi-client seed** (M DATA) — highest narrative impact.

---

## Classification legend (gap types)

| Label | Meaning |
|-------|---------|
| UI ONLY | Backend already computes; surface in Angular |
| BACKEND DATA EXISTS | Query present; DTO or endpoint aggregation missing |
| BACKEND CHANGE REQUIRED | New fields or endpoints |
| NEW DOMAIN LOGIC | New rules (not needed for most rows above) |
| CUSTOMER VALIDATION REQUIRED | Build only after interviews |
