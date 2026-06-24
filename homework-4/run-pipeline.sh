#!/usr/bin/env bash
#
# Single-command runner for the 6-stage bug-fix pipeline.
#
#   Bug Researcher -> Bug Research Verifier -> Bug Planner -> Bug Fixer
#     -> Security Verifier (changed code) -> Unit Test Generator (changed code)
#
# Each stage runs as a headless Claude Code agent (`claude -p`) whose role is
# loaded from agents/*.agent.md and whose skill (where applicable) is loaded
# automatically from skills/*.md. Stages run in order and pass artifacts to the
# next stage via context/bugs/<ID>/.
#
# Usage:
#   ./run-pipeline.sh            # run all bugs (001 002 003)
#   ./run-pipeline.sh 002        # run a single bug
#
set -euo pipefail

HW_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$HW_DIR"

AGENTS="$HW_DIR/agents"
SKILLS="$HW_DIR/skills"

# Bugs to process (default: all). Override with a positional argument.
if [[ $# -ge 1 ]]; then
  BUGS=("$1")
else
  BUGS=(001 002 003)
fi

command -v claude >/dev/null 2>&1 || {
  echo "ERROR: the 'claude' CLI is required but was not found in PATH." >&2
  echo "Install Claude Code and authenticate, then re-run." >&2
  exit 1
}

# run_stage <agent-file> <prompt> [skill-file]
# Loads the agent role (and optional skill) into the system prompt, then runs
# the task headlessly with the model declared in the agent's frontmatter.
run_stage() {
  local agent_file="$1" prompt="$2" skill_file="${3:-}"
  local model
  model="$(sed -n 's/^model:[[:space:]]*//p' "$agent_file" | head -n1)"

  local system_prompt
  system_prompt="$(cat "$agent_file")"
  if [[ -n "$skill_file" ]]; then
    system_prompt+=$'\n\n# Loaded skill\n\n'"$(cat "$skill_file")"
  fi

  claude -p "$prompt" \
    --model "$model" \
    --append-system-prompt "$system_prompt" \
    --dangerously-skip-permissions
}

echo "==> Pipeline starting (bugs: ${BUGS[*]})"

for BUG in "${BUGS[@]}"; do
  DIR="context/bugs/$BUG"
  [[ -f "$DIR/bug-context.md" ]] || { echo "SKIP: $DIR/bug-context.md not found" >&2; continue; }
  mkdir -p "$DIR/research"

  echo ""
  echo "############################################################"
  echo "# BUG $BUG"
  echo "############################################################"

  echo "--> [1/6] Bug Researcher"
  run_stage "$AGENTS/bug-researcher.agent.md" \
"Act strictly as the Bug Researcher defined in your system prompt. Investigate \
the bug in $DIR/bug-context.md against the application source in src/. Capture \
exact file:line references and verbatim snippets. Write your findings to \
$DIR/research/codebase-research.md using the required sections. Do not modify \
any source code or tests."

  echo "--> [2/6] Bug Research Verifier"
  run_stage "$AGENTS/research-verifier.agent.md" \
"Act strictly as the Bug Research Verifier defined in your system prompt and \
apply the loaded research-quality-measurement skill. Fact-check \
$DIR/research/codebase-research.md against src/. Write \
$DIR/research/verified-research.md with the required sections, including the \
Research Quality level and accuracy ratio. Read-only: do not modify source." \
    "$SKILLS/research-quality-measurement.md"

  echo "--> [3/6] Bug Planner"
  run_stage "$AGENTS/bug-planner.agent.md" \
"Act strictly as the Bug Planner defined in your system prompt. Read \
$DIR/research/verified-research.md and write a precise plan to \
$DIR/implementation-plan.md with exact before/after code for each change and \
the test command. Read-only: do not apply the changes yet."

  echo "--> [4/6] Bug Fixer"
  run_stage "$AGENTS/bug-fixer.agent.md" \
"Act strictly as the Bug Fixer defined in your system prompt. Read \
$DIR/implementation-plan.md, apply the changes to src/ exactly as written, run \
'npm test', and write $DIR/fix-summary.md with the required sections. If tests \
fail, document and stop."

  echo "--> [5/6] Security Verifier (changed code)"
  run_stage "$AGENTS/security-verifier.agent.md" \
"Act strictly as the Security Vulnerabilities Verifier defined in your system \
prompt. Read $DIR/fix-summary.md and review the changed files it references in \
src/. Scan for injection, hardcoded secrets, insecure comparisons, missing \
validation, unsafe deps, and XSS/CSRF. Write $DIR/security-report.md with a \
severity and file:line and remediation per finding. Report only; do not edit code."

  echo "--> [6/6] Unit Test Generator (changed code)"
  run_stage "$AGENTS/unit-test-generator.agent.md" \
"Act strictly as the Unit Test Generator defined in your system prompt and apply \
the loaded FIRST skill. Read $DIR/fix-summary.md, generate unit tests in tests/ \
for the changed code only (node:test + node:assert/strict), run 'npm test', and \
write $DIR/test-report.md including FIRST compliance and the real test output." \
    "$SKILLS/unit-tests-FIRST.md"

  echo "--> BUG $BUG complete. Artifacts in $DIR/"
done

echo ""
echo "==> Pipeline finished. Running full test suite:"
npm test
