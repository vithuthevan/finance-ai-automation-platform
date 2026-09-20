# Demo objection map

**Date:** 2026-09-20  
**Rule:** No misleading claims — unsupported = PRODUCT GAP or VALIDATION REQUIRED.

| OBJECTION | WHY PROSPECT MAY ASK | CURRENT PRODUCT ANSWER | DEMO RESPONSE | EVIDENCE | PRODUCT GAP? | VALIDATION REQUIRED? |
|-----------|----------------------|------------------------|---------------|----------|--------------|----------------------|
| Why can't I just use Xero? | Xero is GL + bank feeds + reports | Platform is **evidence-to-close** with optional shadow books; **no Xero sync** | “Keep Xero as GL; use us for **chase, evidence, readiness, close gate** (OPTION B).” Show close blockers + requests. | `PILOT_LEDGER_COEXISTENCE_STRATEGY.md`, no Xero code | **PARTIAL** — positioning not product | **Yes** — will they pay for layer? |
| Why not Dext? | Receipt capture incumbent | We link evidence → **approve → recon → close**; Dext doesn’t own month-end close for firm portfolio | “Dext captures; we **orchestrate month-end** across clients and block close until evidence/recon complete.” | Document → expense flow | **PARTIAL** | Yes — overlap perception |
| We already use Excel | Low cost, flexible | Readiness engine replaces **close tracker spreadsheet** for assigned clients | Show blocker list vs manual checklist; honest: still need discipline to keep data current | Close readiness checklist | **PARTIAL** | Yes |
| We use WhatsApp | Zero friction for owners | **Document requests** + owner portal; email optional | “Portal for **structured** asks; WhatsApp OK for pilot reminders — we measure completion.” | owner.page, requests | **PARTIAL** | **Yes** — owner adoption |
| We already reconcile in Xero | Duplicate work fear | Bank recon **blocks close here** only if you use platform bank import | Pilot: **requests-only** OR single cutover client; don’t recon in both systems | BankReconciliation checks | **PRODUCT GAP** for dual recon | Yes — segment firms |
| Why should my clients log in? | Client resistance | Upload-only path; clear request list | Demo owner hub — 2 clicks upload; password set by firm | Owner UX | **PARTIAL** | **Yes** |
| Do I have to enter transactions twice? | Xero coexistence | **Yes if OPTION A**; **no if OPTION B** requests-only (limited P&L here) | Qualify: “How many lines/month?” — propose cutover subset | Ledger doc | **STRONG RISK** | **Yes** |
| Where is my data? | GDPR / trust | Postgres + object storage (local/S3); firm-scoped | Hosted region + backup on pilot; show audit + export | deploy docs | **PARTIAL** (legal docs missing) | Legal review |
| What if I stop using you? | Lock-in fear | CSV/XLSX exports; DB backup in exit plan | Show export buttons; reference `PILOT_DATA_EXIT_PLAN.md` | Reporting exports | **STRONG** for exports | No |
| Can my staff see every client? | Confidentiality | **Client access** scoping for non-admin; ADMIN sees all | Mention assignment; show accountant vs admin (optional Nimal login) | SECURITY docs, ClientAccessService | **STRONG** | No |
| Can I export everything? | Portability | P&L/income/expense CSV/XLSX; not full GL | Live export demo 30s | Report APIs | **PARTIAL** (no GL export) | No |
| What if someone changes after month-end? | Control | Closed period **write rejection** | Live failed edit after close | Golden path test | **STRONG** | No |
| Is this a full accounting package? | Category confusion | Income/expense + P&L; **not** double-entry GL | “We **close the month with evidence** — not replace statutory GL unless pilot scope says so.” | IMPLEMENTATION_STATUS | Honest gap | Yes — ICP |
| Bank feeds? | Expectation from Xero | **CSV import only** | “Sri Lanka / pilot banks via CSV; feeds later if validated.” | Banking importer | **PRODUCT GAP** | Yes |
| AI replaces bookkeepers? | Hype | AI optional; manual draft path | **AI OFF** in demo; “Accountant approves everything.” | docs/AI.md | N/A | No |
| Security / compliance certified? | Enterprise | Standard app security; **no** SOC2 claim | Honest: pilot DPA + hosting; roadmap | PRODUCTION_READINESS | **GAP** legal/cert | Legal |
| How is this different from Karbon / practice mgmt? | Category | **Readiness tied to ledger evidence + bank recon** | Focus demo on **blocker → resolve → close** not PM features | Close engine | **PARTIAL** | Yes — competitive |
| Price / ROI? | Budget | Manual billing; trial 14 days | Defer to pilot proposal; measure chase hours | Subscription code | Sales process | Yes |
| Mobile app? | Field owners | Responsive web | Phone browser upload in pilot smoke | — | **GAP** native | Optional |
| Tax / VAT filing? | Compliance | Not implemented | “Out of pilot scope.” | PILOT_PRODUCT_SCOPE | **GAP** | No |
| Integrations API? | IT buyer | REST API exists; not marketed as public integration platform | “Founder-assisted; no Xero connector in pilot.” | Controllers | **GAP** Xero | Validate |

### Answer strength summary

| Strength | Count (approx.) |
|----------|-----------------|
| STRONG ANSWER | 4 (tenant access, after-close control, exports partial-strong, audit) |
| PARTIAL ANSWER | Majority |
| WEAK ANSWER | Dual entry + Xero recon duplication |
| PRODUCT GAP | Xero/sync, feeds, tax, GL |
| CUSTOMER-VALIDATION | Coexistence model, owner login, WhatsApp, pay willingness |

### Best answer to “Why not just Xero?”

**“Xero is your general ledger. We’re the layer that tells you — across all clients — what’s still missing before you can **close September**, with evidence and bank reconciliation enforced, without you maintaining a separate spreadsheet tracker. Pilot clients can stay in Xero for GL while we run chase and close discipline — or you move a few simple clients fully here. We don’t claim to sync with Xero today.”**
