#!/usr/bin/env bash
# Scans the tracked files and the new commits for personal identifiers, device serials and local paths.
#
# Usage: scripts/privacy-scan.sh [range]
#   range  commits whose patches and messages are scanned, default origin/dev..HEAD
#
# Patterns come from scripts/privacy-patterns.txt (generic, tracked) and scripts/privacy-patterns.local.txt
# (the maintainer's own values, untracked and ignored by .gitignore). Each line is one extended regular expression.
# Blank lines and lines starting with # are skipped. The matching lines are printed; do not paste them anywhere.
#
# Exit codes: 0 nothing matched, 1 something matched, 2 usage error.
set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "privacy-scan: run this inside the repository" >&2
  exit 2
}
cd "$root" || exit 2

range=${1:-origin/dev..HEAD}
if ! git rev-parse -q --verify "${range%%..*}" >/dev/null 2>&1; then
  echo "privacy-scan: '${range%%..*}' is not a commit; fetch it or pass a range" >&2
  exit 2
fi

patterns=$(mktemp) || exit 2
trap 'rm -f "$patterns"' EXIT
for file in scripts/privacy-patterns.txt scripts/privacy-patterns.local.txt; do
  [ -f "$file" ] || continue
  grep -v -E '^[[:space:]]*(#|$)' "$file" >> "$patterns" || true
done
if [ ! -s "$patterns" ]; then
  echo "privacy-scan: no patterns in scripts/privacy-patterns.txt or scripts/privacy-patterns.local.txt" >&2
  exit 2
fi

status=0

echo "== tracked files"
if git grep -I -i -n -E -f "$patterns" -- . ':(exclude)scripts/privacy-patterns.txt'; then
  status=1
fi

echo "== patches and messages in $range"
if git log -p --no-color --format='commit %H%n%B' "$range" | grep -i -E -f "$patterns"; then
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "privacy-scan: nothing matched"
else
  echo "privacy-scan: matches found above" >&2
fi
exit "$status"
