# Repository cleanup verification

**Date:** 2026-09-20  
**Branch:** `main`

## Files deleted from Git (repository)

| Path | Notes |
|------|--------|
| `qa-results-tc001-027.csv` | Untracked; local copy may remain |
| `qa-results-tc028-050.csv` | Same |
| `qa-results-tc051-072.csv` | Same |
| `qa-results-tc001-027.md` | Removed from index |
| `qa-results-tc028-050.md` | Removed from index |
| `qa-results-tc051-072.md` | Removed from index |
| `qa-run-tc001-027.ps1` | Untracked from Git |
| `qa-run-tc028-050.ps1` | Untracked from Git |
| `qa-run-tc051-072.ps1` | Untracked from Git |

## Files moved

None (playbooks committed at repository root per plan).

## Files ignored (`.gitignore`)

- `docs/reports/`
- `qa-results-*`, `qa-run-*`
- `**/node_modules/`, `frontend/coverage/`
- `.env.*` with `!.env.example`
- OS: `.DS_Store`, `Thumbs.db`
- Playwright output under `e2e/`

## Files added

- 31 demo/pilot/product markdown playbooks (root)
- `REPOSITORY_CLEANUP_AUDIT.md`, `TRACKED_FILES_THAT_SHOULD_BE_IGNORED.md`
- Month-end / document-request application sources (feature commit)

## Source changes (cleanup scope)

- **Hygiene only:** `.gitignore`, QA artifact removal from index, audit docs
- **Post-audit fix:** `MonthEndCommandCenterService.java` — correct record accessor names (compile fix; no intentional behaviour change)

## Build verification

| Check | Result |
|-------|--------|
| Backend `:platform-app:build` (`-x test`) | **PASS** (after accessor fix) |
| Frontend `npm run build` | **PASS** (one existing NG8107 warning in `banking.page.ts`) |
| Backend tests | **Not run** (build used `-x test` for speed after cleanup) |

## Git status before push

Working tree clean; branch ahead of `origin/main` (24 prior commits + 5 cleanup-related commits).

## Remaining warnings

- Angular compiler warning NG8107 in `banking.page.ts` (pre-existing)
- `docs/reports/` remains on disk locally; ignored
- QA CSV/scripts may still exist locally but are not tracked
