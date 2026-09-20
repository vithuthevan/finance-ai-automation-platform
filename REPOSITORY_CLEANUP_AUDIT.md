# Repository cleanup audit — Finance AI Automation Platform

**Stage:** 1 — AUDIT ONLY (no deletes, moves, commits, or push)  
**Audit date:** 2026-09-20  
**Repository:** `finance-ai-automation-platform` (product: **Finance Platform**, Document-to-Close for accounting firms)

> **Name mismatch:** Your brief references a “Care Home System.” This workspace is **not** a care-home codebase. `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md` explicitly verifies the correct product. Proceed with cleanup for **this** repository unless you intended a different project path.

---

## Phase 0 — Safety snapshot

| Check | Result |
|-------|--------|
| Current branch | `main` |
| Remote | `origin` → `https://github.com/VithuThevan/finance-ai-automation-platform.git` |
| vs `origin/main` | **Ahead 24 commits** (unpushed local history) |
| Staged changes | **None** (`git diff --cached --stat` empty) |
| Uncommitted changes | **Yes** — mix of application features, docs, deploy edits, QA artifact deletions, `.gitignore` |
| Untracked files | **Yes** — 31 root-level `*.md` playbooks + new backend/frontend sources |
| Force push / reset | **Not performed** |

**Important:** Cleanup commits must **not** be squashed with in-progress feature work (month-end command center, document request center, shell UI, etc.). Plan **separate** commit groups (see Phase 9).

Approximate **tracked** files: **559** (`git ls-files`).

---

## Phase 1 — Repository inventory (top level)

| Path | Role |
|------|------|
| `backend/` | Spring Boot modular monolith (Gradle), Flyway, tests |
| `frontend/` | Angular 20 SPA |
| `demo/` | `seed_demo.py`, sample files, Harbor Ledger demo README |
| `deploy/` | Nginx/Caddy, backup scripts, systemd units |
| `docs/` | 21 **tracked** product/deployment docs; `docs/reports/` **local-only** (~44 files, gitignored once `.gitignore` change lands) |
| `e2e/` | Playwright tests (`package.json`, specs, local `node_modules` via `e2e/.gitignore`) |
| `postman/` | Postman collection |
| `.github/workflows/` | `backend.yml`, `frontend.yml`, `e2e.yml` |
| `.cursor/rules/` | Agent architecture rules (4 × `.mdc`) |
| `docker-compose.yml`, `docker-compose.prod.yml` | Local/prod compose |
| `.env` | **Local only** (ignored) |
| `.env.example` | **Tracked** template |
| `data/`, `uploads/` | Local runtime data (ignored) |
| `frontend/dist/`, `frontend/.angular/`, `frontend/node_modules/` | Build/deps (ignored; dist present locally ~536 files) |
| Root `*.md` (31 untracked) | Demo, pilot, product discovery, conversion audits |
| Root `qa-*` | **Tracked** QA outputs/runners (should be untracked) |
| `.tools/` | Local tooling (ignored) |
| `.vscode/` | Local IDE settings (ignored, untracked `settings.json` on disk) |

---

## Phase 2 — Classification

Legend: **A** = commit · **B** = local only / ignore · **C** = delete · **R** = review · **ARC** = archive (move under `docs/archive/` or keep local only)

### A — MUST COMMIT (application & ops core)

| Area | Paths / notes |
|------|----------------|
| Backend source & tests | `backend/**` except `build/`, `.gradle/` |
| Frontend source | `frontend/src/**`, `frontend/public/**`, config (`angular.json`, `tsconfig*`, `package.json`, `package-lock.json`) — **not** `dist/`, `.angular/`, `node_modules/` |
| Database | Flyway migrations under `backend/platform-app/src/main/resources/db/migration/` |
| Demo (committed fixtures) | `demo/seed_demo.py`, `demo/verify_demo_state.py`, `demo/README.md`, `demo/files/*` |
| Deploy | `deploy/**` (including `backup.env.example`, scripts, nginx, systemd) |
| CI/CD | `.github/workflows/**` |
| Docker | `docker-compose.yml`, `docker-compose.prod.yml`, `.dockerignore` |
| API tooling | `postman/Income_API.postman_collection.json` |
| E2E | `e2e/**` except `node_modules/`, Playwright report dirs (per `e2e/.gitignore`) |
| Agent rules | `.cursor/rules/**`, `.cursorignore` |
| Config templates | `.env.example`, `deploy/backup/backup.env.example` |
| Core docs (tracked) | All 21 files under `docs/*.md` listed in Phase 4 |
| Root | `README.md`, `.gitignore`, `.gitattributes` |

**Uncommitted application work (A — commit separately from hygiene):**

