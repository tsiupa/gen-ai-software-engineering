# Skill: Research Quality Measurement

A rubric the **Bug Research Verifier** uses to score the quality of a Bug
Researcher's `codebase-research.md` and to label the result in
`verified-research.md`.

## How to use this skill

1. Verify every claim in the research file against the actual source:
   - Does each `file:line` reference point to the line claimed?
   - Does each quoted snippet match the source **verbatim**?
   - Is the described root cause consistent with the code?
2. Count claims as **Verified**, **Inaccurate** (wrong line / altered snippet),
   or **Unverifiable** (no reference, or file/line does not exist).
3. Compute the **accuracy ratio** = Verified / Total claims.
4. Assign a **Research Quality Level** using the table below.
5. Record the level, the ratio, and the reasoning in the result file.

## Quality Levels

| Level | Label | Criteria |
|-------|-------|----------|
| **L4** | Excellent | 100% of claims verified; all snippets verbatim; root cause precise and actionable; no discrepancies. |
| **L3** | Good | ≥ 90% verified; at most minor discrepancies (e.g. off-by-one line, trivial whitespace) that do not change the conclusion. |
| **L2** | Adequate | 70–89% verified; some inaccurate references or snippets, but the identified bug location is still correct. |
| **L1** | Weak | 50–69% verified; multiple inaccuracies or missing references; conclusion only partially supported. |
| **L0** | Unreliable | < 50% verified, or the root cause is wrong / unsupported. Research must be redone before planning. |

## Gate for the next stage

- **L3 or L4** → research is trustworthy; the Bug Planner may proceed.
- **L2** → planner may proceed **with caution**; flag the inaccurate items.
- **L0 or L1** → **block**; the Bug Researcher must redo the research.

## Required output labelling

In `verified-research.md`, the **Verification Summary** must state:

- `Result: PASS | FAIL`
- `Research Quality: L<n> (<label>)`
- `Accuracy: <verified>/<total> (<percentage>)`

and the **Research Quality Assessment** section must explain *why* that level
was assigned, citing specific verified and discrepant claims.
