# API and event contracts

Base URL: `/api/v1`. All non-login endpoints require `Authorization: Bearer <token>`. Tokens last 15 minutes. All mutations except login require an `Idempotency-Key` containing 8–128 characters. Use the same key and identical input after a network failure; a changed body with the same key is a conflict. Keys are scoped to action and user. Rules and case changes require `expectedVersion`.

## Login

```json
{"username":"trader","password":"the generated DEMO_PASSWORD"}
```

`POST /auth/login` returns `accessToken`, `expiresIn`, `user`, and `role`.

## Submit an order

```json
{"portfolioId":"PORTFOLIO_A","instrumentId":"ACME","side":"BUY","quantity":"10","price":"100","currency":"USD"}
```

`POST /orders` returns 202 with the order ID and evaluation state. `GET /orders` reads the projection and supports `status`, instrument search `q`, `page`, and `size` (maximum 100). `GET /orders/{id}` reads authoritative state and audit history. Decimal fields are strings with at most 6 decimal places and 18 significant digits. Quantities and prices must be positive. An order cannot be edited after submission.

`POST /orders/{id}/execute` takes `{"expectedVersion":1}`. It returns 200 after simulation or 202 after triggering fresh evaluation. 409 means state/version conflict; 503 means the compliance verification was unavailable and the order was not executed.

## Evaluations and rules

`GET /evaluations/{id}` returns input, workflow, outcome, reasons, relationship paths, rule/dataset snapshots, selected/completed timestamps, correlation ID, and engine version. `GET /evaluations/{id}/freshness` reports whether the revision pair is still current.

```json
{"name":"New restrictions","restrictedInstruments":["BANNED"],"restrictedIssuers":["BANNED_CORP"],"maxNotional":"100000"}
```

Admin `POST /rulesets` creates an immutable draft; `POST /rulesets/{id}/publish` takes the current configuration's `expectedVersion`. `GET /rulesets` returns drafts, published revisions, and active configuration. Publishing never modifies old evaluation snapshots.

## Investigations

`GET /cases` supports status and pagination. `GET /cases/{id}` includes notes and actions. Reviewer/admin `POST /cases/{id}/assign` takes `expectedVersion` and an optional assignee (admin only for another reviewer). Notes and resolutions require 5–2000 characters.

```json
{"expectedVersion":1,"disposition":"APPROVED","note":"Synthetic order reviewed and approved."}
```

Pre-trade dispositions: `APPROVED`, `REJECTED`. Post-trade dispositions: `CONFIRMED`, `FALSE_POSITIVE`, `DATA_ISSUE`. Pre-trade self-approval is forbidden. Resolved cases are not editable.

## Historical import

Admin `POST /executions/import` takes a JSON object with a `csv` string. CSV uses exactly this header:

```csv
source,externalTradeId,portfolioId,instrumentId,side,quantity,price,executedAt
demo,trade-001,PORTFOLIO_A,BANNED,BUY,10,100,2025-01-01T12:00:00Z
```

Accept 1–100 rows, 100 KB maximum input, unquoted fields only. Whole-file validation precedes persistence. Execution timestamps cannot be in the future. A repeated source/external-trade identifier with changed content returns 409. Response lists execution IDs.

## SSE and recovery

`GET /stream` takes a bearer header and optional numeric `Last-Event-ID`. `refresh` requests a snapshot; `change` contains a sequence and event metadata. Heartbeats keep the stream alive. Tokens never belong in query strings.

Admin `GET /operations/dead-letters` returns up to 100 failed records. `POST /operations/dead-letters/{id}/replay` takes `{}` and preserves the original event ID. `POST /operations/projections/rebuild` takes `{}` and creates a fresh dashboard generation. Both require an idempotency key.

## Events

Envelope schema: `contracts/event-v1.schema.json`. Topics: `tradeguard.trade.v1`, `tradeguard.compliance.v1`, and `tradeguard.case.v1`. Dead-letter topics append `.DLT`. Partition keys are order IDs, or execution IDs for imports. Pre-trade results carry `orderId`, `evaluationId`, `attempt`, `portfolioId`, `traderId`, `outcome`, reasons, and snapshots. Case resolution carries workflow, evaluation ID, disposition, and reviewer identity. Consumers have separate groups and commit after durable processing.
