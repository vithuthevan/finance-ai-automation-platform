# QA Results TC-028 â€“ TC-050

Run: 2026-09-03T18:02:03.7147069+05:30
Environment: local backend :8080, DB finance_platform_qa6

| Case | Result | Notes | Evidence |
|------|--------|-------|----------|
| TC-028 | **Pass** | Accountant created in same firm with FULL client access | id=d1e40e39-985f-48f1-9d61-6a4b79f0fc94 access={"clientId":"485efc67-c2ae-43f7-980f-b2024b9b1030","accessType":"FULL"} |
| TC-029 | **Pass** | Cross-firm client assignment fails; no orphan user | status=404; users before/after=2/2 |
| TC-030 | **Pass** | AUDITOR+FULL coerced to READ_ONLY | access=READ_ONLY |
| TC-031 | **Pass** | ACCOUNTANT+UPLOAD_ONLY rejected | {"detail":"UPLOAD_ONLY is only valid for BUSINESS_OWNER","instance":"/api/v1/users","status":400,"title":"Validation Failed","errorCode":"VALIDATION_FAILED","errors":{"accessType":"UPLOAD_ONLY is only valid for BUSINESS_ |
| TC-032 | **Pass** | Deactivated user cannot auth; historical expense created_by preserved | exp=201/5e41fb9e-44b5-4539-981c-7ceb7fa05b09 hist=1 login=422 approve=200 |
| TC-033 | **Pass** | Reactivate within quota; user can authenticate | react=200 |
| TC-034 | **Pass** | Reactivation blocked over user quota; history preserved | status=422 active=false {"detail":"Your current plan supports up to 2 active users.","instance":"/api/v1/users/d1e40e39-985f-48f1-9d61-6a4b79f0fc94/activate","status":422,"title":"Business Rule Violation","errorCode":"PL |
| TC-035 | **Pass** | Access replaced A->B; no cross-client leakage | getA=403 getB=200 listHasA=False listHasB=True |
| TC-036 | **Pass** | SME client created under firm and visible to admin | id=6da9c99c-e79c-4f6f-bd0a-9e2b369e2307 |
| TC-037 | **Pass** | Extra firmId ignored; client belongs to Firm A | firmId=ddd2d76a-2334-4ed2-be83-ff23c99f55bb |
| TC-038 | **Pass** | Accountant lists only Client A; direct B denied | list=485efc67-c2ae-43f7-980f-b2024b9b1030; getB=403 |
| TC-039 | **Pass** | Business owner denied Client B | create=201 getB=403 |
| TC-040 | **Pass** | Inactive client blocks writes/assignments; history readable | write=422 assign=404 read=200 |
| TC-041 | **Pass** | Client reactivated; new writes work | react=200 write=201 |
| TC-042 | **Pass** | Client reactivation blocked over quota; record preserved | {"detail":"Your current plan supports up to 3 active clients.","instance":"/api/v1/clients/485efc67-c2ae-43f7-980f-b2024b9b1030/activate","status":422,"title":"Business Rule Violation","errorCode":"PLAN_CLIENT_LIMIT_REAC |
| TC-043 | **Pass** | Firm-wide EXPENSE category created and listed | create6100=201 create6200=201 list has6100=True has6200=True |
| TC-044 | **Pass** | Client A-specific category visible to A listing, not B | id=c1a578e9-6405-45f6-bce9-54d415ff353f |
| TC-045 | **Pass** | Firm-wide duplicate code rejected | {"detail":"Category already exists with code: 6100","instance":"/api/v1/categories","status":409,"title":"Duplicate Resource","errorCode":"DUPLICATE_CATEGORY_CODE"} |
| TC-046 | **Pass** | Client-specific duplicate code rejected | {"detail":"Category already exists with code: CA20260903180144","instance":"/api/v1/categories","status":409,"title":"Duplicate Resource","errorCode":"DUPLICATE_CATEGORY_CODE"} |
| TC-047 | **Pass** | Same client-specific code allowed on different clients | A=201 B=201 |
| TC-048 | **Pass** | Self-parent rejected | {"detail":"Category cannot be its own ancestor","instance":"/api/v1/categories/36a302a8-d0eb-469e-9c60-a219625aaafc","status":400,"title":"Validation Failed","errorCode":"VALIDATION_FAILED","errors":{"parentId":"Category |
| TC-049 | **Pass** | Hierarchy cycle rejected | {"detail":"Category cannot be its own ancestor","instance":"/api/v1/categories/1c8bbc59-ba25-4a39-95d2-3719fdf797cc","status":400,"title":"Validation Failed","errorCode":"VALIDATION_FAILED","errors":{"parentId":"Category |
| TC-050 | **Pass** | Inactive category blocks new txns; historical category_id unchanged | new=422 oldCat=36a302a8-d0eb-469e-9c60-a219625aaafc |

## Summary
- Pass: 23
- Fail: 0
- Blocked: 0
- Total: 23
