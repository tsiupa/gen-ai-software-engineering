# agents.md — AI Coding Partner Guidelines (Shared Virtual Card)

> Authoritative behavioral contract for any AI coding agent (Cursor, Claude Code, Copilot Workspace, etc.) working in this repository. These rules **override** any default model behavior. When in doubt, the agent **must ask** rather than guess.

---

## 1. Scope of these rules

These rules apply to all code, configuration, tests, and documentation generated while implementing the **Shared Virtual Card** feature defined in `specification.md`. They are layered on top of the broader banking-platform conventions; where they conflict, **the stricter rule wins**.

---

## 2. Tech-stack assumptions

The agent must assume — and code consistent with — the following stack. Do **not** introduce a new language, framework, or runtime without an explicit human ask.

- **Language / runtime:** Java 21 (LTS), Spring Boot 3.x.
- **Build:** Gradle (Kotlin DSL) with the existing root build conventions.
- **API:** REST over HTTP/1.1+JSON (public), gRPC over mTLS (internal service-to-service).
- **Persistence:** PostgreSQL 15+ via Spring Data JDBC (not JPA — repository convention); migrations via **Flyway** (`V<seq>__<name>.sql`, forward-only; no `R__`).
- **Eventing:** Kafka with the **transactional outbox** pattern (no direct dual-write to DB + Kafka).
- **Caches / coordination:** Redis (idempotency-key cache, rate limits, distributed locks where genuinely needed — locks are last resort, not default).
- **Observability:** OpenTelemetry (traces + metrics), structured JSON logs via Logback, Prometheus scrape endpoint, Grafana dashboards.
- **Testing:** JUnit 5, AssertJ, Mockito (only at the seams), Testcontainers for Postgres/Kafka/Redis, k6 for performance, Pact for contracts, jqwik for property tests.
- **Secrets:** sourced from the platform secret manager via Spring Cloud Config; **never** from env files committed to the repo.

If a task seems to require something outside this stack, **stop and ask**.

---

## 3. Domain rules (banking / FinTech)

These rules are non-negotiable. Code reviewers will reject PRs that violate them, and the agent should refuse to produce code that does.

### 3.1 Money

