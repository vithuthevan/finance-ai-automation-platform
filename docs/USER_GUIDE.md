# Finance Platform — New User Guide (A–Z)

Accountant-first **Document-to-Close** workspace for bookkeeping firms.

```
Collect → Extract → Review → Approve → Reconcile → Close → Report
```

This guide walks through every product section a new user will see, what it is for, and what to do first.

---

## 1. Who you are (roles)

What you see in the left sidebar depends on your role.

| Role | Typical job | Main areas |
|------|-------------|------------|
| **ADMIN** | Firm owner / practice manager | Everything: clients, users, settings, subscription, full bookkeeping |
| **ACCOUNTANT** | Day-to-day bookkeeping | My work, documents, expenses, income, banking, close, reports |
| **AUDITOR** | Read-only oversight | Clients, documents, ledger views, banking/close (view), reports, audit log |
| **BUSINESS_OWNER** | Client of the firm | Home (requests), my documents, financial summary (read-only); cannot create or approve ledger entries |
| **Upload-only access** | Client staff who only send files | Requested documents + my documents |

Client access types (`FULL`, `READ_ONLY`, `UPLOAD_ONLY`) never exceed your role. Accountants only work on clients they are assigned to.

---

## 2. First-time setup (firm ADMIN)

Do this once before daily work starts.

1. **Register the firm** on the Register page (name, your admin account).
2. **Sign in** on Login — registration does not log you in automatically.
3. Open **Firm settings** → set currency, timezone, financial year start, and whether AI extraction is on.
4. Open **Categories** → confirm or adjust expense/income categories.
5. Open **Users** → invite accountants, auditors, and business owners.
6. Open **Clients** → create each client business and keep them Active.
7. Assign accountants to clients (primary accountant routes notifications and workload).
8. Optionally check **Subscription** for plan, trial, and usage limits.

Then the daily loop is: documents in → review → approve → bank match → close → report.

---

## 3. Sign-in, profile, and notifications

### Login / Register
- Create a firm once, then use **Login** every day.
- If you land on **Unauthorized**, your role cannot open that page — use the sidebar or ask an admin.

### Profile (`/app/profile`)
- View your account details.
- Adjust **notification preferences** (in-app / email where available).

### Notification bell (toolbar)
- Unread count for work that needs you (uploads, failed AI, document requests, bank imports, period ready/closed).
- Open **Notifications** for full history and mark-as-read.
- ADMIN is not flooded with every receipt upload; AUDITOR does not get draft bookkeeping noise.

---

## 4. Section-by-section guide

### Dashboard
**Who:** ADMIN, ACCOUNTANT, AUDITOR (ledger roles)

Your morning home screen.

- **Today** cards (ADMIN/ACCOUNTANT): documents to review, approvals pending, bank items unresolved, overdue client requests, clients ready to close — each opens the matching **My work** queue.
- **Practice overview**: active clients, drafts, review backlog.
- **Selected client this month**: income, expenses, profit/loss, drafts, unlinked/unreviewed documents.

**Tip:** Start here, click the largest number, clear that queue first.

---

### My work
**Who:** ADMIN, ACCOUNTANT

Actionable queue derived from real bookkeeping state (not a separate to-do app).

| Queue type | Meaning | Typical next step |
|------------|---------|-------------------|
| Document review | Receipts waiting in review | Open document review |
| Processing failures | AI/extraction failed | Fix manually or re-handle |
| Approvals | Draft expenses/income | Approve or correct |
| Document requests | Open / uploaded / overdue asks to clients | Remind, complete, or follow up |
| Bank reconciliation | Unmatched / suggested / pending approval lines | Banking section |
| Period close | Clients ready for month-end | Close section |

Also shows a **client portfolio** snapshot (review, bank %, close status). Filter by type, then **Open** the item.

---

### Clients
**Who:** ADMIN, ACCOUNTANT, AUDITOR

Master list of businesses your firm serves.

- ADMIN: create clients (name, contact email), activate / deactivate.
- Everyone with access: pick clients elsewhere (documents, banking, close, reports) from this list of active relationships.
- Assign a **primary accountant** so uploads and work route to the right person.

**Do first:** Create clients before uploading documents or importing bank CSVs.

