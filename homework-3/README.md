# Homework 3 — Specification-Driven Design

## Student & task summary

**Student:** Oleksandr Tsiupa

This homework is a **documents-only** deliverable: a layered specification package for a finance-oriented feature on top of an existing banking application. The chosen feature is the **Shared Virtual Card** described in `INITIATIVE.md` — a virtual card that splits every authorized purchase across multiple accounts owned by different customers, with multi-party confirmation for creation/updates and unilateral detachment for removal.

No code is produced. The graded artifact is the **specification itself** and the supporting agent/editor rules that would steer an AI coding partner to implement it consistently in a regulated environment.

### Files in this package

| File | Purpose |
|------|---------|
| [`specification.md`](./specification.md) | The full layered spec — high-level objective → mid-level objectives → non-functional/policy → implementation notes → context → 30 low-level tasks, plus dedicated sections for edge cases, verification, performance, and traceability. |
| [`agents.md`](./agents.md) | Authoritative behavioral contract for any AI coding agent working in this repo: stack assumptions, banking domain rules, testing expectations, security & compliance constraints, and how to treat edge cases. |
| [`.cursor/rules/fintech.md`](./.cursor/rules/fintech.md) | Cursor (and equivalent) editor rules — a compressed, always-loaded version of the rules in `agents.md` aimed at steering live edits in the IDE. |
| `INITIATIVE.md` | The product brief that seeded the spec (provided). |
| `TASKS.md` | The homework instructions (provided). |
| `specification-TEMPLATE-example.md` | The seed template (provided). |

---

## Rationale — why the specification was written this way

### Why a layered shape (intent → objectives → policy → tasks)

A flat spec can describe **what** to build but rarely shows **why** any single piece exists. In a regulated banking context the "why" is load-bearing: when a regulator asks "show me how your audit log meets retention rules," the answer can't be "see this method." It has to be: *this objective (M6) demanded it, this non-functional rule (§3.3) sized it, these tasks (T19, T20) implemented it, and §8 verifies it.* The layered shape in `specification.md` is built specifically so that traceability runs both directions — every task in §9 names the mid-level objective(s) it serves, and §10 lists the inverse mapping.

The rubric in `TASKS.md` calls out "many" small low-level tasks rather than three generic bullets. `specification.md` §9 contains **30** tasks (T01–T30) covering schema, aggregates, each REST endpoint, the charge-split path, audit hash-chain, observability, runbooks, security/compliance review gates, and feature-flag rollout — enough to show real decomposition rather than a token gesture.

### Why edge cases, verification, and performance are first-class sections (not bullets)

The rubric explicitly says these must "appear somewhere in the spec, not only in README" and **not** be relegated to a single vague bullet. So:

- Edge cases (§7) are a **29-row table** scoped to *this* feature — self-invite, duplicate invitees, expired confirmations, phone reassignment, concurrent detach, partial reversal, KYC lapse mid-card, hash-chain break, etc. — each with both *expected behaviour* and *audit/compliance implication*. They are referenced by ID (E1–E29) from individual tasks so an implementer can't miss them.
- Verification (§8) provides a per-objective matrix, the test categories that count as evidence, named fixtures, and explicit **reconciliation** rules (nightly drift = 0, weekly hash-chain sweep, quarterly Compliance dry-run). Several low-level tasks carry their own *Acceptance* lines so the definition-of-done is not folksy ("it works") but checkable.
- Performance (§5) is a labelled table of **assumed targets** (the rubric encourages labelling and justifying), each row including the *why* — e.g., the 800 ms p99 budget for charge-split is sized to fit inside the 2 s scheme-adapter ceiling with ≥500 ms headroom. The "Why this number" column is the part graders can interrogate without me being in the room.

### How performance targets were chosen

Two anchors:

1. **External hard ceilings** the system *cannot* exceed regardless of preference — the card-scheme authorize timeout (2 s, industry-standard) constrains the synchronous charge-split path. Working backwards: subtract ~500 ms for scheme/network round-trip, leaving an ~800 ms p99 budget for our handler. Anything looser is fictional; anything tighter is over-engineered for v1.
2. **End-user perceptual budgets** — under ~300 ms p95 the UI feels "instant"; up to ~500 ms is acceptable for a confirmation tap; >1 s on a primary action invites bug reports. The proposal/confirm endpoints are sized accordingly.

