# Commercial demo reset

Use this after a live demo or when portfolio/readiness states drift.

## Quick reset (recommended)

```bash
# From repo root — backend must be up on http://localhost:8080
python demo/seed_demo.py
```

Idempotent: re-applies Harbor Ledger firm, Cedar hero client, open rent request, optional Keells inbox doc, and **extra portfolio clients** (Green Leaf, Ocean Traders, ABC Engineering, Harbor Retail).

## Full clean environment

1. Drop and recreate the demo database (or use a disposable Postgres volume).  
2. Start backend — Flyway runs migrations (no new migrations in this sprint).  
3. Run `python demo/seed_demo.py`.  
4. Clear browser storage / use private window for login tests.

## Verify after reset

```bash
python demo/verify_demo_state.py
```

Manual smoke:

- Admin → **Month-end** — at least 2 clients, mixed states  
- Owner → open request visible  
- **Users** → owner has client access  

## Change from prior demo docs

- Portfolio seed adds clients beyond Cedar for Command Center demo.  
- User client access can be set in **Users** UI (Postman no longer required for pilot narrative).  
- Default categories created on **firm registration** (existing firms: run seed or create categories once).

See also: `demo/README.md`, `FROM_SCRATCH_DEMO_RESET_GUIDE.md`.