---

### Documents (Document inbox)
**Who:** Roles that can upload or review; business owners see **My documents**

Central place for evidence files.

1. Choose **client**, **document type** (receipt, purchase/sales invoice, credit note, bank statement, other), optional note.
2. Upload PDF / image / CSV as allowed.
3. If AI is enabled and configured, extraction may run; otherwise review stays manual.
4. Open a row to enter **Document review**.

**Document review** (per file):

- Check extracted or suggested fields (vendor, date, amount, category).
- Correct values, link to an expense/income draft, or accept into the books workflow.
- Failed extraction: enter data manually and still link to a transaction.

Statuses that block month-end close include documents still needing review, processing, or extracted-but-unresolved — plus unlinked financial document types.

---

### Expenses
**Who:** Ledger roles; business owners see approved view only

Expense ledger for a client.

- Create or edit **draft** expenses (category, date, amount, notes).
- Attach / link supporting documents.
- **Approve** when ready — approved amounts feed reports and bank matching.
- **Void** incorrect approved items (void does not block close the way drafts do).

Drafts in an open period **block** month-end close until approved or cleared.

---

### Income
**Who:** Same as Expenses

Mirror of Expenses for sales / other income.

- Draft → approve → appears in P&L and bank credit matching.
- Same closed-period rules: you cannot change books for a **CLOSED** month without ADMIN reopen.

---

### Banking
**Who:** ADMIN, ACCOUNTANT, AUDITOR (operate vs view by role/access)

Answers: *does the bank statement agree with the books?*

**Tabs / steps:**

1. **Accounts** — add bank name, account name, masked number, currency (no online banking passwords stored).
2. **Import** — upload bank **CSV**, map columns (date, description, reference, debit, credit, balance), preview, then commit. Identical files and duplicate lines are skipped.
3. **Reconcile** — review statuses:

| Status | Meaning |
|--------|---------|
| Unmatched | Needs attention |
| Suggested | Candidate match — confirm or reject |
| Matched | Confirmed against approved expense/income |
| Pending approval | Draft created from a bank line — approve in Expenses/Income, then confirm |
| Ignored | Non-bookkeeping line (reason required) |

You can confirm suggestions, create drafts from bank lines, request supporting documents, or ignore with a reason. High confidence never auto-finalizes — the accountant confirms.

Unresolved bank lines in a period with imported data **block** close.

---

### Close (Month-end close)
**Who:** ADMIN, ACCOUNTANT (operate); AUDITOR (view)

Answers: *can we confidently say this client's month is complete?*

1. Pick client + calendar month.
2. Run / view **readiness** — blockers vs warnings.
3. Clear blockers from Documents, Expenses, Income, Banking, and document requests.
4. Move through review → **Close** (optional note).
5. Only **ADMIN** can **Reopen** with a required reason.

**Typical blockers:** draft expenses/income, documents needing review, failed unlinked AI docs, unlinked financial documents, open/uploaded document requests, unmatched/suggested/pending bank lines.

**Warnings (do not block):** approved transactions without a document; bank account with no statement imported.

After close, financial writes for dates in that period are blocked. Linking extra evidence to an already approved transaction (without changing amounts) may still be allowed.

A closed period is a **finalized bookkeeping period**, not a legal audit certificate.

---

### Reports
**Who:** Ledger roles; business owners see a simpler **Summary**

- **Profit & Loss** — income and expenses by category for a date range (approved only; drafts/voids excluded).
- Drill into category totals → income/expense detail reports.
- **Trends** — period comparisons where available.
- Export CSV / XLSX from report filters.
- Business owners: approved totals only, no practice close tooling.

Use reports after approvals (and ideally after close) so numbers are stable.

---

### Users *(ADMIN)*
Invite and manage firm staff.

- Create users: name, email, password, role (Accountant, Auditor, Business owner).
- Activate / deactivate accounts.
- Pair with client access so accountants and owners only see the right businesses.

---

### Categories *(ADMIN)*
Chart of categories used when coding expenses and income.

- Keep codes/names clear for the practice.
- Changing categories does not rewrite history by itself — use consistently going forward.
- Needed before clean review and P&L reporting.

---

