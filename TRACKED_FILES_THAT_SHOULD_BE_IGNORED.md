# Tracked files that should be ignored

**Audit date:** 2026-09-20  
**Branch:** `main` (read-only audit; no `git rm` performed yet)

Adding paths to `.gitignore` does **not** stop Git from tracking files that are already committed. The items below should be removed from the index with `git rm --cached` (keeping local copies where useful) after you approve the cleanup plan.

---

## Summary

| Path pattern | Tracked count | Recommended action |
|--------------|---------------|-------------------|
| `qa-results-*.{csv,md}` | 6 | `git rm --cached`; add to `.gitignore`; keep local if needed |
| `qa-run-*.ps1` | 3 | `git rm --cached`; add to `.gitignore` |
| *(none)* `test-output.txt` | 0 | Already untracked; ensure `.gitignore` entry remains |
| *(none)* `frontend/dist/` | 0 | Already untracked |
| *(none)* `frontend/node_modules/` | 0 | Already untracked |
| *(none)* `.env` | 0 | Already untracked |
| *(none)* `docs/reports/` | 0 | Not tracked; unstaged `.gitignore` addition correctly ignores this tree |

---

## Detail — QA automation artifacts (should not be in Git)

These are **local test run outputs and one-off PowerShell runners**, not application source. `.cursorignore` already excludes them from AI indexing; **Git still tracks them**.

| File | Type | Action |
|------|------|--------|
| `qa-results-tc001-027.csv` | Generated QA results | `git rm --cached` |
| `qa-results-tc001-027.md` | Generated QA results | `git rm --cached` (working tree already deleted) |
| `qa-results-tc028-050.csv` | Generated QA results | `git rm --cached` |
| `qa-results-tc028-050.md` | Generated QA results | `git rm --cached` (working tree already deleted) |
| `qa-results-tc051-072.csv` | Generated QA results | `git rm --cached` |
| `qa-results-tc051-072.md` | Generated QA results | `git rm --cached` (working tree already deleted) |
| `qa-run-tc001-027.ps1` | Local QA runner | `git rm --cached` (optional: move to `scripts/qa/` if you want a **committed** runner without committing results) |
| `qa-run-tc028-050.ps1` | Local QA runner | `git rm --cached` |
| `qa-run-tc051-072.ps1` | Local QA runner | `git rm --cached` |

**Evidence:** Filenames match ephemeral test batches (`tc001-027`, etc.); `.cursorignore` lines 38–39 treat `qa-results-*` and `qa-run-*` as generated. No CI workflow references these paths.

**Risk:** Low. Removing from Git does not delete local files unless you also delete them on disk.

---

## Verified — correctly untracked (no action)

| Path | Ignored by |
|------|------------|
| `.env` | `.gitignore` |
| `.vscode/` | `.gitignore` |
| `frontend/dist/` | `.gitignore` |
| `frontend/.angular/` | `.gitignore` |
| `test-output.txt` | `.gitignore` |
| `HELP.md` | `.gitignore` (Gradle help; not in index) |
| `data/`, `uploads/` | `.gitignore` |
| `docs/reports/**` | Proposed unstaged rule `docs/reports/` |

---

## Not recommended for untracking

| Path | Reason |
|------|--------|
| `.env.example` | Safe template; **should stay committed** |
| `deploy/backup/backup.env.example` | Safe template |
| `demo/files/*` | Intentional demo fixtures (CSV/PDF/PNG) |
| `docker-compose*.yml` | Uses placeholders / env vars, not live secrets |
| `.cursor/rules/*.mdc` | Shared agent conventions (committed) |

---

## Post-cleanup verification commands

After approval and `git rm --cached`:

```powershell
git ls-files | Select-String -Pattern "qa-results|qa-run|test-output"
git check-ignore -v qa-results-tc001-027.csv
```

Expected: no tracked QA artifacts; ignored paths reported by `check-ignore`.
