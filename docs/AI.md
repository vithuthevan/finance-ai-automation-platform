# AI and OCR configuration

AI is assistive. It never writes approved books.

```
Document → extraction facts → accounting suggestion → accountant review → DRAFT → accountant approval
```

## Disabled / manual mode

Default:

```
APP_AI_ENABLED=false
DOCUMENT_EXTRACTION_PROVIDER=none
```

Upload, inbox, manual draft creation, approval, and Phase 3 reporting continue to work. Documents go to `NEEDS_REVIEW` for manual entry.

`APP_AI_PROVIDER=mock` produces filename-only suggestions for local demos. It is not OCR.

## Enable OpenAI-compatible extraction

```
APP_AI_ENABLED=true
DOCUMENT_EXTRACTION_PROVIDER=openai
ACCOUNTING_AI_PROVIDER=openai
APP_AI_API_KEY=replace-me
AI_MODEL=gpt-4o-mini
APP_AI_TIMEOUT_SECONDS=45
```

Supported automatic extraction types:

- RECEIPT
- PURCHASE_INVOICE
- SALES_INVOICE
- INVOICE
- CREDIT_NOTE

`BANK_STATEMENT`, `OTHER`, and `BANK_SLIP` stay manual. Bank-statement parsing is a later phase.

Images (JPEG/PNG/WebP) may be sent to a vision-capable model. PDFs are not treated as scanned OCR in V1; metadata/filename context is used instead.

The firm setting **AI extraction enabled** can disable processing per firm even when the server is configured.

## Human approval rule

Accepting a suggestion creates a **DRAFT** expense or income (`source=AI`). Existing Phase 1 approval is still required before reports include the amount.

Rejecting a suggestion does **not** reject the document.

## Credentials

Keys come from environment variables only. They are never returned by the API or stored in audit logs.
