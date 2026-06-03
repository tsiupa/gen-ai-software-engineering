# Shared Virtual Card — Specification

> **How to read this document**
> The spec is layered from *intent* down to *executable slices*. Each low-level task ties back to a mid-level objective (column `Serves`). Cross-cutting concerns — edge cases, verification, performance — are pulled into their own first-class sections so they cannot be skimmed over. An AI coding agent (or human team) should be able to execute the Low-Level Tasks without inventing requirements.

---

## 1. High-Level Objective

Enable two or more existing bank customers to **co-own a single virtual card** whose every authorized purchase is **automatically split across their personal accounts** by a pre-agreed percentage ratio, with **multi-party consent** gating creation and split changes, and with **any holder able to unilaterally dissolve** the card.

**Scope boundary (one sentence):** This spec covers the lifecycle, authorization-time charge splitting, audit, and notification of a *shared virtual card* product on top of the existing single-account banking platform; it does **not** cover physical card issuance, FX/multi-currency conversion, dispute/chargeback intake, KYC onboarding, or rewards/loyalty.

---

## 2. Mid-Level Objectives

Each objective is **observable** — the third column states what changes in the world when the objective is met.

| # | Objective | Observable outcome |
|---|-----------|-------------------|
| **M1** | A holder can propose a shared card by selecting up to **5** other customers (by phone number) and a percentage split that totals exactly **100%**. | A `SharedCardProposal` exists in state `PENDING_CONFIRMATIONS`; invitees see a confirmation request in their app within 5 s. |
| **M2** | A shared card transitions to `ACTIVE` **only after every invited holder confirms** within a **24 h TTL**; any decline or timeout cancels the proposal. | `SharedCard` is materialized with status `ACTIVE` exactly when all `decisions[].status = CONFIRMED`; otherwise terminal status is `CANCELLED_DECLINED` or `CANCELLED_EXPIRED`. |
| **M3** | A captured purchase debits each linked account **proportionally** to the current split ratio, in a single atomic posting; partial postings are impossible. | For every authorization $A$, $\sum_i debit_i = A$ to the minor unit, and either all per-holder debits commit or none do. |
| **M4** | Split-ratio changes follow the **same N-party confirmation gate** as creation; in-flight authorizations remain unaffected. | A `SharedCardProposal` of type `UPDATE_SPLIT` mutates the card's `split_version` only after unanimous confirmation; authorizations posted before the change use the old version, after use the new. |
| **M5** | Any single holder can **detach themselves** at any time; detachment is **irrevocable** and immediately closes the card for all remaining holders. | After `DELETE /holders/me`, `SharedCard.status = CLOSED_DETACHED`; no further authorizations are accepted; all remaining holders are notified within 5 s. |
| **M6** | Every lifecycle event (propose, confirm, decline, expire, activate, charge-split, split-change-propose/confirm, detach, close) emits an **immutable, queryable audit record** consumable by internal Ops/Compliance. | A read-only Ops endpoint returns a tamper-evident, hash-chained event sequence per `card_id`; the chain validates end-to-end. |
| **M7** | End-user-facing API latencies and charge-split throughput meet the **performance budgets in §5**. | SLO dashboards show p95/p99 within budget over rolling 7-day windows; alerts fire when error budget burns >10%/hour. |
| **M8** | Per-card daily spending limits and per-holder caps are enforced **before** the authorization commits at the scheme layer. | Authorizations exceeding any limit are declined with a stable, documented decline code; declines appear in the audit log with the specific limit that tripped. |

---

## 3. Non-Functional Requirements & Policy

These are **not** suggestions; every low-level task must comply.

### 3.1 Security (PCI-DSS / app sec)
- **PAN, CVV, full track data** never persisted in application stores. The card scheme's tokenization vault holds PAN; services use opaque `card_token` references.
- **PAN / CVV / full PII never appear in logs, metrics, traces, or error bodies.** Logs use `card_id` (internal UUID) and `pan_last4` only. A regex-based log filter rejects any 13–19 digit sequence at the appender level as defense-in-depth.
- All API traffic over **TLS 1.3+**; HSTS preload; service-to-service via **mTLS** inside the mesh.
- Secrets via KMS-backed secret manager; **no plaintext secrets in env files, configs, or code**.
- Data at rest: **AES-256** with per-tenant data keys.
- Authorization tokens: short-lived (≤15 min access, ≤7 d refresh), audience-bound.

### 3.2 Privacy & Data Handling
- Phone numbers stored as **keyed HMAC** for lookup + AES-encrypted ciphertext for display; raw E.164 never persisted in plaintext columns.
- Phone-number lookup endpoint is **rate-limited** (30/min/IP, 10/min/user) and returns a generic `RECIPIENT_NOT_ELIGIBLE` for any of: number not registered, blocked, sanctioned, KYC-stale — to prevent enumeration and information leakage.
- API responses include `pan_last4`, `phone_masked` (last 2 digits), and `display_name` only. Full counterparty data is never echoed.
- Data classification: `card_id`, `account_id` → **Internal**; `phone_hmac`, `display_name` → **Confidential**; `pan_full`, `cvv` → **Restricted** (never leaves vault).

