# Commercialization status

| Phase | Status | Notes |
|-------|--------|-------|
| 0 Foundation | COMPLETE | Pre-existing |
| 1.1 BUSINESS_OWNER hardening | COMPLETE | Pre-existing |
| 1.2 Distributed auth rate limiting | COMPLETE | Memory + optional Redis store |
| 1.3 Session revocation | COMPLETE | Refresh rotation/reuse |
| 1.4 Platform admin bootstrap | COMPLETE | Explicit enable flag |
| 1.5 Bank import correctness | COMPLETE | Unique indexes + race-safe handling |
| 1.6 Durable outbox | COMPLETE | SKIP LOCKED worker, AI handler |
| 2 Practice workspace | PARTIAL | Work queue + today; assignment APIs added; team/at-risk views still thin |
| 3 Automated client chase | PARTIAL | Policy API + chase UI; enrollment beyond document requests still expanding |
| 4 Advanced reconciliation | PARTIAL | Invoice confirm creates payment+allocation; multi-invoice bank wizard still thin |
| 5 Invoice-to-cash | READY (UI) | Payment allocation UI, PDF download, email with PDF attachment |
| 6 Accounts receivable | PARTIAL | Summary, ageing SQL, overdue lists; dedicated ageing UI basic in AR hub |
| 7 Payment matching | PARTIAL | Deterministic invoice suggestions in recon service; bank UI explain/split incomplete |
| 8 Automation engine | PARTIAL | Schema only |
| 9 AI financial ops assistant | PARTIAL | Extraction only |
| 10 Integrations | PARTIAL | Schema only |
| 11 Commercial SaaS billing | PARTIAL | Manual billing |
| 12 ROI dashboard | PARTIAL | Assumptions table |
| 13 Enterprise readiness | PARTIAL | Outbox metrics |

See `docs/COMMERCIAL_PRODUCT_READINESS.md` for pilot checklist.