- **All monetary values are integers in minor units** (e.g., USD cents). No `float`, no `double`. If a third-party API returns a decimal string, parse to `BigDecimal` with `scale ≥ 2`, multiply by $10^{\text{scale}}$, and convert to `long`. Never to `double` first.
- **Percentages → `basis_points`** (integer 0–10000). Sum must equal exactly 10000.
- Per-holder amount: `Math.floorDiv(amount * basis_points, 10000L)`. Residual minor units (from flooring) go to the **proposer's** account — this is the documented "remainder absorber" rule. Implement it by computing all non-proposer shares first and giving the remainder to the proposer.
- Rounding mode anywhere arithmetic genuinely needs it: **`RoundingMode.HALF_EVEN`** (banker's rounding).
- All money in API and event payloads serializes as: `{ "amount_minor_units": <int>, "currency": "<ISO 4217>" }`. Never strings, never floats. JSON serializers must enforce this with a custom converter, not field-by-field.

### 3.2 PCI-DSS / PAN handling

- **Never** read, store, log, return, trace, metric-label, or send to Kafka: the **PAN**, **CVV**, **CVC2**, **PIN**, **track data**, or **full magstripe data**. The tokenization vault holds these; services hold opaque `card_token` references.
- Allowed externally: `pan_last4` only.
- Defense-in-depth: a Logback filter scrubs any 13–19-digit run from log lines before write. Agent must **not** disable or weaken this filter.
- If the agent finds itself about to write code that touches PAN outside the vault adapter, **stop and ask**. There is no legitimate reason to do so in this repository.

### 3.3 PII and counterparty data

- Phone numbers are stored as **keyed HMAC** for lookup + AES-encrypted ciphertext for display; raw E.164 never lives in plaintext columns.
- API responses include `pan_last4`, `phone_masked` (last 2 digits), `display_name` only. **Never** return full phone numbers or other holders' balances.
- Audit rows may include richer identifiers (still under Confidential classification); never include `pan_full`, `cvv`, or `pin`.
- Sanctions-screening outcomes are Compliance-only and never returned to the customer. Customer-facing API collapses every ineligibility reason into `RECIPIENT_NOT_ELIGIBLE` to prevent enumeration.

### 3.4 Idempotency

- **All** state-changing endpoints accept and require an `Idempotency-Key` request header (UUID v4).
- Replays of the same key within 24 h return the **original** response and HTTP status verbatim.
- Same key with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`.
- Internal handlers must compose idempotency keys deterministically: e.g., `"{authorization_id}:{holder_index}"` for account reserves. Never `UUID.randomUUID()` inside a handler — that defeats retry safety.

### 3.5 Concurrency

- Aggregates use **optimistic concurrency** (`version` column). Losers get `409` and retry at the client.
- Distributed locks are a code smell; prefer DB-level constraints (uniqueness, optimistic version, single-row state machines) or message-key partitioning (Kafka by `card_id`).
- Charge posting uses **two-phase reserve → commit** with a 30 s reservation TTL. No partial successes ever surface — all reserves succeed and commit, or all are released.

### 3.6 Audit

- Every state-changing path emits **at least one** `shared_card_audit_event` row with `(actor_user_id, actor_ip, actor_device_id, correlation_id, before_state, after_state, reason_code)`.
- Audit rows are **never deleted**. GDPR Art. 17 deletion requests pseudonymize personal fields; rows remain for the 7-year retention window.
- Audit rows participate in the hash-chain (`hash = H(prev_hash || payload)`). The agent must **not** add a code path that writes events while bypassing the chain.

### 3.7 Observability

- Every public endpoint and every Kafka consumer is instrumented for traces, has a counter for `_total` and `_errors_total`, and a histogram for `_duration_seconds`.
- No PII or PAN in metric labels (cardinality and confidentiality both forbid it).
- Logs are structured JSON with a stable set of top-level fields: `ts`, `level`, `service`, `trace_id`, `span_id`, `correlation_id`, `event`, `card_id?`, `user_id?`.

---

## 4. Code style

- Java 21 features welcomed: records for DTOs, sealed types for state machines, pattern matching for `switch`, `var` only when the inferred type is obvious at the call site.
- Domain aggregates are **pure** — no `@Autowired` Spring beans inside. I/O lives at the edge in adapters/services.
- Public methods documented with **why**, not what. No JavaDoc filler.
- Files are ≤ ~400 LOC; methods ≤ ~50 LOC; cyclomatic complexity ≤ 10. If you're tempted to exceed, **split first**.
- Imports are explicit (no wildcard). Static imports allowed only for AssertJ/JUnit in tests.
- Error responses follow RFC 7807. Every `type` URI is stable and documented in OpenAPI.

---

## 5. Testing & verification expectations

The agent must produce tests *with* the code, not in a follow-up.

- **Unit tests** for every aggregate method, validator, and money computation; coverage gate ≥ 85% line, ≥ 80% branch on new packages.
- **Property tests** for split math (sum invariants, remainder lands on proposer, non-negativity), state-machine reachability, and idempotency-key behavior.
- **Integration tests** for every endpoint using Testcontainers (real Postgres, real Kafka, real Redis). No mocking of these.
- **Contract tests** (Pact) against `account-service`, `notification-service`, `scheme-adapter`. The agent must **not** mock these in integration tests — use the contract.
- **Performance tests** (k6) for any endpoint with a p95/p99 budget in `specification.md` §5. CI gates fail on regression > 10%.
- **Security tests:** SAST (Semgrep / SpotBugs-FindSecBugs), secrets scanner (Gitleaks) on every PR.
- **Chaos / failure tests** for at least: kill account-service mid-charge, kill DB primary mid-write, Kafka consumer crash mid-commit. Assert no orphan reserves, no missing audit rows.

The agent **must not** mark a task complete until: (a) the unit + integration tests for that task pass locally, (b) coverage gates are met, (c) the relevant edge cases from `specification.md` §7 are covered.

---

## 6. How the agent should treat edge cases

Edge cases are first-class. When implementing a task, the agent **must**:

1. Open `specification.md` §7 and read every row whose scenario could plausibly occur on the code path being touched.
2. For each, write a test (unit or integration) that asserts the documented *expected behaviour* **and** the *audit/compliance implication*.
3. If the spec is ambiguous for a real edge case the agent encounters, **stop and ask** — do not invent behavior.

Specific edge-case defaults the agent must apply without prompting:

- **Insufficient funds at any holder ⇒ decline the entire authorization.** Never partial-post. (E13)
- **Currency mismatch ⇒ reject at validation.** No FX in this scope. (E28)
- **Replay of any state-changing call ⇒ idempotent return.** Never double-charge, never double-notify. (E7, E23, §3.4)
- **Partial reversal ⇒ apply against the original charge's split snapshot**, never against the current split. (E15)
- **Unexpected state transition ⇒ 409 with `current_state` echoed**; never silently coerce. (§4.3)
- **Notification failure ⇒ retry; never block the underlying state change.** Audit log is the source of truth. (E22)
- **Hash-chain integrity failure ⇒ halt, page on-call**; never re-link. (E29)

---

## 7. Security & compliance constraints (operational)

- **No new dependencies** without checking the SBOM and license; no GPL-family licenses in production code. Use the existing dependency catalog where possible.
- **No code that disables TLS verification, hostname checks, certificate pinning, or signature verification**, even in tests. Use a fixture-served test PKI instead.
- **No code that bypasses authorization** with TODO comments like "will add later." If auth isn't wired, the endpoint isn't implemented.
- **No code paths gated on `Locale.US` or English-only assumptions** for user-visible strings; use the existing i18n bundle.
- **No third-party HTTP calls from inside a DB transaction** — extract to outbox or saga.
- **Personal data deletion (GDPR)** must follow the documented "pseudonymize, don't delete" path for audit rows (§3.6).
- Before generating code that calls a sanctions screening, KYC, fraud, or scheme adapter the agent has not seen used elsewhere in the repo, **stop and ask** for the adapter's contract.

---

## 8. Performance defaults

- Read paths: pagination is mandatory; default page size 25, max 100. Cursor-based, not offset, for endpoints returning >1k rows.
- Write paths: synchronous up to the budgets in `specification.md` §5; anything heavier moves to async via outbox.
- N+1 queries are a defect — agent must check the SQL plan for any join-heavy read endpoint and fix with a single query or read-model projection.
- Cache invalidation strategy is required for any new cache the agent introduces, in writing, in the PR description.

---

## 9. Documentation expectations

For every implemented task, update:
- **OpenAPI** under `/contracts/openapi/shared-card.yaml`.
- **`specification.md` traceability table** (§10) only if the mapping changed — do not silently drift.
- **README.md** if a public-facing aspect changed.
- **Runbook** (T27) for any new failure mode introduced.
- **CHANGELOG** under `Unreleased` with a short, user-facing line.

---

## 10. Things the agent must never do

A short list to keep in working memory:

- Log, return, or trace PAN / CVV / full PII.
- Use `float` or `double` for money or percentages.
- Disable or downgrade idempotency, TLS, mTLS, signature checks, or auth.
- Add `--no-verify` to commits, or skip required CI checks.
- Drop or recreate tables in a migration (forward-only — write a new migration).
- Suppress a failing test with `@Disabled` to "move on." Either fix the test or stop and ask.
- Invent a sanctions/fraud/KYC rule. These are policy decisions, not engineering decisions.
- Build a "fall back" code path that silently masks a hash-chain integrity break, ledger drift, or scheme-adapter mismatch. These are page-on-call conditions.

When tempted to do any of the above: **stop and ask**.

---

## 11. When the agent is unsure

Default behavior: **ask, don't guess**. Acceptable forms:

- "I see two plausible interpretations of §X — A and B. Which do you want?"
- "The spec doesn't cover scenario Y. Should I add it to §7 and follow up, or treat it as out of scope for now?"
- "This task needs dependency Z, which isn't in our catalog. Want me to propose adding it, or use an existing alternative?"

Silent guessing is the worst failure mode for this codebase. A regulated environment punishes confident-but-wrong far more than slow-and-correct.

---

*End of `agents.md`.*