### 3.3 Audit & Compliance
- **Append-only** event log per `card_id` with hash-chain integrity (`event_n.prev_hash = H(event_{n-1})`); chain head signed daily by KMS-managed key.
- Retention: **7 years** post-card-closure (ledger-of-record).
- Every state-changing API call records `actor_user_id`, `actor_ip`, `actor_device_id`, `correlation_id`, `request_id`, `before_state`, `after_state`, `reason_code`.
- Compliance Ops view: read-only, queryable by `card_id`, `user_id`, `date_range`; export to CSV/Parquet for regulator requests.
- Personal data deletion requests (GDPR Art. 17): pseudonymize `display_name`/`phone_masked` in audit events, **never delete the audit row** (financial-record retention overrides erasure for the retention window).

### 3.4 Reliability
- **Availability target: 99.95%** monthly (≈21.6 min downtime/month) for charge-split path; 99.9% for proposal/management endpoints.
- **RPO ≤ 60 s**, **RTO ≤ 15 min** for the charge-split path.
- Active-active across ≥2 regions for read; primary-region writes with automated failover on region-level fault.

### 3.5 Operational Policy
- All state-changing endpoints require an `Idempotency-Key` request header (UUID v4); replays within 24 h return the original response and HTTP status.
- All endpoints return RFC 7807 problem details on error; error `type` URIs are stable and documented.
- All money fields are objects: `{ "amount_minor_units": <int>, "currency": "<ISO 4217>" }`. **Never** floats; **never** strings.
- All timestamps are RFC 3339 UTC with millisecond precision.

---

## 4. Implementation Notes (Guardrails for Builders)

Conventions an AI/human implementer **must not violate**.