- New month-end / document-request backend services, DTOs, controllers, integration test
- Frontend `month-end/`, `requests/`, shell/routes/dashboard/work/owner/admin changes
- Modified `demo/seed_demo.py`, deploy/docs path tweaks tied to product

### B — KEEP LOCALLY, DO NOT COMMIT

| Path | Recommendation |
|------|----------------|
| `.env` | Keep; never commit |
| `deploy/backup/backup.env` | Keep; already in `.gitignore` |
| `data/`, `uploads/` | Runtime storage |
| `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/` | Rebuild from source |
| `backend/**/build/`, `.gradle/` | Gradle outputs |
| `e2e/node_modules/`, Playwright `test-results/`, `playwright-report/` | Per `e2e/.gitignore` |
| `test-output.txt` | Local test log |
| `qa-results-*`, `qa-run-*` | Local QA; **untrack** from Git (see `TRACKED_FILES_THAT_SHOULD_BE_IGNORED.md`) |
| `docs/reports/**` | Local readiness/audit snapshots (~44 files); **gitignore** (unstaged change ready) |
| `.vscode/` | Machine-specific unless team agrees to share settings |
| `HELP.md` | Gradle-generated help |
| `.tools/` | Local |

**Template gap (optional, post-approval):** No change required if `.env.example` remains the single source; do not duplicate unless secrets are split across more files.

### C — DELETE (proposed; requires approval)

| File | Reason | Evidence | Replacement | Risk |
|------|--------|----------|-------------|------|
| `qa-results-tc*.md` | Ephemeral QA write-ups | Working tree already deleted; CSV siblings remain tracked | Re-run QA locally if needed | Low |
| `qa-results-tc*.csv` | Generated metrics | Not referenced by CI | Regenerate from scripts | Low |
| *(optional local only)* Duplicate copies under `docs/reports/` | Many `FINAL_*` / duplicate runbooks overlap root guides | Same topics as `A_TO_Z_*`, `COMMERCIAL_*`, `docs/USER_GUIDE.md` | Keep one canonical doc per track | Medium — **prefer ARC not C** |

**Do not delete** root pilot/demo markdown without your choice of canonical doc (see duplicates).

### ARC — ARCHIVE (optional moves after approval)

| Candidates | Suggestion |
|--------------|------------|
| `docs/JAVA_SPRING_BOOT_PROJECT_INTERVIEW_BANK.md` | `docs/archive/career/` — not required for product ops |
| `docs/ARCHITECTURE_INTERVIEW_ANSWERS.md` | Same |
| `COMMERCIAL_DEMO_UPGRADE_REPORT.md` | One-time upgrade narrative → `docs/archive/demo/` |
| Entire `docs/reports/` tree | Either stay **local-only** (current `.gitignore` intent) **or** cherry-pick 2–3 canonical files into `docs/operations/` and delete the rest locally |

### R — REVIEW — DO NOT DELETE

| Item | Question |
|------|----------|
| `docs/TEST_RESULTS.md` | Snapshot from 2026-09-02; stale counts. **Keep** as historical record, **refresh** after next test run, or **archive**? |
| Two demo tracks | Harbor Ledger (`seed_demo.py` + `A_TO_Z_*` + `COMMERCIAL_*`) vs Summit from-scratch (`FROM_SCRATCH_*`). **Both intentional** — do not merge without product decision |
| `qa-run-*.ps1` | Delete from repo vs relocate to `scripts/qa/` as maintained tooling |
| 24 unpushed commits | Push hygiene branch only after you review commit messages on `main` |
| Uncommitted `shell.component.ts` large diff | Ensure formatting-only vs functional before any commit |

---

## Phase 3 — Secrets audit

No live API keys, PATs, or cloud access keys found (pattern scan for `sk-`, `AKIA`, `ghp_`).

| Finding | File | Type | Action |
|---------|------|------|--------|
| SECRET DETECTED (intentional demo) | `demo/seed_demo.py`, demo READMEs | Demo password `DemoPass123!` | **Acceptable** for demo; rotate if same password used in production |
| SECRET DETECTED (dev default) | `application-local.yml`, compose defaults | JWT / DB placeholders | **Acceptable** with env override; prod validators enforce real secrets |
| SECRET DETECTED (test fixtures) | `application-test.yml`, integration test YAML | Test JWT base64 | **Acceptable** for CI/local test only |
| SECRET DETECTED (CI fixtures) | `.github/workflows/e2e.yml` | `ci_e2e_password`, CI JWT string | **Acceptable** for ephemeral CI DB |
| Local secrets | `.env` | Real local config | **Not in Git** — verify never committed: `git log -- .env` returned no history in audit |
| Documentation | Pilot/runbook markdown | Mentions SMTP/JWT setup | No literal production secrets |

