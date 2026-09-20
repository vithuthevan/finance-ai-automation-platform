# Product–market need matrix

**Product:** Finance Platform (document-to-close workspace)  
**Date:** 2026-09-20  
**Legend — SOLUTION STRENGTH:** A = clearly solves | B = partial | C = does not | D = may add work  
**STATUS:** STRONG HYPOTHESIS | PLAUSIBLE | WEAK | DUPLICATIVE | UNKNOWN  

| CUSTOMER PAIN | PERSONA | FREQUENCY HYPOTHESIS | CURRENT WORKAROUND | CURRENT PRODUCT SOLUTION | STRENGTH | BEHAVIOUR CHANGE | COMPETITIVE ALTERNATIVE | POTENTIAL BUSINESS VALUE | EVIDENCE | CUSTOMER VALIDATION QUESTION | STATUS |
|---------------|---------|----------------------|--------------------|---------------------------|----------|------------------|-------------------------|--------------------------|----------|-------------------------------|--------|
| Missing receipts at month-end | Accountant | Monthly per client | WhatsApp chase, email | Document requests + owner upload | B | Owners must log in; accountant creates requests | Dext email/WhatsApp capture | Fewer close delays; less rework | CODE: requests block close | “Show last month chase — channels and attempts?” | PLAUSIBLE |
| Missing invoices | Accountant | Monthly | Email/Drive | Same as receipts | B | Same | Dext → Xero | Same | CODE | “Invoices vs receipts — same pain?” | PLAUSIBLE |
| Docs via WhatsApp (unstructured) | Accountant | Daily | WhatsApp groups | No ingestion; manual upload to platform | C | Would need dual channel or switch | WhatsApp, Dext | Low unless portal wins | CODE: no WhatsApp | “If clients must use portal, what % comply?” | UNKNOWN |
| Docs via email | Accountant | Daily | Email folders | No email-in; SMTP for notifications only | C/B | Forward/upload manually | Dext email-in | Medium if centralized | CODE | “Would unique email-in replace portal?” | PLAUSIBLE |
| Documents arrive late | Partner | Monthly wave | Spreadsheets, nagging | Requests + overdue in work queue | B | Request discipline | Karbon client tasks | Shorter close cycle | CODE: overdue counts | “What delayed last close — top 3 causes?” | PLAUSIBLE |
| Repeated client chasing | Accountant | Weekly | WhatsApp/calls | Requests + remind API | B | Structured vs informal | Karbon, email | Hours saved **if** adopted | CODE | “Hours/month chasing per 10 clients?” | CUSTOMER VALIDATION REQUIRED |
| Unknown if client ready to close | Partner / PM | Daily in peak | Excel tracker, memory | Portfolio + readiness % + blockers | A/B | All blockers must be maintained in platform | Excel, Karbon work status | Partner time; fewer status meetings | CODE: CloseReadinessService, work UI | “How do you know today — show tracker?” | STRONG HYPOTHESIS |
| Month-end checklist spreadsheets | PM | Monthly | Google Sheets | Close checklist items from engine | B | Replace sheet only if blockers match reality | Excel, Karbon templates | Coordination time | CODE | “Does our blocker list match your checklist?” | PLAUSIBLE |
| Approval backlog | Accountant | Weekly | Xero + email | Draft blockers + work queue TRANSACTION_APPROVAL | A/B | Ledger in platform | Xero approval apps | Faster close | CODE | “Where do approvals happen today?” | DUPLICATIVE if Xero-only |
| Unreconciled bank lines | Accountant | Monthly | Xero bank rec | CSV import + match; blocks close | A/B | CSV upload + match in platform | Xero feeds | Risk reduction | CODE: BankReconciliationCheck | “Do you recon in Xero only?” | WEAK for Xero-heavy; PLAUSIBLE CSV firms |
| Bank CSV manipulation in Excel | Accountant | Monthly | Excel | Importer + mapping profiles | A | Use platform instead of Excel prep | Excel | Small time save | CODE + tests | “Still manipulate CSV before import?” | PLAUSIBLE (LKR/CSV) |
| Partner asks “status of all clients?” | Partner | Daily (peak) | Ask staff | Portfolio table + clients ready metric | B | Data currency | Karbon dashboard | Supervision time | CODE | “How often do you ping staff for status?” | STRONG HYPOTHESIS |
| No portfolio-wide view | Partner | Monthly | Spreadsheets | Work summary + portfolio | A/B | — | PM software | Capacity planning | CODE | “Would you open this daily?” | PLAUSIBLE |
| Staff workload visibility | PM | Weekly | Gut feel, timesheets | Dashboard/work counts (not revenue rollup) | B | — | Karbon, FC | Hiring decisions | CODE | “How do you spot overload?” | WEAK |
| Evidence scattered (Drive + email) | Accountant | Ongoing | Drive folders | Firm-scoped document store + link to txn | B | Upload to second store | Drive, Dext | Audit prep time | CODE | “Single source of truth for evidence?” | PLAUSIBLE |
| Client comms scattered | Accountant | Daily | WhatsApp/email | In-app + email notifications | C/B | — | Integrated email in Karbon | Low alone | CODE | “Would you move chase off WhatsApp?” | UNKNOWN |
| Audit evidence retrieval | Auditor / Partner | Ad hoc | Drive hunt | Audit log + linked docs | B | — | Dext vault | Compliance comfort | CODE | “Last audit — time to gather?” | PLAUSIBLE |
| Repeated monthly requests (same items) | Accountant | Monthly | Copy-paste email | Manual requests each time | C | — | Recurring tasks (Karbon) | Small | CODE: no recurring packs | “Same ask every month — automate?” | PLAUSIBLE |
| Close delays (firm-level) | Partner | Monthly | Overtime | Readiness prevents premature close | B/E | — | — | Billing realization | INFERRED | “Revenue impact of late close?” | UNKNOWN |
| Rework from wrong/missing data | Accountant | Monthly | Review in Xero | Review docs + approve ledger | B | Dual entry risk | Xero | Error cost | CODE | “% rework from missing docs?” | UNKNOWN |
| Duplicate data entry (Xero + platform) | Accountant | Daily if OPTION A | N/A | Full expense/income entry | D | High | Xero only | Negative | CODE + PILOT_LEDGER doc | “Maximum lines/month you’d re-key?” | STRONG HYPOTHESIS (risk) |
| Categories setup burden | Admin | Onboarding | Xero COA | Manual categories; **no default pack** | C/D | Create all categories | Xero sync | Onboarding delay | CODE | “Who maps COA — how long?” | WEAK onboarding |
| Business owner won’t use software | Owner | Per request | Send photos on WhatsApp | Login + upload | B | **High friction** | WhatsApp | Chase continues | CODE: owner page | “Owners — app or WhatsApp only?” | STRONG HYPOTHESIS (risk) |
| P&L for management accounts | Partner | Monthly | Xero reports | Approved P&L export | B/C | Enter data here | Xero | Low if Xero SOR | CODE | “Who reads P&L — from where?” | DUPLICATIVE |
| Statutory / tax accounts | Partner | Annual | Xero/tax software | Not supported | C | — | Xero | N/A | PILOT_SCOPE | — | N/A |
| Trust in “ready” flag | Partner | Monthly | Partner review | Rule-based blockers | B | Accountants must maintain | Manual sign-off | Risk | CODE | “What’s NOT in our blockers?” | CUSTOMER VALIDATION REQUIRED |
| Multi-currency clients | Firm | Varies | Xero | Single firm currency | C | — | Xero | Disqualifier | CODE | — | WEAK fit |
| AI reduces review time | Accountant | Per doc | Dext OCR | Optional vision extract → draft | B | Review AI drafts | Dext | Unclear | CODE + AI.md | “Trust AI suggestions?” | UNKNOWN |
| Tenant/data security trust | Partner | Purchase | — | JWT, firm scope, audit | B | Host on VPS with backups | — | Deal enabler | CODE + gaps in ops | “Data residency concerns?” | PLAUSIBLE |
| Trial expires at 14 days | Admin | Pilot | — | Manual extend via platform admin | C | Ops process | — | Embarrassment | CODE | — | Ops not PMF |

---

## Feature reassessment (commercial)

| Feature | Classification | Rationale |
|---------|----------------|-----------|
| Close readiness engine | **CORE VALUE** (conditional) | Closest to differentiated hypothesis |
| Practice work queue + portfolio | **CORE VALUE** (conditional) | Delivers “aha” if trustworthy |
| Document requests | **SUPPORTING** → core for Direction 3 | Chase wedge |
| Document inbox + review | **SUPPORTING** | Needed for evidence story |
| Ledger draft/approve | **CORE** (A) / **POSSIBLE DUPLICATION** (B) | Model-dependent |
| Bank CSV + reconciliation | **SUPPORTING** / segment differentiator | Not universal |
| P&L & exports | **TABLE STAKES** / **COMMODITY** | vs Xero |
| AI extraction | **LOW COMMERCIAL VALUE** initially | Validate |
| Notifications | **TABLE STAKES** | |
| Audit log | **SUPPORTING** | |
| Subscription/quotas | **TABLE STAKES** | |
| Users without client-access UI | **LOW COMMERCIAL VALUE** (blocks adoption) | Activation gap |
| No default categories | **LOW COMMERCIAL VALUE** | Friction |
