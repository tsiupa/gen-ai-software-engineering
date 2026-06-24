---
description: FinTech / banking rules for AI-assisted edits in this repo. Loaded automatically.
globs: ["**/*.java", "**/*.kt", "**/*.sql", "**/*.yaml", "**/*.yml"]
alwaysApply: true
---

# Cursor Rules — FinTech / Banking

This file steers AI edits in this repository. It mirrors the more detailed `agents.md` and `specification.md`; if Cursor cannot load those, these are the load-bearing rules. **When in doubt, ask — do not guess.**

---

## Stack defaults (do not deviate without asking)

- Java 21, Spring Boot 3.x, Gradle (Kotlin DSL).
- PostgreSQL + Spring Data **JDBC** (not JPA). Migrations: Flyway, forward-only (`V<seq>__<name>.sql`). Never edit a shipped migration.
- Kafka via **transactional outbox** — no direct DB+Kafka dual-write.
- Redis for idempotency cache and rate limits. Distributed locks are a last resort.
- Testing: JUnit 5, AssertJ, Testcontainers (real Postgres/Kafka/Redis — do not mock these), Pact contracts against partner services, jqwik for property tests, k6 for perf.
- Observability: OpenTelemetry, structured JSON logs (no PII / PAN in labels).

## Money — always

- Integer **minor units** (`long`). Never `float` / `double`. Never strings in computation.
- Percentages as **basis points** (integer 0–10000). Sum must be exactly 10000.
- Per-holder split: `Math.floorDiv(amount * basis_points, 10000L)`. Remainder cents go to the **proposer** ("remainder absorber" rule).
- Rounding mode: `RoundingMode.HALF_EVEN`.
- API/event shape for money: `{ "amount_minor_units": <int>, "currency": "<ISO 4217>" }`. Enforced via a single JSON converter — do not roll a new shape per endpoint.

## PCI-DSS — never

Never write code that **reads, stores, logs, returns, traces, metric-labels, or publishes** any of: PAN (full card number), CVV/CVC2, PIN, track data, magstripe data. Only `pan_last4` may leave the tokenization-vault adapter. A Logback filter scrubs 13–19-digit runs — **do not disable or weaken it**. If a proposed edit looks like it would touch raw PAN outside the vault adapter, **stop and ask**.

## PII

- Phone numbers: HMAC for lookup + AES ciphertext for display. Raw E.164 is never in plaintext columns.
- API responses: `pan_last4`, `phone_masked`, `display_name` only. No other holder's balance.
- Customer-facing ineligibility reasons collapse to `RECIPIENT_NOT_ELIGIBLE` (no enumeration of blocked/sanctioned/KYC-stale).
- Sanctions-screening outcomes are **Compliance-only** — never returned to the customer.

## Idempotency — required on every write

- `Idempotency-Key` (UUID v4) is required on every `POST`/`PUT`/`DELETE`.
- 24 h replay window. Same key + same payload → original response; same key + different payload → `409 IDEMPOTENCY_KEY_REUSED`.
- Internal idempotency keys for downstream calls (e.g., account reserves) must be **deterministic** — e.g., `"{auth_id}:{holder_idx}"`. Never `UUID.randomUUID()` inside a handler.

## Concurrency

- Optimistic locking (`version` column) on aggregates. Loser → `409`.
- Charge posting is **two-phase: reserve → commit**, 30 s reservation TTL. No partial postings ever. If any holder reserve fails, release all and decline.

## State machines

- Implement aggregate state machines as **sealed** types + exhaustive `switch`. Invalid transitions → `409 INVALID_STATE_TRANSITION` echoing `current_state`. Never silently coerce.

## Audit (banking ledger of record)

- Every state-changing path writes ≥1 `*_audit_event` row with `(actor_user_id, actor_ip, actor_device_id, correlation_id, before_state, after_state, reason_code)`.
- Append-only + hash-chained (`hash = H(prev_hash || payload)`). Daily KMS signature on chain head. **Never** add a write path that bypasses the chain.
- GDPR Art. 17: **pseudonymize**, do not delete audit rows. Retention 7 years.

## Edge-case defaults (apply without asking)

- Insufficient funds at any holder → **decline whole authorization** (never partial). Release all reserves.
- Currency mismatch → reject at validation (FX is out of scope).
- Replay of any state-changing call → idempotent (no double-charge, no double-notify).
- Partial reversal from scheme → apply against the **original charge's snapshot**, not the current split.
- Notification delivery failure → retry; **never** block the underlying state change. Audit log is the source of truth.
- Hash-chain integrity failure → halt that card's audit writes, page on-call; **never** silently re-link.
- Phone reassignment → confirmation tokens are bound to `user_id`, not phone; old user's pending decisions auto-`DECLINED`.

## Style

- Records for DTOs, sealed types for state machines, pattern-matching `switch`, `var` only when type is obvious.
- Aggregates are **pure** (no Spring beans inside). I/O lives at adapter/service edges.
- Files ≤ ~400 LOC, methods ≤ ~50 LOC, cyclomatic complexity ≤ 10.
- No wildcard imports. Static imports only for AssertJ/JUnit in tests.
- Errors: RFC 7807 problem details with stable `type` URIs documented in OpenAPI.

## Tests are part of the change

- Coverage gate ≥ 85% line / 80% branch on new packages.
- Every documented edge case in `specification.md` §7 that touches the changed code has a test.
- **Do not** suppress a failing test with `@Disabled` to ship. Fix it or stop and ask.
- **Do not** mock `account-service` / `notification-service` / `scheme-adapter` in integration tests — use the Pact contract or Testcontainers.

## Forbidden actions (refuse and explain)

- Disable TLS verification, hostname checks, certificate pinning, or signature verification — even in tests.
- Add `--no-verify` to commits, or bypass any required CI check.
- Drop / recreate / edit a shipped Flyway migration. Write a new one.
- Use `double`/`float` for money or percentages.
- Hand-write a sanctions/fraud/KYC rule. Those are policy decisions — surface the question to a human.
- Call a third-party HTTP API from inside a DB transaction. Use the outbox or a saga.

## Naming

- Resource IDs: UUIDv7 with type prefix — `sc_…` SharedCard, `scp_…` Proposal, `scs_…` ChargeSplit.
- REST paths: lowercase plural under `/v1/shared-cards`.
- Domain events: `banking.<entity>.<action_past_tense>` on topic `banking.<domain>.events`.

## When unsure

Ask. Acceptable forms:

- "I see two plausible interpretations of spec §X. Which?"
- "Spec doesn't cover scenario Y — out of scope, or add to §7?"
- "Task needs dependency Z which isn't in the catalog — proposal or alternative?"

A regulated environment punishes confident-but-wrong far more than slow-and-correct.