**History note:** If `.env` or real credentials were ever committed in the past, removing them in a cleanup commit is **not** sufficient — rotation required. No evidence of committed `.env` at audit time.

---

## Phase 4 — Documentation audit

### KEEP IN REPOSITORY (tracked today)

`docs/AI.md`, `ARCHITECTURE_*.md`, `BANK_RECONCILIATION.md`, `CLOSE.md`, `CODEBASE_ONBOARDING.md`, `CRITICAL_CODE_REVIEW_FINDINGS.md`, `DEPLOYMENT.md`, `FIRM_SETTINGS.md`, `IMPLEMENTATION_STATUS.md`, `PRACTICE_WORKFLOW.md`, `PRODUCTION_READINESS.md`, `PRODUCT_ROADMAP.md`, `SAAS_SUBSCRIPTIONS.md`, `SECURITY.md`, `TESTING.md`, `TEST_RESULTS.md`, `USER_GUIDE.md`, `VPS_HOSTING.md`, interview bank files.

### COMMIT (currently untracked root — high value)

| Group | Files |
|-------|--------|
| Demo — Harbor / commercial | `A_TO_Z_CLIENT_DEMO_OPERATOR_GUIDE.md`, `CLIENT_DEMO_CHEAT_SHEET.md`, `CLIENT_DEMO_DATA_CARD.md`, `ACCOUNTING_FIRM_15_MIN_DEMO_PLAN.md`, `COMMERCIAL_DEMO_RUNBOOK.md`, `COMMERCIAL_DEMO_RESET.md`, `DEMO_OBJECTION_MAP.md`, `DEMO_CONVERSION_*.md` |
| Demo — from scratch | `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md`, `FROM_SCRATCH_DEMO_*` (3 files) |
| Pilot / first customer | `FIRST_*`, `PILOT_*`, `PRE_PILOT_DO_NOT_BUILD.md` |
| Product discovery | `CUSTOMER_DISCOVERY_PLAN.md`, `FOUNDER_PRODUCT_HYPOTHESES.md`, `PRODUCT_MARKET_NEED_*` |
| One-time report | `COMMERCIAL_DEMO_UPGRADE_REPORT.md` → prefer **ARC** |

### MOVE INTO `docs/` (proposed, not executed)

```
docs/
  demo/           # commercial + from-scratch operator guides
  pilot/          # PILOT_*, FIRST_PAID_*, onboarding
  product/        # discovery, hypotheses, market need
  operations/     # optional: slim ops runbooks from docs/reports if any are promoted
  archive/        # interview bank, upgrade reports, superseded audits
```

**Safe default:** Commit root markdown **as-is** first (no moves), then reorganize in a **docs-only** commit to avoid broken links.

### LOCAL ONLY (`docs/reports/`)

44 files including overlapping titles: `FINAL_CLIENT_DEMO_RUNBOOK.md`, `CLIENT_DEMO_OPERATOR_RUNBOOK.md`, `FINAL_PRODUCTION_READINESS_REPORT.md`, etc. **Aligned with unstaged `.gitignore` rule** — treat as developer machine artifacts unless you promote specific files.

---

## Phase 5 — Generated / local artifacts

| Artifact | Committed? | Should be |
|----------|------------|-----------|
| `frontend/dist/` | No | Ignored |
| `frontend/.angular/` | No | Ignored |
| `node_modules` (frontend) | No | Ignored |
| Gradle `build/` | No | Ignored |
| `qa-results-*`, `qa-run-*` | **Yes** | **Untrack + ignore** |
| `docs/reports/` | No (after gitignore) | Ignore |
| Playwright output | No | `e2e/.gitignore` |
| `test-output.txt` | No | Ignored |

---

## Phase 6 — Proposed `.gitignore` improvements

Current file is solid for Gradle, frontend, secrets, `data/`, `test-output.txt`. **After approval**, merge:

```gitignore
# OS
.DS_Store
Thumbs.db

# Node (all packages)
**/node_modules/

# Frontend coverage
frontend/coverage/

# QA artifacts (align with .cursorignore)
qa-results-*
qa-run-*

# Env (keep example)
.env
.env.*
!.env.example

# Playwright (belt-and-suspenders; e2e/.gitignore already covers)
e2e/test-results/
e2e/playwright-report/
e2e/blob-report/
e2e/playwright/.cache/

# Reports (already in working tree diff)
docs/reports/
```

**Keep:** Explicit `frontend/node_modules/` is fine alongside `**/node_modules/`.  
**Do not ignore:** `appsettings`-style YAML in `backend/.../resources/` (required for profiles).

---

## Phase 7 — Tracked files to untrack

