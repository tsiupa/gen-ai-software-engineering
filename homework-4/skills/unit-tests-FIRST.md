# Skill: Unit Tests — FIRST Principles

The **Unit Test Generator** must apply the **FIRST** principles to every test it
writes, and must record in `test-report.md` how each principle is satisfied.

## FIRST

| Letter | Principle | What it means | How to satisfy it here |
|--------|-----------|---------------|------------------------|
| **F** | **Fast** | Tests run in milliseconds so they are run often. | No real network, disk, or `sleep`. Bind servers to port `0` (ephemeral) and close them in a `finally`. Avoid fixed timeouts. |
| **I** | **Independent** | Tests do not depend on each other or on order. | Build fresh state per test (`createServer()` / `createQueue()` inside each test). No shared mutable module state between tests. |
| **R** | **Repeatable** | Same result every run, in any environment. | No reliance on wall-clock time, randomness, or external services. Inject/freeze any time-dependent input. |
| **S** | **Self-validating** | The test decides pass/fail by itself. | Use `assert`/`assert.strict`; assert exact values (status codes, bodies). No manual log inspection. |
| **T** | **Timely** | Tests are written together with the code change. | Generate tests for the **changed code only**, in the same pipeline run as the fix. |

## Rules for this project

- Framework: Node's built-in **`node:test`** with **`node:assert/strict`**
  (matches `tests/api.test.js`). Do not add dependencies.
- Test files live in `tests/` and end with `.test.js`; run with `npm test`.
- Test **only the new/changed behaviour** described in `fix-summary.md` — do not
  re-test unrelated code.
- Each test must include at least one assertion and clean up any resources it
  opens (servers, handles).
- Prefer one clear behaviour per test with a descriptive name.

## Required output

In `test-report.md`, include:

- the list of generated test files and the behaviours covered,
- the `npm test` result (counts of pass/fail),
- a short **FIRST compliance** note mapping each letter to how the new tests
  satisfy it.
