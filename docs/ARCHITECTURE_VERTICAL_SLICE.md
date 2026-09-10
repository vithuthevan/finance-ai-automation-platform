# Architecture Mastery — Login & Document Upload Vertical Slice

Teaching walkthrough of the two paths every engineer should internalize first.
Read this with the cited files open. Ask at every step: *what invariant is being protected?*

---

## 1. Login (identity becomes a tenant context)

### What / Why

**What:** Exchange email + password for an access JWT and a refresh token.  
**Why:** Every later API call must know *who* you are and *which firm* you belong to without storing a server session.

### How (exact path)

1. UI — [`frontend/src/app/features/auth/login.page.ts`](../frontend/src/app/features/auth/login.page.ts)  
   User submits → `AuthService.login(email, password)`.

2. Client — [`frontend/src/app/core/auth/auth.service.ts`](../frontend/src/app/core/auth/auth.service.ts)  
   `POST /api/v1/auth/login` → `persist(session)` writes `fp.session` to `localStorage` (access + refresh).

3. API — [`LoginController.login`](../backend/platform-app/src/main/java/com/finance/platform/controller/auth/LoginController.java)  
   → [`AuthenticationService.login`](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/AuthenticationService.java)

4. Rate limit — `AuthRateLimiter.checkAllowed("login:" + email)` (in-process, login only).

5. Password check — Spring `AuthenticationManager` + BCrypt via `DaoAuthenticationProvider`.  
   Failures audit as `LOGIN_FAILURE` (independent TX so they survive rollback).

6. Tokens  
   - Access: [`JwtTokenProvider.generateAccessToken`](../backend/module-auth/src/main/java/com/finance/platform/auth/infrastructure/security/JwtTokenProvider.java) — HS256 claims `sub`, `firmId`, `role`, `email`, `jti`, ~1h.  
   - Refresh: [`SessionService.issueRefreshToken`](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/SessionService.java) — random 32 bytes, **SHA-256 stored**, 14 days; raw token returned once.

7. Later requests — [`JwtAuthenticationFilter`](../backend/module-auth/src/main/java/com/finance/platform/auth/infrastructure/security/JwtAuthenticationFilter.java):  
   parse JWT → **reload user from DB** → require active + `user.firmId == claim.firmId` → set Spring Security + `TenantContextHolder`.

### Alternatives & trade-offs

| Approach | Trade-off |
|----------|-----------|
| Stateless JWT (chosen) | Scale horizontally easily; logout cannot instantly kill access token |
| Server sessions | Instant revoke; needs sticky sessions or shared session store |
| Trust JWT role claim alone | Faster, but stale/role-change unsafe — this app reloads DB role (good) |

### What can fail

- Bad password → generic “Invalid credentials” (no user enumeration in message).  
- Deactivated user → filter leaves request unauthenticated → 401.  
- Stolen `localStorage` → attacker has access until expiry + refresh until logout rotates/revokes refresh.  
- Access token after logout → still valid until `exp` (refresh is revoked only).

### Senior takeaway

Authentication proves identity; **authorization and tenancy are re-derived from the database on every request**. The JWT is a capability ticket, not the source of truth for permissions.

### Refresh-on-401 (Priority 0)

[`auth.interceptor.ts`](../frontend/src/app/core/interceptors/auth.interceptor.ts) catches 401, calls `AuthService.refreshSession()` (single in-flight refresh), retries once with `X-Auth-Retry: 1`, or clears session and sends the user to `/login`.

---

## 2. Document upload (command + async reaction)

### What / Why

**What:** Attach a receipt/invoice file to a client.  
**Why:** Bookkeeping starts from evidence; typed ledger rows come later (manual or AI-assisted drafts).

### How (exact path)

```
documents.page.upload(allowDuplicate)
  → ApiService.upload POST /api/v1/clients/{clientId}/documents
  → JwtAuthenticationFilter + TenantContext
  → DocumentController.upload @PreAuthorize(ADMIN|ACCOUNTANT|BUSINESS_OWNER)
  → DocumentService.upload @Transactional
       requireUploadAccess
       validate file / quota / checksum duplicate
       FileStorageService.store
       Receipt INSERT (UPLOADED)
       DocumentUploadedEvent + audit DOCUMENT_UPLOADED
  → COMMIT
  → DocumentUploadedListener @Async AFTER_COMMIT
       DocumentAiProcessor → DocumentAiPersistenceService (REQUIRES_NEW)
  → 201 DocumentResponse → UI message + inbox refresh
```

Key files:

- UI: [`documents.page.ts`](../frontend/src/app/features/documents/documents.page.ts)  
- Controller: [`DocumentController`](../backend/platform-app/src/main/java/com/finance/platform/controller/finance/DocumentController.java)  
- Service: [`DocumentService.upload`](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/DocumentService.java)  
- Listener: [`DocumentUploadedListener`](../backend/module-ai/src/main/java/com/finance/platform/ai/application/DocumentUploadedListener.java)

### Invariants protected

1. **Tenant + ACL** — `ClientAccessService.requireUploadAccess` (firm membership, not READ_ONLY, not AUDITOR).  
2. **File safety** — extension allowlist, MIME sniffing, size limits, server-generated `storageKey`.  
3. **Dedup** — SHA-256 per firm+client; `allowDuplicate` opt-in.  
4. **AI distrust** — upload succeeds even if AI fails; AI never auto-approves (accept creates DRAFT only).  
5. **TX ordering** — AI runs `AFTER_COMMIT` so failed uploads never trigger extraction.

### Alternatives & trade-offs

| Design | Why chosen / not |
|--------|------------------|
| Sync OCR in request | Would make uploads slow and timeout-prone |
| Outbox table for AI enqueue | Schema exists (V25); live path still uses Spring events — crash after commit can lose AI kick |
| Store file after DB insert | Orphan DB rows pointing at missing blobs; current code stores first, deletes blob if persist fails |

### What can fail

- Storage up, DB down mid-persist → cleanup deletes blob (best effort).  
- DB committed, process dies before async listener → document stuck until `retry-processing`.  
- Duplicate click → new checksum same → `DuplicateDocumentException` unless `allowDuplicate`.  
- Quota exceeded → business error before store.

### Senior takeaway

Separate **user-visible command** (persist document) from **best-effort reaction** (AI). Money never moves in this step — only evidence and draft suggestions later.

---

## Study checklist

- [ ] Trace one login in debugger from `LoginPage.submit` to `TenantContextHolder.set`  
- [ ] Trace one upload and confirm AI does not run if you force-rollback before commit  
- [ ] Confirm accepting a suggestion creates `DRAFT`, not `APPROVED`  