See **`TRACKED_FILES_THAT_SHOULD_BE_IGNORED.md`**.

---

## Phase 8 — Duplicate / overlap matrix

| Candidate A | Candidate B | Keep | Archive/Delete | Reason |
|-------------|-------------|------|----------------|--------|
| `A_TO_Z_CLIENT_DEMO_OPERATOR_GUIDE.md` | `docs/reports/FINAL_CLIENT_DEMO_RUNBOOK.md` | **A_TO_Z** (if promoting one) | Local reports | A_TO_Z is newer, untracked, comprehensive |
| `COMMERCIAL_DEMO_RUNBOOK.md` | `ACCOUNTING_FIRM_15_MIN_DEMO_PLAN.md` | **Both** | — | Runbook vs timed agenda |
| `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md` | `A_TO_Z_*` | **Both** | — | Different narratives (Summit vs Harbor) |
| `CLIENT_DEMO_CHEAT_SHEET.md` | `FROM_SCRATCH_DEMO_CHEAT_SHEET.md` | **Both** | — | Per-track cheat sheets |
| `README.md` demo section | `demo/README.md` | **Both** | — | Root = overview; demo = day-of commands |
| `docs/PRODUCTION_READINESS.md` | `docs/reports/FINAL_PRODUCTION_READINESS_REPORT.md` | **docs/PRODUCTION_READINESS.md** | Local report | Tracked doc is canonical |
| `docs/USER_GUIDE.md` | Pilot runbooks | **All** | — | Different audiences |

---

## Phase 9 — Proposed final structure

Use **existing** layout; optional doc folders only:

```
finance-ai-automation-platform/
├── backend/
├── frontend/
├── demo/
├── deploy/
├── docs/                    # product + deployment (+ optional subfolders)
├── e2e/
├── postman/
├── .github/
├── .cursor/
├── docker-compose.yml
├── docker-compose.prod.yml
├── .env.example
├── README.md
└── (pilot/demo *.md at root OR under docs/pilot, docs/demo)
```

No need to rename `backend/` / `frontend/` or introduce a `CarehomeSystem/` root.

---

## Phase 10 — Execution plan (awaiting your approval)

### 1. Files to KEEP and COMMIT

- All of **§ A** above, including choosing whether to commit the 31 untracked markdown playbooks and the in-progress feature code (recommended: **yes**, in logical feature commits).

### 2. KEEP LOCALLY but IGNORE

- **§ B**; untrack QA artifacts; confirm `docs/reports/` gitignore committed.

### 3. DELETE (from Git and optionally disk)

- QA result CSV/MD; optionally prune redundant `docs/reports/` **locally** after picking archives.

### 4. ARCHIVE

- Interview bank, upgrade report, superseded local reports (optional).

### 5. REVIEW

- **§ R** items — your call on `TEST_RESULTS.md`, `qa-run` scripts location, demo doc consolidation.

### 6. Secrets

- No rotation required from this audit; keep `.env` out of commits.

### 7. `.gitignore`

- **§ Phase 6** + commit unstaged `docs/reports/` rule.

### 8. Directory structure

- Optional `docs/{demo,pilot,product,archive}` in a **second** docs commit.

### 9. Proposed Git commit groups (after approval)

**Order matters — hygiene separate from product:**

| # | Message (draft) | Contents |
|---|-----------------|----------|
| 1 | `feat: month-end command center and document request center` | Uncommitted backend + frontend feature files (only if you want this on `main` now) |
| 2 | `docs: add demo, pilot, and product discovery playbooks` | 31 untracked root `*.md` (+ README tweaks if any) |
| 3 | `chore: ignore local reports and QA artifacts` | `.gitignore`; `git rm --cached` qa-* |
| 4 | `docs: organize playbooks under docs/demo and docs/pilot` | **Only if** you approve moves |
| 5 | `chore: remove obsolete QA result files from repository` | If CSV/MD removals not folded into #3 |
| 6 | `docs: update deployment and demo seed notes` | `deploy/*`, `docs/DEPLOYMENT.md`, `demo/seed_demo.py` if not in #1 |

Then **push** (non–force): `git push origin main` — only after clean tree and your explicit go-ahead for Stage 2.

**Note:** You are **24 commits ahead** of `origin/main`. Consider reviewing `git log origin/main..main` before push so remote history matches your intent.

---

## Stage 2 gate

Reply with approval (or edits) to:

- Untrack/delete QA artifacts  
- Commit `.gitignore` + `docs/reports/` ignore  
- Commit untracked markdown at root vs move under `docs/`  
- Whether feature work commits separately before hygiene  
- Push all 24+ new commits to `origin/main`

**No destructive operations will run until you approve.**