Throughput is sized for a hypothetical mid-sized neobank (≈1 M MAU). 50 charge-splits/s sustained ≈ 4.3 M charges/day across all shared cards, well above realistic usage on a v1 launch; the 4× burst absorbs lunch/payday spikes without paging on-call. Every number is labelled "assumed target" per the rubric.

### How verification depth was chosen

Banking software is graded on **what didn't go wrong**. So verification leans heavy on:

- **Property tests** for money math (sum invariants, remainder placement, non-negativity) — manual case selection always misses a bucket.
- **Chaos tests** at the failure seams (kill account-service mid-commit, kill DB mid-write, kill Kafka consumer mid-publish) — these are exactly the moments where partial state can leak money.
- **Reconciliation** as a production correctness control, not just a test — daily ledger-drift = 0 catches anything tests didn't.
- **Hash-chain integrity sweep** because an audit log that *might* be tampered with is no better than no audit log; the chain plus daily KMS signature is the cryptographic shoulder.
- **Explicit Compliance / Security launch gates** (T28, T29) so the launch checklist forces a human pause — a v1 of a regulated product should not ship purely on green CI.

I deliberately did **not** add UI tests, accessibility tests, or load tests beyond the stated SLOs to the spec — over-specifying verification creates noise that real reviewers will skim past. The cuts here are intentional.

### Why the agents.md + Cursor rules layering

`agents.md` is the **detailed contract** an agent should fully read at task start. `.cursor/rules/fintech.md` is the **always-loaded synopsis** an editor injects on every prompt. They reference the same source of truth (`specification.md`) so we avoid the classic "rules drift apart from the spec" failure. Both files end with the same instruction — *when unsure, ask, don't guess* — because in a regulated environment confident-but-wrong is the most expensive failure mode.

### Stack and scope choices (locked in this submission)

- **Stack assumed:** Java 21 + Spring Boot 3.x, PostgreSQL + Spring Data JDBC, Kafka with transactional outbox, Redis for idempotency/rate limits. Consistent with Homework 1's Spring Boot Banking API.
- **Editor rules format:** `.cursor/rules/*.md`. Equivalent rules could be placed under `.github/copilot-instructions.md` or `.claude/` without rewriting the content.
- **Out of scope** (called out in `specification.md` §1): physical card issuance, FX/multi-currency conversion, dispute/chargeback intake, KYC onboarding, rewards/loyalty.

---

## Industry best practices — and where they appear

The cross-reference column points to the *specific* file and section where the practice is encoded in this package, per the rubric's "industry best practices … and **where they appear** in the spec (file/section references)" requirement.

### FinTech / banking-specific

| Practice | Why it matters | Where it lives |
|---------|---------------|----------------|
| **Integer minor units for money; no floats; ISO 4217 currency on every amount** | Floating-point arithmetic silently loses cents over millions of transactions; financial audits don't forgive that. | `specification.md` §4.1; `agents.md` §3.1; `.cursor/rules/fintech.md` → "Money — always" |
| **Basis-points for percentages; documented "remainder absorber" rule** | Percentage splits with rounding leak fractional cents; somebody has to absorb them, and *who* must be a written decision. | `specification.md` §4.1, T13; `agents.md` §3.1 |
| **PCI-DSS scope minimization — PAN/CVV in vault only, never logs/metrics/events** | Out-of-scope PAN handling drags the entire codebase into PCI audit. Defense-in-depth log-line filter handles operator error. | `specification.md` §3.1; `agents.md` §3.2; `.cursor/rules/fintech.md` → "PCI-DSS — never" |
| **Sanctions screening collapsed to a generic "RECIPIENT_NOT_ELIGIBLE"** | Enumerable sanctions responses leak adversarially useful data and may itself violate OFAC tipping-off rules. | `specification.md` §3.2, E3, T04; `agents.md` §3.3 |
| **Append-only, hash-chained audit log with KMS-signed daily head; 7-year retention** | Banking is a "ledger of record" business; tamper-evident chains are the modern equivalent of bound paper journals. | `specification.md` §3.3, T19, T20; `agents.md` §3.6 |
| **GDPR Art. 17 = pseudonymize, do not delete audit rows** | Right-to-erasure conflicts with financial-record retention; the documented resolution is pseudonymization within the audit. | `specification.md` §3.3; `agents.md` §3.6 |
| **Idempotency-Key on every state-changing endpoint with 24 h replay window (RFC 9457 patterns)** | Mobile retries are inevitable; without idempotency they create double-charges, the most visible class of bug. | `specification.md` §3.5, T03; `agents.md` §3.4; `.cursor/rules/fintech.md` → "Idempotency — required" |
| **Two-phase reserve → commit for multi-account postings; no partial successes** | A multi-leg transaction that half-commits is unrecoverable without manual ops; better to decline cleanly. | `specification.md` §4.2, §4.4, T13; `agents.md` §3.5 |
| **Per-card daily limits enforced pre-commit in the holder's tenant timezone** | Time-of-day rollovers across DST/timezones are a classic source of "we charged through the daily cap" incidents. | `specification.md` §5, E26, T15 |
| **Nightly reconciliation across sub-ledger and scheme captures** | Tests catch logic errors; reconciliation catches the unknown unknowns (clock skew, lost messages, exotic scheme behavior). | `specification.md` §8.4, T24 |

