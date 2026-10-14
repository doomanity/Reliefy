#!/usr/bin/env bash
# Runs a Gradle command, and if it fails, surfaces the useful part of the log:
#  - as GitHub annotations (visible on the run page and through the checks API)
#  - as a full log file pushed to the `ci-logs` branch
# Usage: .github/scripts/gradle-ci.sh <label> <gradle args...>
set -uo pipefail

label="$1"; shift
log="gradle-${label}.log"

./gradlew "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -eq 0 ]; then
  exit 0
fi

# Escape for workflow commands: % CR LF must be encoded.
encode() { sed -e 's/%/%25/g' | sed -e ':a;N;$!ba;s/\r/%0D/g;s/\n/%0A/g'; }

# Kotlin / Java compiler errors.
errs=$(grep -E '^(e: |error: )|: error: ' "$log" | sed -E 's#file:///home/runner/work/[^/]+/[^/]+/##' | head -n 80 || true)
if [ -n "$errs" ]; then
  echo "::error title=${label}: compile errors::$(printf '%s' "$errs" | encode)"
fi

# Gradle's own summary.
wrong=$(awk '/What went wrong:/{f=1} f{print} /Try:/{if(f) exit}' "$log" | head -n 60 || true)
if [ -n "$wrong" ]; then
  echo "::error title=${label}: what went wrong::$(printf '%s' "$wrong" | encode)"
fi

# Last lines, in case neither of the above matched.
echo "::error title=${label}: log tail::$(tail -n 40 "$log" | encode)"

# Full log on a throwaway branch, readable through the contents API.
if [ -n "${GITHUB_TOKEN:-}" ]; then
  tmp=$(mktemp -d)
  cp "$log" "$tmp/"
  (
    cd "$tmp" && git init -q -b ci-logs &&
    git config user.name "github-actions" && git config user.email "actions@github.com" &&
    git add . && git commit -q -m "CI log for ${GITHUB_SHA::7} (${label})" &&
    git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" ci-logs
  ) || echo "could not push ci-logs branch"
fi

exit "$status"
