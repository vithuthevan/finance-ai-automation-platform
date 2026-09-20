# Pre-pilot — do not build yet

**Purpose:** Prevent distraction before Customer #1 pays and validates.  
**Classification key:** BUILD NOW | VALIDATE FIRST | PILOT MANUALLY | DEFER

| Item | Decision | Rationale |
|------|----------|-----------|
| Full double-entry GL | **DEFER** | Product positioning is document-to-close, not Xero clone |
| Payroll | **DEFER** | Out of scope; bad pilot fit |
| Tax filing / VAT engine | **DEFER** | Legal/regulatory scope explosion |
| Open Banking feeds | **DEFER** | CSV acceptable for pilot |
| Native mobile apps | **DEFER** | Responsive web sufficient |
| WhatsApp integration | **VALIDATE FIRST** | Chase reduction unknown vs email |
| Xero integration | **VALIDATE FIRST** | Biggest commercial objection; OPTION B/C first |
| QBO / Sage integration | **DEFER** | Same as Xero after validation |
| Email document intake | **DEFER** | Upload + requests work |
| Recurring request packs | **VALIDATE FIRST** | Manual monthly requests OK for pilot |
| White-label | **DEFER** | Single brand pilot |
| Generic workflow builder | **DEFER** | Close engine exists |
| Enterprise SSO | **DEFER** | Not required for 2–10 person firms |
| SOC2 programme | **DEFER** | Post-pilot scale |
| Multi-country tax | **DEFER** | LKR/single currency pilot |
| PDF reports | **VALIDATE FIRST** | CSV/XLSX exists; ask if partner requires PDF |
| Zero-login owner upload | **VALIDATE FIRST** | Reduces friction; security review needed |
| AI extraction (default on) | **PILOT MANUALLY** | Turn off per firm; optional later |
| Portfolio close matrix polish | **DEFER** | Already exists; optimize after usage |
| Staff workload BI | **DEFER** | Dashboard + work queue sufficient |
| Stripe / billing | **DEFER** | Manual billing for 1–3 firms |
| Malware scanning | **DEFER** | MIME/size checks exist; enterprise AV later |
| Kubernetes / multi-region | **DEFER** | Single VPS + Compose prod acceptable |
| PostgreSQL RLS | **DEFER** | App-layer + tests; backstop later |
| Fancy onboarding wizard | **PILOT MANUALLY** | Founder Zoom + runbook |
| Default category packs | **BUILD NOW** (small) | P1 activation — see build plan |
| Users UI client access | **BUILD NOW** (small) | P1 — owners blocked without it |
| Client CSV import | **DEFER** | 5 clients manual OK |
| Automated restore in product UI | **DEFER** | Ops scripts sufficient |

---

## Explicitly NOT bugs (missing by design)

- Balance sheet, trial balance, journals
- AR/AP modules
- Xero sync

Do not file as P0 defects.

---

## Demo vs pilot optimization

See readiness report sections **DEMO VALUE** vs **PRODUCTION/PILOT VALUE**.
