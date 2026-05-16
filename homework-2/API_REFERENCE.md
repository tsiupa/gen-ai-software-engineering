# API Reference

> Audience: clients consuming the Support Tickets REST API.

- **Base URL** (local): `http://localhost:8080`
- **Content type**: `application/json` (snake_case fields on the wire)
- **Date/time**: ISO-8601 UTC instants (e.g. `2026-05-16T12:34:56.789Z`)

---

## Endpoints at a glance

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/tickets` | Create a single ticket |
| `POST` | `/tickets/import` | Bulk import from CSV / JSON / XML |
| `POST` | `/tickets/{id}/auto-classify` | Re-classify an existing ticket |
| `GET` | `/tickets` | List tickets (with filters) |
| `GET` | `/tickets/{id}` | Fetch a single ticket |
| `PUT` | `/tickets/{id}` | Update a ticket (full replace) |
| `DELETE` | `/tickets/{id}` | Delete a ticket |

---

## Data Model

### `Ticket` (response)

```json
{
  "id": "UUID",
  "customer_id": "string",
  "customer_email": "string (RFC 5322 email)",
  "customer_name": "string",
  "subject": "string (1-200 chars)",
  "description": "string (10-2000 chars)",
  "category": "account_access | technical_issue | billing_question | feature_request | bug_report | other",
  "priority": "urgent | high | medium | low",
  "status": "new | in_progress | waiting_customer | resolved | closed",
  "created_at": "datetime",
  "updated_at": "datetime",
  "resolved_at": "datetime (nullable)",
  "assigned_to": "string (nullable)",
  "classification_confidence": "number 0..1 (present when auto-classified, cleared on manual override)",
  "tags": ["array of string"],
  "metadata": {
    "source": "web_form | email | api | chat | phone",
    "browser": "string",
    "device_type": "desktop | mobile | tablet"
  }
}
```

### `TicketRequest` (POST / PUT body)

Same shape as response, minus server-managed fields (`id`, timestamps, `classification_confidence`).
Validation rules:

| Field | Rule |
|---|---|
| `customer_id` | required, non-blank |
| `customer_email` | required, valid email |
| `customer_name` | required, non-blank |
| `subject` | required, 1–200 chars |
| `description` | required, 10–2000 chars |
| `category` | required *unless* `auto_classify=true` |
| `priority` | required *unless* `auto_classify=true` |
| `status` | optional, defaults to `new` |
| `tags`, `metadata`, `assigned_to` | optional |

---

## Endpoints

### POST `/tickets`

Create a single ticket.

**Query parameters**

| Name | Type | Default | Description |
|---|---|---|---|
| `auto_classify` | bool | `false` | If `true`, run the classifier and fill in `category`/`priority` from the subject + description. |

**Status codes**

- `201 Created` — body contains the saved ticket
- `400 Bad Request` — validation failed, malformed JSON, or `auto_classify=false` with missing category/priority

**cURL**

```bash
curl -X POST "http://localhost:8080/tickets" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "C-1",
    "customer_email": "alice@example.com",
    "customer_name": "Alice",
    "subject": "Cannot login",
    "description": "Cant access my account; this is critical.",
    "category": "account_access",
    "priority": "urgent"
  }'
```

**Response (201)**

```json
{
  "id": "a4f4b65f-...",
  "customer_id": "C-1",
  "customer_email": "alice@example.com",
  "customer_name": "Alice",
  "subject": "Cannot login",
  "description": "Cant access my account; this is critical.",
  "category": "account_access",
  "priority": "urgent",
  "status": "new",
  "created_at": "2026-05-16T12:00:00Z",
  "updated_at": "2026-05-16T12:00:00Z",
  "tags": []
}
```

With `auto_classify=true` you may omit `category`/`priority`:

```bash
curl -X POST "http://localhost:8080/tickets?auto_classify=true" \
  -H "Content-Type: application/json" \
  -d '{"customer_id":"C-1","customer_email":"a@b.com","customer_name":"Alice",
       "subject":"Production down","description":"security incident, critical."}'
```

The response will additionally include `classification_confidence`.

---

### POST `/tickets/import`

Bulk import. Accepts `multipart/form-data` with a single `file` part.

**Form fields**

| Field | Type | Description |
|---|---|---|
| `file` | file | CSV, JSON, or XML payload. Format inferred from filename extension or `Content-Type`; override with `format` query param. |

**Query parameters**

| Name | Type | Default | Description |
|---|---|---|---|
| `format` | enum (`csv\|json\|xml`) | inferred | Force the parser. |
| `auto_classify` | bool | `false` | Apply the classifier to each record. |

**Status codes**

- `201 Created` — every record imported successfully
- `207 Multi-Status` — some records failed validation; body has per-record errors
- `400 Bad Request` — file is malformed, unknown format, or file part missing

**Response body** (`ImportResult`)

```json
{
  "total_records": 10,
  "successful": 9,
  "failed": 1,
  "created_ids": ["uuid", "..."],
  "errors": [
    { "record_index": 4, "message": "customerEmail: must be a valid email" }
  ]
}
```

**cURL**

```bash
curl -X POST "http://localhost:8080/tickets/import?auto_classify=true" \
  -F "file=@sample_tickets.json"
