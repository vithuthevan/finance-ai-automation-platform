# First paid pilot gap matrix

**Audit date:** 2026-09-20  
**Pilot tier:** B — First paid pilot (not public SaaS scale)  
**Evidence:** CODE-VERIFIED | TEST-VERIFIED | DOCUMENTED | INFERRED | RUNTIME-VERIFIED | LEGAL-REVIEW | EXTERNAL-INFRA | CUSTOMER-VALIDATION

| AREA | CURRENT STATE | EVIDENCE | CUSTOMER IMPACT | BUSINESS IMPACT | PILOT BLOCKER? | SEVERITY | RECOMMENDED ACTION | MANUAL FOR PILOT? | VALIDATION REQUIRED? | OWNER | TIME | DEPENDENCY |
|------|---------------|----------|-----------------|-----------------|----------------|----------|-------------------|-------------------|---------------------|-------|------|------------|
| Repository identity | Finance Platform: Angular + Spring monolith + PostgreSQL | README, structure | — | — | No | — | — | — | No | — | — | — |
| Self-registration | Public register creates firm + ADMIN + STARTER trial | `RegistrationService`, `FirmService` | Firm can start | Revenue funnel works | No | P2 | Optional wizard later | Yes (Zoom) | No | Product | S | — |
| Post-register login | No JWT; redirect to login | README, register UI | Extra step | Minor friction | No | P2 | Post-register checklist UI | Yes | No | Eng | S | — |
| Empty dashboard | Zeros until data | `dashboard.page.ts` | "What next?" confusion | Slower TTV | No | P1 | Next-steps panel + runbook | Yes (founder) | No | Eng/CS | S | — |
| Default categories | None on register | `FirmService`, FROM_SCRATCH doc | Cannot post until categories exist | Onboarding delay | No | P1 | Template pack on firm create | Yes (founder creates) | No | Eng | S | — |
| Firm defaults | LKR, Asia/Colombo, FY month 4, AI on | V15, FIRM_SETTINGS | Wrong currency if not changed | Mis-reports if ignored | No | P1 | Confirm settings on kickoff | Yes | No | CS | XS | — |
| Trial expiry 14d | Auto-suspend writes | SAAS_SUBSCRIPTIONS | Sudden lockout | Churn / embarrassment | **Yes** if not ops | P1 | Extend trial or ACTIVE before pilot | Yes (platform API) | No | Ops | XS | Platform admin |
| Client CRUD | UI + API firm-scoped | IMPLEMENTATION_STATUS | Core workflow | — | No | — | — | — | No | — | — | — |
| Client CSV import | Not implemented | grep | Slow for 50+ clients | — | No for 5 clients | P2 | Defer | Yes (manual) | Yes | Product | L | — |
| Users UI client access | API exists; UI missing | users.page.ts, UserController | Owners see empty portal | **Evidence loop breaks** | **Yes** for self-serve | P1 | Add client access to Users UI | Yes (API on Zoom) | No | Eng | M | — |
| Owner password setup | Admin sets password at create | UserService | Security/training burden | Support load | No | P1 | Invite-link flow later | Yes | No | Eng | M | SMTP |
| Email verification | Default off | AuthProperties | Immediate login | — | No | P2 | Keep off for pilot | — | No | Ops | XS | — |
| Password reset | Token + email | SessionService | Owners locked out | Support tickets | **Yes** without SMTP | P0 | Configure SMTP + test | No | No | DevOps | S | Hosting |
| RBAC enforcement | Service-layer + SECURITY.md | docs/SECURITY | Trust | Liability | No | — | — | — | No | — | — | — |
| Tenant isolation | Integration tests | TenantIsolationIntegrationTest | Data leak if broken | Fatal | **Yes** if tests not run | P0 | Run PG tests pre go-live | No | No | Eng | S | Docker CI |
| JWT / refresh | Memory access + httpOnly refresh | SECURITY.md | Session security | — | No | P1 | MFA later | — | No | Eng | L | — |
| MFA | Not implemented | CRITICAL_CODE_REVIEW | Weaker account security | Acceptable pilot | No | P2 | Defer | — | Yes | Product | L | — |
| Document storage | Local volume or S3; keys firm-scoped | IMPLEMENTATION_STATUS | Doc loss if no backup | Legal/trust | **Yes** without backup | P0 | Backup + S3 for prod | Partial | No | DevOps | M | Hosting |
| Download auth | Stream via API | IMPLEMENTATION_STATUS | Leak if misconfigured | — | No | — | — | — | No | — | — | — |
| AV scan | None | IMPLEMENTATION_STATUS | Malware risk | Low for pilot | No | P3 | Defer | — | No | Eng | L | — |
| DB backups | Scripts exist; not auto until configured | deploy/backup/README | Data loss | Fatal | **Yes** | P0 | Schedule + offsite | No | No | DevOps | S | Host |
| Restore drill | "Not production-validated" | deploy/backup/README | Cannot recover | Fatal | **Yes** | P0 | Execute verify-restore.sh | No | No | DevOps | M | Backups |
| Flyway V1–V29 | Migrations in repo | db/migration | Deploy fail | — | No | P1 | Staging migrate test | Yes | No | Eng | XS | Staging |
| Accounting model | Income/expense approved-only P&L | IMPLEMENTATION_STATUS | Wrong expectations | Mis-selling | No | P1 | PILOT_PRODUCT_SCOPE | Yes | Yes | Product | XS | — |
| Period close guards | Closed period writes blocked | CLOSE.md, tests | Integrity | Trust | No | — | — | — | No | — | — | — |
| Bank CSV | Generic importer + profiles | GoldenPath tests | Core hero | — | No | — | Bank mapping doc | Yes | No | CS | XS | — |
| Bank splits | One expense/income per match | BankReconciliationService | Edge cases manual | — | No | P2 | Document limits | Yes | No | CS | — | — |
| Close bank blocker | Recon required when bank exists | BankReconciliationCheck | Cannot close | Expected | No | — | Train on ignore/match | Yes | No | CS | — | — |
| Document requests | Email + in-app | DocumentRequestService | Chase reduction | Value prop | Partial without SMTP | P1 | SMTP + test | Partial | No | DevOps | S | SMTP |
| Xero integration | None | grep | "We use Xero" | **Deal risk** | Commercial | P1 | OPTION B/C strategy | Yes | **Yes** | Founder | — | Strategy doc |
| Reporting PDF | Not built | IMPLEMENTATION_STATUS | Partner habit | — | No | P2 | Validate need | Yes (XLSX) | Yes | Product | M | — |
| Data export | Per-report CSV/XLSX | ReportingController | Exit anxiety | — | No | P1 | PILOT_DATA_EXIT_PLAN | Yes | No | CS | XS | — |
| Stripe billing | Manual only | SAAS_SUBSCRIPTIONS | — | — | No | — | PILOT_BILLING_PROCESS | Yes | Yes | Founder | — | — |
| Privacy policy | Not in repo | grep | Legal exposure | Cannot store PII | **Yes** | P0 | Draft + counsel | No | LEGAL | Legal | M | — |
| AI default on | Firm aiEnabled true default | FIRM_SETTINGS | Sends docs to LLM if enabled | GDPR risk | **Yes** if AI on | P0 | AI off pilot + env false | Yes | No | Ops | XS | — |
| Production email | Prod blocks log provider | ProductionEnvironmentValidator | No reset/notify | Onboarding fail | **Yes** | P0 | SMTP | No | No | DevOps | S | Host |
| Monitoring | Health endpoints; no SaaS APM | SECURITY.md | Slow incident detect | — | No | P1 | Uptime + backup alerts | Partial | No | DevOps | S | Host |
| CI backend tests | Gradle test on push | backend.yml | Regressions | — | No | P1 | Enable Docker IT in CI | No | No | Eng | S | — |
| E2E | Depends seed_demo | e2e.yml | — | — | No | P2 | From-scratch e2e later | — | No | Eng | M | — |
| Global firm name unique | DB constraint | FirmService | Register fail 409 | Awkward signup | No | P2 | UX message | Yes | No | Eng | XS | — |
| Global email unique | Users | UserService | Multi-firm email blocked | Rare | No | P3 | — | — | No | — | — | — |
| STARTER 3 users | Quota | SAAS_SUBSCRIPTIONS | Cannot add staff+owners | Pilot blocked | **Yes** if >3 | P1 | PRACTICE plan or ops bump | Yes | No | Ops | XS | — |
| Platform admin docs | SAAS doc outdated (email allowlist) | vs V24 grants | Ops confusion | Wrong access | No | P1 | Update doc | Yes | No | Eng | XS | — |
| DEPLOYMENT backup links | docs/reports missing | DEPLOYMENT.md | Ops gap | — | No | P1 | Point to deploy/backup | Yes | No | Eng | XS | — |

**Counts (disciplined):**

- **P0 blockers:** 8 (tenant test gate, backup, restore drill, SMTP, legal privacy, AI pilot posture, hosting/TLS/secrets as one operational cluster counted as backup+restore+SMTP+legal+isolation verification)
- **P1 requirements:** 14
- **P2 opportunities:** 10+
