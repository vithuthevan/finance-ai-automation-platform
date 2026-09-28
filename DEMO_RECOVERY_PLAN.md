# Demo recovery plan

For each risky step: **fallback** so the story continues without a single fragile live action.

---

## Environment / startup

| If | Then |
|----|------|
| App won’t load on :4200 | `docker compose up -d frontend`; or `cd frontend && npm start` after freeing RAM |
| Backend not ready | `curl localhost:8080/api/v1/health/ready`; `docker compose logs backend`; rebuild: `docker compose build backend && docker compose up -d backend` |
| Login fails | Re-run `python demo/seed_demo.py`; confirm `.env` `APP_JWT_SECRET` unchanged since tokens issued |
| Migrations error | `docker compose down -v` + fresh `up` + seed (disposable demo DB only) |

---

## STEP: Month-end command center

| If | Then |
|----|------|
| Page shows “Could not load command center” / 404 API | **Skip command center.** Use **Close** (`/app/close`, September 2026, Refresh) and **My work** (`/app/work`) — same readiness story. |
| Portfolio empty | Re-run seed; confirm logged in as Priya |

---

## STEP: Document review (Keells)

| If | Then |
|----|------|
| No document in inbox | Upload `demo/files/keells-receipt.pdf` manually on Documents |
| Review page won’t accept draft | Enter all required fields manually (date, category, amount, party); AI is off by design |
| Accept fails | Create expense manually on Expenses (5 Sep, Food, 4850, Keells) and approve — say “same outcome, manual entry” |

---

## STEP: Income / expense entry

| If | Then |
|----|------|
| Category missing | Admin → Categories; seed should have Food, Utilities, Café sales |
| Approve button missing | Check filter **Draft**; confirm role ADMIN/ACCOUNTANT |
| Wrong client | Re-select **Cedar Café** in header |

---

## STEP: Bank import

| If | Then |
|----|------|
| Import fails / mapping error | Use default column mapping; file is `demo/files/bank-sept.csv` (Date, Description, Debit, Credit) |
| “Already imported” | Go straight to **Reconciliation** tab — demo still works |
| Preview shows 0 valid rows | Re-select bank account **Cedar Café Operating**; re-upload file |

---

## STEP: Reconciliation

| If | Then |
|----|------|
| No match suggestions | Confirm September **approved** lines exist (4850, 18500, 6200) before import |
| Confirm fails | Match one line live; **say** remaining lines follow same pattern; show **summary %** partial |
| Cannot reach 100% | **Ignore** BANK FEE with reason; show close readiness still works if recon rules satisfied |

**Prepared state:** If recon already done in rehearsal, open Banking with recon at 100% and **narrate** import step without re-uploading.

---

## STEP: Close blocker (document request)

| If | Then |
|----|------|
| No blocker shown | Seed open request: re-run `python demo/seed_demo.py` or create request from close workspace |
| Owner login empty | Use **Option B:** Admin **Cancel** request on `/app/requests` |
| Owner upload fails | Priya **Mark complete** only if status UPLOADED; else cancel request |
| Close still disabled | Refresh workspace; check reconciliation % and open requests list |

---

## STEP: Close period

| If | Then |
|----|------|
| Close button disabled | **Demo close-readiness only:** explain gates, do not force close |
| Close errors | Read blocker list aloud; fix one blocker (cancel request) and retry |
| Period already closed | Switch narrative to **closed-period protection** — try new expense in September (should fail) |

---

## STEP: Reports

| If | Then |
|----|------|
| P&L zero | Run for September 2026, client Cedar; ensure approvals done |
| Trends empty | Mention August seed history; use P&L only |
| Report error | Show Expenses/Income list totals as backup |

---

## STEP: Work queue

| If | Then |
|----|------|
| My work empty | Normal if queue cleared; use **Close** grid counts instead |

---

## Nuclear option (2-minute story)

Login → **Close** September → show **blocked** client → **Banking** recon screen at partial or 100% → **Reports** screenshot from rehearsal → offer technical deep-dive after meeting.

Keep `demo/verify_demo_state.py` output in a slide or note as last-resort proof of API health.