### General software / SRE practices

| Practice | Why it matters | Where it lives |
|---------|---------------|----------------|
| **Layered spec with explicit traceability table from objectives to tasks** | Without traceability, "is this implemented?" becomes archaeology. | `specification.md` §10 |
| **Define SLOs with hard ceilings and error-budget burn alerts (Google SRE)** | A latency target without a budget is decoration. Error budgets force a written rollout-vs-stability tradeoff. | `specification.md` §5, T26 |
| **Transactional outbox pattern; no DB+Kafka dual-writes** | Dual-writes either lose events on crash or duplicate them; outbox makes commits atomic. | `specification.md` T11; `agents.md` §2; `.cursor/rules/fintech.md` → Stack defaults |
| **Optimistic concurrency on aggregates; distributed locks as last resort** | Locks scale badly and tend to leak. Optimistic concurrency surfaces conflicts to the user as retries — exactly where they belong. | `specification.md` §4.2; `agents.md` §3.5 |
| **Forward-only Flyway migrations; never edit a shipped migration** | Rewriting history of a deployed migration breaks every replica and every developer who already ran it. | `agents.md` §2, §10; `.cursor/rules/fintech.md` → "Forbidden actions" |
| **Feature-flag rollout (1% → 10% → 50% → 100%) with kill-switch and auto-rollback on SLO burn** | A regulated launch shouldn't be all-or-nothing; cohort rollout converts unknown-unknown risk into observable risk. | `specification.md` T30 |
| **Pact-based consumer-driven contract tests instead of mocking partner services** | Mocks drift from reality; contracts force a shared, versioned source of truth between teams. | `specification.md` §8.2; `agents.md` §5 |
| **Property-based tests for invariants (money math, state-machine reachability)** | Example tests miss the cases the author didn't think of; property tests find them. | `specification.md` §8.1, §8.2; `agents.md` §5 |
| **Chaos tests at failure seams (DB mid-write, Kafka mid-publish, account-service mid-charge)** | Partial-failure paths are where money leaks. Exercising them is the only way to learn they're safe. | `specification.md` §8.2; `agents.md` §5 |
| **Structured JSON logs with `trace_id`/`correlation_id`; no PII in metric labels** | Cardinality bombs and confidentiality both forbid PII labels; correlation IDs make incident timelines tractable. | `specification.md` §3.5, agents.md §3.7 |
| **RFC 7807 problem details for errors; stable `type` URIs in OpenAPI** | Stable error types are a public contract; clients code against them. | `specification.md` §3.5; `agents.md` §4 |
| **Explicit Security and Compliance review gates as named launch-blockers (T28, T29)** | A green CI pipeline doesn't approximate a regulated launch. Force a documented human pause. | `specification.md` T28, T29 |
| **Runbooks per failure mode** | The runbook is the difference between a 30-minute on-call page and a 3-hour one. | `specification.md` T27 |
| **Auth model: 404 (not 403) for non-holders on existence-leaking endpoints** | A `403` confirms the resource exists; for shared cards that's enough adversarial signal to enable social-engineering. | `specification.md` T21 |
| **"Ask, don't guess" as the explicit agent default** | The most expensive failure mode of an AI partner in regulated code is confident-but-wrong. Making the default ask-first changes that. | `agents.md` §11; `.cursor/rules/fintech.md` → "When unsure" |

---

*End of `README.md`.*