### 4.1 Money & Math
- Internal type: **integer minor units** (e.g., USD cents); language type `long` / `BigInteger` / `BigDecimal(scale=0)` per service.
- Split computation: percentages are stored as `basis_points` (integer 0–10000), summing to exactly 10000. **Never** use `double`/`float` for percentages.
- Per-holder amount = `floor(authorization_amount * holder_basis_points / 10000)`.
- **Residual cents** from flooring go to the **proposer's account** (documented "remainder absorber" rule). The sum of all holder debits always equals the authorization to the cent.
- Rounding mode anywhere arithmetic is used: **HALF_EVEN** (banker's rounding).
- No currency conversion in this scope — if `card.currency ≠ any holder_account.currency`, the proposal is rejected at validation.

### 4.2 Idempotency & Concurrency
- All write endpoints idempotent via `Idempotency-Key`.
- `SharedCard` aggregate uses **optimistic concurrency** (`version` column); concurrent split changes resolve by first-writer-wins with 409 Conflict for the loser.
- Charge posting uses a **two-phase reserve/commit** per holder account: reserve all → if all succeed, commit all; if any fails, release all. Reservations expire after 30 s if not committed.
- Pending proposals have a single open-slot rule: **at most one** `UPDATE_SPLIT` proposal may be open per card at a time; second attempt returns 409.

### 4.3 State Machines

**SharedCardProposal**
```
                       (any decline)
                       ┌──────────────► CANCELLED_DECLINED
                       │
                       │       (TTL hit)
PENDING_CONFIRMATIONS ─┼─────────────► CANCELLED_EXPIRED
                       │
                       │   (proposer cancel pre-quorum)
                       ├─────────────► CANCELLED_BY_PROPOSER
                       │
                       │   (all confirm)
                       └─────────────► CONFIRMED ──► (consumed → SharedCard active/updated)
```

**SharedCard**
```
PENDING ──(proposal CONFIRMED)──► ACTIVE
                                    │
                                    ├─(any holder detach)─► CLOSED_DETACHED
                                    ├─(fraud/admin)──────► CLOSED_ADMIN
                                    └─(card expiry)──────► CLOSED_EXPIRED
```

Invalid transitions return `409 INVALID_STATE_TRANSITION` with the actual current state.

### 4.4 Authorization Flow
1. Card scheme webhook → authorization request (single amount, currency, merchant) → `card_id`.
2. Look up `SharedCard`, current `split_version`, snapshot the holder set + basis points.
3. For each holder: call Account service `reserve(account_id, amount_share, ttl=30s, idempotency_key={auth_id}-{holder_idx})`.
4. If any reserve fails (insufficient funds, account frozen, sanctions block) → release all reserves → **decline** the authorization with a specific decline code → emit `SharedCardChargeDeclined` audit event.
5. If all succeed → confirm reservations within the same atomic write → emit `SharedCardChargeSplit` audit event with the immutable split snapshot → ACK scheme.
6. Asynchronous capture/reversal events from the scheme apply proportionally using the **same snapshot** stored in the original charge record — never recomputed against a possibly-newer split version.

### 4.5 Notifications
- One notification per lifecycle event per holder; delivery is best-effort but **audit log is the source of truth**.
- Notification ordering not guaranteed; payloads include monotonic `event_seq` per `card_id` so clients can reorder.
- Notification content **never** includes PAN, CVV, full counterparty PII, or merchant-sensitive data beyond what already appears on the holder's transaction list.

### 4.6 Identifier & Naming Conventions
- All resource IDs are **UUIDv7** (time-ordered) prefixed by resource type: `sc_…` (SharedCard), `scp_…` (Proposal), `scs_…` (ChargeSplit).
- API paths: lowercase plural under `/v1/shared-cards`.
- Event names: `domain.entity.action` past tense, e.g., `banking.shared_card.activated`.

---

## 5. Performance Expectations

All numbers below are **assumed targets** for a mid-sized neobank (≈1 M MAU, ≈5 k peak TPS bank-wide, ≈50 charge-splits/s peak for this feature). They are sized to keep the **shared card path indistinguishable in latency from a regular single-holder virtual card** to end users, and to allow the scheme adapter to ACK within its own 2 s ceiling.

| Path | Metric | Target | Hard ceiling | Why this number |
|------|--------|--------|--------------|-----------------|
| `POST /v1/shared-cards` (create proposal) | p95 latency | ≤ 300 ms | 600 ms | Interactive — user is staring at the screen; matches existing card-create latency in the app. |
| `POST /…/proposals/{id}/confirm` | p95 latency | ≤ 200 ms | 500 ms | Single-row update + event emit; perceived as "instant tap." |
| Charge-split end-to-end (auth → ACK to scheme) | p99 latency | ≤ 800 ms | 1500 ms | Card scheme adapter has a 2 s authorize timeout; we leave ≥500 ms headroom for network + adapter. |
| Notification fan-out (event committed → device delivery) | p95 | ≤ 5 s | 30 s | Push-notification industry norm; users will accept ≤5 s for non-critical confirmations. |
| Read-after-write consistency (`GET /v1/shared-cards/mine` after a state change) | p95 | ≤ 1 s | 3 s | Read-model lag tolerable as long as user sees the card after pull-to-refresh. |
| Charge-split throughput | sustained | 50 TPS / region | 200 TPS burst (30 s) | 50 TPS ≈ 4.3 M charges/day across all shared cards — well above 1 M MAU usage assumptions; 4× burst absorbs lunch/payday spikes. |
| Proposal-expiry sweep job | latency from TTL hit to `CANCELLED_EXPIRED` | ≤ 60 s | 5 min | Holders should not see "expired" proposals lingering as actionable. |
| Audit-chain daily signing job | runtime | ≤ 5 min | 15 min | Operational window before business hours. |

**Limits (hard product constraints, enforced at API):**
- Max linked holders per card: **5** (including proposer).
- Max active shared cards per user: **20**.
- Max open proposals per user: **10** (incoming + outgoing combined).
- Per-card daily spend ceiling: configurable, default **USD 5 000** equivalent.
- Per-holder daily share ceiling: card ceiling × that holder's basis points (computed, not separately configured).

---

## 6. Context

### 6.1 Beginning Context (what exists before this work)

**Services (existing):**
- `account-service` — owns `Account { account_id, owner_user_id, currency, balance_minor_units, status }`; exposes `reserve / commit / release / debit / credit` with idempotency keys.
- `user-service` — owns `User { user_id, phone_hmac, kyc_status, sanctions_status, devices[] }`.
- `card-service` — owns `Card { card_id, owner_user_id, type ∈ {PHYSICAL, VIRTUAL_INDIVIDUAL}, status, limits }`.
- `transaction-service` — single-account ledger; idempotent posting; reconciliation reports.
- `scheme-adapter` — Visa/Mastercard authorize/capture/reverse webhooks; today routes all auths to `card-service`.
- `notification-service` — push / SMS / in-app; per-channel rate limiting.
- `audit-service` — append-only event store; today consumed by Ops portal.
- `kms`, `tokenization-vault`, `secrets-manager`.

**Data stores (existing):** PostgreSQL primary (per service), Kafka event bus, Redis for idempotency-key cache and rate limits.

**Platform (existing):** Java 21, Spring Boot 3.x, gRPC + REST gateways, OpenTelemetry tracing, Kubernetes.

**Conventions (existing):** every domain event published to Kafka topic `banking.<domain>.events`; OpenAPI specs in `/contracts/openapi`; database migrations via Flyway.

### 6.2 Ending Context (what exists after this work)

**New service / module:** `shared-card-service` (could live inside `card-service` if team prefers a modular monolith; spec is agnostic).

**New aggregates:**
- `SharedCard { card_id, status, currency, split_version, holders[], limits, created_at, version }`
- `SharedCardHolder { card_id, user_id, account_id, basis_points, role ∈ {PROPOSER, MEMBER}, joined_at }`
- `SharedCardProposal { proposal_id, card_id_target, type ∈ {CREATE, UPDATE_SPLIT}, payload, expires_at, status, decisions[], created_by }`
- `SharedCardProposalDecision { proposal_id, user_id, status ∈ {PENDING, CONFIRMED, DECLINED}, decided_at }`
- `SharedCardChargeSplit { charge_split_id, card_id, auth_id, split_version_snapshot, per_holder_debits[], total_minor_units, currency, status }`

**New tables (Flyway migration `V<n>__shared_card.sql`):** `shared_card`, `shared_card_holder`, `shared_card_proposal`, `shared_card_proposal_decision`, `shared_card_charge_split`, `shared_card_charge_split_leg`, `shared_card_audit_event`.

**New REST endpoints (`/v1/shared-cards`):**
- `POST /v1/shared-cards` — create proposal
- `GET  /v1/shared-cards/mine` — list own shared cards (active + pending)
- `GET  /v1/shared-cards/{card_id}` — detail
- `POST /v1/shared-cards/proposals/{proposal_id}/confirm`
- `POST /v1/shared-cards/proposals/{proposal_id}/decline`
- `DELETE /v1/shared-cards/proposals/{proposal_id}` — proposer cancel pre-quorum
- `POST /v1/shared-cards/{card_id}/split-change-proposals` — create UPDATE_SPLIT proposal
- `DELETE /v1/shared-cards/{card_id}/holders/me` — unilateral detach (closes card)
- `GET  /v1/shared-cards/{card_id}/transactions` — own visibility
- `GET  /internal/v1/shared-cards/{card_id}/audit` — Ops/Compliance read-only

**New events (Kafka topic `banking.shared_card.events`):** `proposal.created`, `proposal.confirmed`, `proposal.declined`, `proposal.expired`, `proposal.cancelled`, `card.activated`, `card.split_changed`, `card.charge_split.captured`, `card.charge_split.declined`, `card.charge_split.reversed`, `holder.detached`, `card.closed`.

**Scheme adapter change:** router decides `SHARED` vs `INDIVIDUAL` and dispatches to the new handler.

**Observability artifacts:** SLO dashboard, error-budget burn alert, audit-chain integrity alert, daily reconciliation report.

---

## 7. Edge Cases & Failure Modes

Scoped specifically to this feature. **Each row states the expected user-visible outcome AND the audit/compliance implication.**

| # | Scenario | Expected behaviour | Audit / compliance implication |
|---|----------|--------------------|-------------------------------|
| E1 | Proposer lists their own phone in invitees | `400 VALIDATION_ERROR/SELF_INVITE`; client UI also blocks pre-submit. | Logged as validation failure, no audit row for the (non-existent) card. |
| E2 | Same invitee phone appears twice | `400 VALIDATION_ERROR/DUPLICATE_INVITEE`. | As above. |
| E3 | Invitee phone not a customer / blocked / sanctioned / KYC-stale | `200` with generic `RECIPIENT_NOT_ELIGIBLE` for that recipient (no leakage of *which* reason). | If reason is sanctions, emit **separate** internal-only audit event `sanctions.lookup.blocked` visible only to Compliance. |
| E4 | Split basis_points sum ≠ 10000 | `400 VALIDATION_ERROR/SPLIT_SUM`. | — |
| E5 | A holder's basis_points = 0 | `400 VALIDATION_ERROR/ZERO_SHARE` (use detach instead). | — |
| E6 | Holder confirms an expired proposal | `409 PROPOSAL_EXPIRED`. | Confirmation attempt audited with the expired proposal id. |
| E7 | Holder confirms twice (replay) | Idempotent: `200` with original `decided_at`. | Second hit not double-counted. |
| E8 | One holder declines | Proposal → `CANCELLED_DECLINED`; all holders notified within 5 s. | Audit row records `decision_by`, `reason_code` (optional free-text scrubbed of PII). |
| E9 | Proposer cancels before everyone has decided | Allowed; proposal → `CANCELLED_BY_PROPOSER`. | Audited. After at least one CONFIRM the cancellation is still allowed but flagged in audit (potential coercion vector — Ops review hint). |
| E10 | Phone number reassigned to a new user before confirmation | Confirmation tokens are bound to `user_id`, not phone; old user's pending decisions auto-`DECLINED` with reason `IDENTITY_REASSIGNED`. | Audited; security team notified via metric. |
| E11 | Two `UPDATE_SPLIT` proposals open simultaneously | Second attempt returns `409 PROPOSAL_IN_FLIGHT`. | — |
| E12 | Charge arrives while `UPDATE_SPLIT` is pending | Uses **current active** split version, not pending. | Charge audit row references `split_version` actually used. |
| E13 | Authorization where one holder's account has insufficient funds | **Whole authorization declined**; all reserves released. Decline code: `INSUFFICIENT_FUNDS_AT_HOLDER` (does not name the holder to other holders; the affected holder's own statement shows the decline). | Audit row records *which* holder failed and the reserve attempt details (Compliance-only fields). |
| E14 | Authorization where one holder's account is frozen / closed / sanctioned | Same as E13, decline code `HOLDER_ACCOUNT_UNAVAILABLE`. | Same; Compliance can correlate with account-status events. |
| E15 | Partial reversal from scheme (e.g., 30% refund) | Reverse each holder's `floor(reversal_amount * basis_points_snapshot / 10000)`; residual cents reverse to proposer (mirror of charge rule). | Reversal audit row links to original charge_split_id and snapshot. |
| E16 | Reversal of an amount > original (shouldn't happen but guard against scheme bugs) | Reject with internal alert; capped at original amount; difference flagged for manual reconciliation. | High-severity audit event; pages on-call. |
| E17 | Holder closes their personal bank account while linked to a shared card | Account-service publishes `account.closing.requested`; shared-card-service auto-detaches that holder (which closes the card per **M5**); user is informed during the account-close flow that all shared cards will close. | Audit row reason: `AUTO_DETACH_ACCOUNT_CLOSURE`. |
| E18 | Holder's KYC lapses (expired document) | New authorizations decline at the holder's reserve step (E14 path). Existing card remains `ACTIVE` until detach or admin close — KYC lapse alone does not auto-close (avoids surprise card death for other holders). | Audit row each decline; ops dashboard shows "card with KYC-lapsed holder" count. |
| E19 | Concurrent detach by two holders | First write wins (optimistic-lock); second receives `409 CARD_ALREADY_CLOSING` and is treated as already detached. | Both attempts audited; final state has one `holder.detached` event and one `card.closed`. |
| E20 | Detach received while an authorization is in-flight (reserves placed, not committed) | In-flight auth completes against the current split (reserves committed); detach takes effect on the next event after commit. | Both events audited with overlapping timestamps; reconciliation handles ordering. |
| E21 | All N holders trigger detach within ms of each other | Same as E19; the *first* detach closes the card; subsequent detach calls return `409 CARD_ALREADY_CLOSED`. | — |
| E22 | Notification delivery failure (push channel down) | Retry with backoff; fall back to in-app banner; never block the underlying state change. | Audit log is unaffected — it is the source of truth, not the notification. |
| E23 | Idempotency-key collision with different payload | `409 IDEMPOTENCY_KEY_REUSED` (per RFC 9457; preserves original semantics). | — |
| E24 | Fraud heuristic: rapid create→detach loop (>3 per holder per 24 h) | Soft-block (`429 RATE_LIMIT_FRAUD_HINT`) plus fraud-team notification. | Audit event `fraud.heuristic.tripped`. |
| E25 | Card frozen by one holder | **Any** holder can freeze; **only the proposer** can unfreeze (documented product choice for v1). | Audit row records freezer; counts toward fraud heuristics if pattern emerges. |
| E26 | Time-of-day rollover crosses daily-limit boundary mid-authorization | Limit is checked against the wall-clock at *authorization receipt*, in the **holder's** tenant timezone (configured per account). | Audit row records the timezone-resolved bucket. |
| E27 | Database write fails after scheme already ACKed | Compensating event: emit `charge_split.commit_failed`; reconciliation job replays from scheme until ledger matches. | High-severity audit event; pages on-call; ledger discrepancy ticket auto-opened. |
| E28 | Currency mismatch between card and any holder's account | Proposal rejected at validation (`400 VALIDATION_ERROR/CURRENCY_MISMATCH`). | — (FX is out of scope; see §1). |
| E29 | Hash-chain integrity check fails during daily signing | Signing job halts, pages on-call, audit-service goes read-only for that `card_id` until investigated. | Highest-severity event; **never** silently re-link the chain. |

---

## 8. Verification

How we **know** each mid-level objective is met. Verification is a first-class artifact, not a side-effect of "we'll write tests."

### 8.1 Per-objective verification matrix

| Objective | Verification |
|-----------|-------------|
| **M1** Propose with split | Unit: validation rules (sum, count, self/dup invitee, currency). Integration: end-to-end POST → DB row + Kafka event + push to each invitee. Contract test against `notification-service`. |
| **M2** Multi-party confirmation gate | State-machine property tests: from `PENDING_CONFIRMATIONS`, only `(all confirm) → CONFIRMED` activates the card. Replay & expiry tests. Manual UX walkthrough sign-off (Product). |
| **M3** Atomic split posting | Property test: $\sum debit_i = A$ for 10 000 randomized splits. Chaos test: kill account-service mid-commit → reserves expire, no leaked money. Reconciliation report: nightly batch sums every `shared_card_charge_split_leg` and compares to `transaction-service` debits — drift must be $0$. |
| **M4** Split change with confirmation | Same as M1+M2 plus a test verifying that authorizations crossing the change use the version active at auth time. |
| **M5** Unilateral detach closes card | Integration test: 3-holder card; one detaches; assert card status = `CLOSED_DETACHED`, no further authorizations accepted, all remaining holders received a notification. |
| **M6** Audit completeness | Coverage check: every public state-changing endpoint emits ≥1 audit row (enforced via integration-test assertion `auditEvents.count > before`). Daily hash-chain validation job + alert. |
| **M7** Performance budgets | Load test in CI staging on each release: k6 / Gatling scenario hitting §5 numbers; perf regression gate (release blocked if p95 budget exceeded by >10%). Production SLO dashboards + error-budget burn alert. |
| **M8** Limits enforcement | Boundary tests at limit ± 1 minor unit; daily-cap rollover test across timezone boundaries; multi-currency-equivalent cap test (out of scope for v1 but stubbed). |

### 8.2 Test categories (as documentation, not implementation)

- **Unit:** money math (basis-point split + remainder), state-machine transitions, validators, idempotency-key cache.
- **Integration:** REST endpoint × DB × Kafka × notification, with Testcontainers for Postgres/Kafka/Redis.
- **Contract:** Pact consumer-driven contracts against `account-service`, `notification-service`, `scheme-adapter`.
- **End-to-end:** scripted lifecycle: propose → confirm × N → charge × M → split-change → detach → close. Run nightly against a sandbox scheme.
- **Property-based:** split math invariants (sum, non-negativity, remainder lands on proposer), state-machine reachability.
- **Chaos:** kill account-service / Kafka / DB primary mid-flow; assert no orphan reserves, no missing audit rows.
- **Performance:** k6 scripts targeting §5; CI gate.
- **Security:** SAST + DAST on the new endpoints; secrets scanner pre-commit; PCI scope review by Security Eng before launch.
- **Compliance review:** sign-off from Compliance Ops on (a) audit row completeness, (b) sanctions screening hook, (c) data retention config.

### 8.3 Data fixtures (used across categories)

- `holder_set_2_50_50`, `holder_set_3_50_30_20`, `holder_set_5_20_20_20_20_20`, `holder_set_3_with_remainder` (e.g., 33/33/34 against a $0.01 amount).
- `holder_with_frozen_account`, `holder_with_lapsed_kyc`, `holder_with_sanctions_hit`.
- `proposal_at_expiry_T-1s`, `proposal_at_expiry_T+1s`.
- `auth_amount_micro_USD_1c`, `auth_amount_macro_USD_5000`.

### 8.4 Reconciliation (production correctness)

- Nightly: $\sum$(shared_card child debits in `transaction-service`) − $\sum$(authorizations captured by `scheme-adapter`) = **0** per `card_id` per day. Non-zero opens an automatic ticket and pages Finance Ops.
- Weekly: hash-chain integrity sweep across all cards (sampled 100% in v1; sampling later if cost demands).
- Quarterly: Compliance audit-trail dry-run — pretend to be a regulator and answer "show me everything that touched card X."

---

## 9. Low-Level Tasks

Each task ties back to a mid-level objective. Tasks marked **(crit)** are gating for launch; the rest may ship incrementally.

> **For every task below**, the AI agent **must** also: respect §3 (security/policy), follow §4 (conventions), and add the verification listed in §8 before declaring done.

### T01 — Persistence schema and migrations *(crit)* — serves M1, M2, M5, M6
- **What:** Create Flyway migration `V<n>__shared_card.sql` with the seven tables in §6.2; add foreign keys with `ON DELETE RESTRICT` (audit retention overrides cascade); indexes: `(card_id, version)`, `(card_id, created_at desc)` on audit, `(user_id, status)` on holders, `(expires_at)` partial-index where `status='PENDING_CONFIRMATIONS'`.
- **Acceptance:** migration runs forward and back on empty DB and on a copy of staging; integrity constraints reject negative balances, basis-point sums ≠ 10000, currency mismatches at the row level (CHECK constraints as defense-in-depth).

### T02 — `SharedCard` and `SharedCardProposal` domain aggregates *(crit)* — serves M1, M2, M4, M5
- **What:** Implement aggregates with explicit state-machine guards (§4.3). Aggregates expose pure methods (`propose`, `confirm(by:)`, `decline(by:)`, `cancelByProposer`, `expire`, `applyConfirmedCreate`, `applyConfirmedSplitChange`, `detach(by:)`, `freeze(by:)`, `unfreeze(by:)`). No I/O inside aggregate methods.
- **Acceptance:** unit + property tests cover every transition and every invalid transition; mutation testing score ≥ 80%.

### T03 — Idempotency-key middleware integration *(crit)* — serves §3.5
- **What:** Wire the existing platform idempotency filter to all new `POST/DELETE` endpoints; replay window 24 h via Redis; payload hash check (E23).
- **Acceptance:** integration test: identical request returns identical response with same `Location`/body; differing payload under same key returns `409 IDEMPOTENCY_KEY_REUSED`.

### T04 — Phone-based recipient resolution *(crit)* — serves M1, §3.2
- **What:** `RecipientResolver` translates `(phone_e164)` → `(user_id, account_id_candidate)` via HMAC lookup with rate limiter; collapses *all* ineligibility reasons (not registered, blocked, sanctioned, KYC-stale) into a single external response `RECIPIENT_NOT_ELIGIBLE`; emits a Compliance-only audit row for sanctions hits (E3).
- **Acceptance:** unit tests for each ineligibility reason; rate-limit test trips at the documented thresholds; security review confirms no information leak via timing differences (constant-time response path).

### T05 — Split validation *(crit)* — serves M1, M4
- **What:** Validator enforces: basis_points sum = 10000, count 2..5, no zero shares, no duplicates, no self, all holders' account currencies equal card currency (E4, E5, E28). Returns RFC 7807 problem details.
- **Acceptance:** table-driven tests for every documented validation error; fuzzer feeds random inputs and asserts every rejection has a stable `type` URI.

### T06 — `POST /v1/shared-cards` (create proposal) *(crit)* — serves M1
- **What:** Endpoint creates `SharedCardProposal` with `type=CREATE`, calls T04 for each invitee, calls T05 on the split, persists, emits `proposal.created` event, fans notifications.
- **Acceptance:** happy path + each validation/eligibility error; idempotent on replay; p95 ≤ 300 ms in perf test.

### T07 — Confirmation endpoint *(crit)* — serves M2
- **What:** `POST /v1/shared-cards/proposals/{id}/confirm`. Records decision; if quorum reached, atomically activates `SharedCard` (or applies split change if `UPDATE_SPLIT`), emits events; idempotent.
- **Acceptance:** integration test with 2-, 3-, 5-holder cases; E6, E7, E10 covered; p95 ≤ 200 ms.

### T08 — Decline endpoint *(crit)* — serves M2
- **What:** `POST /v1/shared-cards/proposals/{id}/decline`. Sets proposal terminal `CANCELLED_DECLINED`; notifies all holders; idempotent.
- **Acceptance:** integration test confirms terminal state, fan-out, audit row.

### T09 — Proposer-cancel endpoint — serves M2
- **What:** `DELETE /v1/shared-cards/proposals/{id}`; allowed by proposer pre-terminal; flagged in audit if any CONFIRM already recorded (E9).
- **Acceptance:** role enforcement (non-proposer → 403); audit flag visible to Compliance.

### T10 — Proposal expiry sweep job *(crit)* — serves M2
- **What:** Scheduled worker scans `status='PENDING_CONFIRMATIONS' AND expires_at < now()`; transitions to `CANCELLED_EXPIRED`; emits events.
- **Acceptance:** simulated clock test; chaos test (worker killed mid-batch resumes idempotently); SLO: ≤60 s from TTL hit to terminal.

### T11 — Activation transaction on full confirmation *(crit)* — serves M2
- **What:** When last `CONFIRMED` decision arrives, in a single DB transaction: insert `shared_card` + `shared_card_holder × N` + audit row + outbox row; transactional outbox publishes `card.activated` event. **No** dual-write between DB and Kafka.
- **Acceptance:** crash test between DB commit and Kafka publish → outbox replays exactly-once; downstream consumers idempotent.

### T12 — `POST /v1/shared-cards/{card_id}/split-change-proposals` — serves M4
- **What:** Creates `UPDATE_SPLIT` proposal; rejects if another is open (E11); reuses T07/T08 flow.
- **Acceptance:** E11 covered; activated change bumps `split_version`; authorizations crossing the change behave per E12.

### T13 — Authorization handler (charge split) *(crit)* — serves M3
- **What:** Scheme-adapter routes auth for `card_type=SHARED_VIRTUAL` here; handler snapshots split, calls `account-service.reserve` per holder (parallel; idempotency key `{auth_id}:{holder_idx}`), commits all on success or releases all on any failure; persists `shared_card_charge_split` + legs; emits `charge_split.captured` or `charge_split.declined`; ACKs scheme.
- **Acceptance:** property test on split math (8.1/M3); E13–E16 covered; p99 ≤ 800 ms under 50 TPS sustained.

### T14 — Capture / reversal handler *(crit)* — serves M3
- **What:** Apply capture and (partial) reversal using the **snapshot** on the original `shared_card_charge_split` (§4.4 step 6, E15); reject impossible reversals (E16) and page on-call.
- **Acceptance:** unit tests for partial reversal math; E16 triggers high-severity alert in test.

### T15 — Per-card daily limit enforcement *(crit)* — serves M8
- **What:** Pre-commit check against per-card daily ceiling (configurable) using `transaction-service` rollups in the holder's configured timezone (E26); declines exceed with code `LIMIT_EXCEEDED_CARD_DAILY`.
- **Acceptance:** boundary tests at limit ± 1 minor unit; DST-rollover test.

### T16 — Detach endpoint *(crit)* — serves M5
- **What:** `DELETE /v1/shared-cards/{card_id}/holders/me`. Optimistic-lock; on success transitions card to `CLOSED_DETACHED`; emits events; releases card token at vault.
- **Acceptance:** E19, E20, E21 covered; idempotent replay returns the original close timestamp.

### T17 — Auto-detach on personal account closure — serves M5, §3.5
- **What:** Subscriber on `account.closing.requested` event; for each shared card that holder is in, invoke T16 path; user is informed in the account-close flow that shared cards will close.
- **Acceptance:** integration test: closing one of two holders' accounts results in `CLOSED_DETACHED` for the shared card and the surviving holder is notified.

### T18 — Notification fan-out worker *(crit)* — serves M1, M2, M5, M6
- **What:** Consumes `banking.shared_card.events`; per event maps to template and recipient list; calls `notification-service`; never blocks the producing transaction (outbox-driven).
- **Acceptance:** delivery within p95 ≤ 5 s in perf test; E22 covered (push down → in-app fallback); contract test against `notification-service`.

### T19 — Audit-event writer + hash-chain *(crit)* — serves M6
- **What:** Every state-changing path writes `shared_card_audit_event` with `(prev_hash, payload, hash = H(prev_hash || payload))`; daily KMS-signed chain head; integrity validator job.
- **Acceptance:** integrity job catches a manually-tampered row in test; signing job produces a verifiable signature.

### T20 — Compliance read endpoint — serves M6
- **What:** `GET /internal/v1/shared-cards/{card_id}/audit` returns hash-chained events with `chain_valid: true/false` summary; CSV/Parquet export; AuthN via internal-only mTLS + role `compliance-ops`.
- **Acceptance:** role-enforcement test; export round-trip test.

### T21 — Customer-facing list/detail/transactions endpoints — serves M3, M5, §3.2
- **What:** `GET /v1/shared-cards/mine`, `GET /v1/shared-cards/{id}`, `GET /v1/shared-cards/{id}/transactions`. Returns `pan_last4`, `phone_masked`, `display_name`, holder roles, current split. Excludes other holders' personal balance.
- **Acceptance:** authorization test (non-holder → 404, not 403, to avoid leaking existence); read-after-write ≤1 s (M7).

### T22 — Freeze / unfreeze *(crit for product, ships with v1)* — serves §3.5, E25
- **What:** `POST /v1/shared-cards/{id}/freeze` (any holder), `POST /unfreeze` (proposer only); blocks new authorizations; emits events.
- **Acceptance:** authorization test; auth-time test confirms frozen card declines with stable code.

### T23 — Fraud heuristics hook — serves E24
- **What:** Increment fraud-heuristic counters on each proposal create/detach; trip threshold returns `429 RATE_LIMIT_FRAUD_HINT` and emits `fraud.heuristic.tripped`.
- **Acceptance:** simulated abuse pattern trips heuristic and notifies fraud team.

### T24 — Reconciliation job *(crit)* — serves M3, M6
- **What:** Nightly batch sums `shared_card_charge_split_leg` debits and reconciles against `transaction-service`; drift > 0 opens a ticket and pages.
- **Acceptance:** seeded-drift test opens the expected ticket; clean-DB run reports zero drift.

### T25 — OpenAPI contract and SDK regeneration — serves §3.5
- **What:** Update `/contracts/openapi/shared-card.yaml`; regenerate Java/Kotlin/Swift/TS SDKs; all error `type` URIs documented and stable.
- **Acceptance:** contract test in CI catches any breaking change; client integration test passes against generated SDK.

### T26 — Observability: dashboards, SLOs, alerts *(crit)* — serves M7
- **What:** Dashboards for latency / throughput / error rate per endpoint; SLO definitions per §5; error-budget burn alerts; audit-chain integrity alert; reconciliation drift alert.
- **Acceptance:** dashboards render against staging traffic; alerts fire in synthetic-failure test.

### T27 — Runbooks — serves M6, M7
- **What:** On-call runbooks for: scheme-adapter failure, audit-chain integrity break, reconciliation drift, mass detach incident, sanctions-screening outage. Each runbook lists triage, mitigation, rollback, post-incident steps.
- **Acceptance:** game-day exercise covering each scenario; runbook updated with findings.

### T28 — Security review & PCI scope sign-off *(crit, launch-gate)* — serves §3.1
- **What:** Walk-through with Security Eng confirming PAN never leaves the vault, logs scrubbed, mTLS enforced, secrets sourced from KMS, threat model documented (STRIDE).
- **Acceptance:** signed-off threat-model doc; SAST/DAST clean; pen-test scope review.

### T29 — Compliance review & launch sign-off *(crit, launch-gate)* — serves §3.3
- **What:** Compliance Ops dry-run on staging; sanctions-screening hook validated; retention configs verified; GDPR Art. 17 / Art. 30 procedures documented.
- **Acceptance:** signed-off compliance memo attached to launch ticket.

### T30 — Feature-flag rollout & kill-switch *(crit)* — serves M7
- **What:** Gate all new endpoints behind a remote feature flag with per-cohort rollout (1% → 10% → 50% → 100%); a single flag toggle disables creation of *new* proposals while leaving existing cards functional (graceful degradation).
- **Acceptance:** rollout plan documented; kill-switch tested in staging; SLO burn auto-rollback policy documented.

---

## 10. Traceability Summary

| Mid-level objective | Low-level tasks |
|---------------------|-----------------|
| M1 Propose with split | T01, T02, T03, T04, T05, T06, T18, T19 |
| M2 Multi-party confirmation gate | T07, T08, T09, T10, T11, T18, T19 |
| M3 Atomic charge split | T13, T14, T15, T24, T26 |
| M4 Split change with confirmation | T07, T08, T11, T12, T18 |
| M5 Unilateral detach | T16, T17, T18, T19, T21 |
| M6 Audit & Compliance visibility | T19, T20, T24, T26 |
| M7 Performance & reliability | T26, T27, T30 |
| M8 Limits enforced pre-commit | T13, T15 |

---

*End of `specification.md`.*