### Firm settings *(ADMIN)*
Practice configuration (not billing credentials).

| Area | What you set |
|------|----------------|
| General | Firm name, currency, timezone |
| Accounting | Financial year start month |
| Automation | AI assistance on/off |
| Link | Jump to Subscription |

Currency/timezone changes do **not** rewrite past transactions or timestamps. Turning AI off still allows upload, manual review, banking, and reports.

---

### Subscription *(ADMIN)*
Plan, trial, and usage — not card payment in the app today.

- See current plan (e.g. Starter / Practice / Professional), status (trial, active, suspended…).
- Usage meters: clients, users, documents/month, AI/month, storage.
- Warnings appear in the toolbar/dashboard near plan limits.
- Request a plan change from this page when needed.
- If suspended/inactive: existing data stays **read-only**; uploads and many writes are blocked.

---

### Audit log *(ADMIN, AUDITOR)*
Who did what, when.

- Use for oversight, investigations, and reopen/close history.
- Read-only; scoped to your firm and client access rules.

---

### Owner home / Requested documents
**Who:** BUSINESS_OWNER; upload-only users

Client-facing home.

1. Select **business** (client).
2. **Documents needed** — open requests from your accountant (type, due date, description). Upload the file; status becomes uploaded until the accountant accepts/completes.
3. **Completed requests** — history.
4. Business owners can also **upload other documents** (receipt, invoice, bank statement) and see **approved totals this month**.
5. Upload-only users stay focused on fulfilling requests and their document list.

There is no chat — communication is structured document requests and reminders.

---

### Platform admin *(platform operators only)*
Separate area (`/platform`) to manage firms at SaaS level. Ordinary firm users ignore this section.

---

## 5. Recommended daily / monthly rhythm

### Daily (accountant)
1. Dashboard / **My work**
2. Clear document review and processing failures
3. Approve drafts
4. Work bank unmatched items when statements are in
5. Send or complete document requests

### Month-end
1. Import remaining bank CSVs and reconcile
2. Clear all close blockers
3. Review readiness warnings
4. **Close** the period
5. Run / export **Reports** for the client

### Client (business owner)
1. Check Home for **Documents needed**
2. Upload on time (overdue requests get reminders)
3. Optionally upload extra receipts
4. Check Summary / approved expenses & income when needed

---

## 6. Quick map: sidebar → purpose

| Sidebar label | Purpose in one line |
|---------------|---------------------|
| Dashboard | Practice & client health today |
| My work | Personal actionable queue |
| Clients | Businesses you bookkeep |
| Documents | Upload and review evidence |
| Expenses | Expense drafts and approvals |
| Income | Income drafts and approvals |
| Banking | CSV import and reconciliation |
| Close | Month-end readiness and lock |
| Reports / Summary | P&L and exports |
| Users | Staff accounts |
| Categories | Coding taxonomy |
| Firm settings | Currency, timezone, AI switch |
| Subscription | Plan and usage |
| Audit log | Compliance trail |
| Home / Requested documents | Client upload workspace |
| Profile | You + notification prefs |
| Notifications | Alert history |

---

## 7. Common first-week checklist

- [ ] Firm registered and ADMIN signed in  
- [ ] Firm settings and categories set  
- [ ] At least one accountant user created  
- [ ] First client created and accountant assigned  
- [ ] Business owner invited (if clients upload themselves)  
- [ ] Sample receipt uploaded and reviewed  
- [ ] Draft expense approved  
- [ ] Bank account added and one CSV imported  
- [ ] One match confirmed  
- [ ] Close readiness checked for the current month  
- [ ] P&L opened for the same client  

---

## Related docs

- Practice workflow (queues & notifications): [PRACTICE_WORKFLOW.md](PRACTICE_WORKFLOW.md)
- Banking detail: [BANK_RECONCILIATION.md](BANK_RECONCILIATION.md)
- Month-end close detail: [CLOSE.md](CLOSE.md)
- Firm settings: [FIRM_SETTINGS.md](FIRM_SETTINGS.md)
- Subscriptions: [SAAS_SUBSCRIPTIONS.md](SAAS_SUBSCRIPTIONS.md)
- Security overview: [SECURITY.md](SECURITY.md)