```

CSV layout expected:

```
customer_id,customer_email,customer_name,subject,description,category,priority,tags,source,browser,device_type
```

- `tags` are split on `;` (CSV-safe separator).
- `category`, `priority`, `status` are case-insensitive on input.

XML layout expected:

```xml
<tickets>
  <ticket>
    <customer_id>C-1</customer_id>
    ...
    <tags><tag>foo</tag><tag>bar</tag></tags>
    <metadata><source>web_form</source>...</metadata>
  </ticket>
</tickets>
```

---

### POST `/tickets/{id}/auto-classify`

Run the classifier against an existing ticket's `subject + description`, persist the result (`category`, `priority`, `classification_confidence`), return the classification.

**Status codes**

- `200 OK` — body is the `ClassificationResult`
- `404 Not Found` — ticket does not exist

**Response body** (`ClassificationResult`)

```json
{
  "category": "account_access",
  "priority": "urgent",
  "confidence": 1.0,
  "reasoning": "category account_access matched on [login, password]; priority urgent matched on [critical]",
  "keywords_found": ["login", "password", "critical"]
}
```

**cURL**

```bash
curl -X POST "http://localhost:8080/tickets/$ID/auto-classify"
```

---

### GET `/tickets`

List tickets, optionally filtered. All filters AND together; omitted filters match everything.

**Query parameters**

| Name | Type | Description |
|---|---|---|
| `category` | enum | Exact match |
| `priority` | enum | Exact match |
| `status` | enum | Exact match |
| `customer_id` | string | Exact match |
| `assigned_to` | string | Exact match |

**Status codes**

- `200 OK` — returns an array of tickets (possibly empty)
- `400 Bad Request` — an enum filter received an invalid value

**cURL**

```bash
curl "http://localhost:8080/tickets?priority=urgent&category=account_access"
```

---

### GET `/tickets/{id}`

**Status codes**

- `200 OK` — full ticket
- `404 Not Found` — id does not exist

```bash
curl "http://localhost:8080/tickets/$ID"
```

---

### PUT `/tickets/{id}`

Full-replacement update. Same body shape and validation rules as `POST /tickets` (without `auto_classify`).

- `category` and `priority` are required.
- Changing `category` or `priority` **clears `classification_confidence`** — the field is treated as a manual override.
- Setting `status: "resolved"` (from any other state) sets `resolved_at` to "now".

**Status codes**

- `200 OK` — body is the updated ticket
- `400 Bad Request` — validation failed
- `404 Not Found` — id does not exist

```bash
curl -X PUT "http://localhost:8080/tickets/$ID" \
  -H "Content-Type: application/json" \
  -d '{...same shape as POST body...}'
```

---

### DELETE `/tickets/{id}`

**Status codes**

- `204 No Content`
- `404 Not Found`

```bash
curl -X DELETE "http://localhost:8080/tickets/$ID"
```

---

## Error Response Format

Any `4xx` / `5xx` response shares the same envelope:

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Request payload failed validation",
  "timestamp": "2026-05-16T12:34:56.789Z",
  "field_errors": [
    { "field": "customerEmail", "message": "customer_email must be a valid email" }
  ]
}
```

`field_errors` is only present for body-validation failures.

| HTTP | `error` | Trigger |
|---|---|---|
| 400 | `Validation Failed` | Bean Validation on `@Valid` body |
| 400 | `Bad Request` | Malformed JSON / missing required @PathVariable / type mismatch / illegal argument |
| 400 | `Invalid Import File` | CSV/JSON/XML parser threw `ImportFormatException` |
| 404 | `Not Found` | `TicketNotFoundException` |
| 207 | *(no error body; success envelope)* | Bulk import partial success |
| 500 | `Internal Server Error` | Anything else |

---

## Enum Reference

| Enum | Values |
|---|---|
| `category` | `account_access`, `technical_issue`, `billing_question`, `feature_request`, `bug_report`, `other` |
| `priority` | `urgent`, `high`, `medium`, `low` |
| `status` | `new`, `in_progress`, `waiting_customer`, `resolved`, `closed` |
| `metadata.source` | `web_form`, `email`, `api`, `chat`, `phone` |
| `metadata.device_type` | `desktop`, `mobile`, `tablet` |

All enums are case-insensitive on input; output is always lowercase.

---

<sub>This document was drafted with Claude Opus 4.7 (technical-writing pass).</sub>
