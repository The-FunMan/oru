#!/usr/bin/env bash
# Makes sure the required CI checks have passed on one commit before a release is cut.
#
# Usage: ci-gate.sh <sha> <plan|dispatch>
#   plan      report the state of every required check; exit 0 only when all are success
#   dispatch  when no required check exists for <sha>, start ci.yml on dev, then wait until all are finished
#
# Required checks are the job ids of ci.yml that run on branches. dependency-review runs on pull requests only and
# is not required here. A ci.yml run reports its checks on the commit it built, so only the check runs of <sha> count.
# Environment: GH_TOKEN and GH_REPO (owner/name).
set -euo pipefail

sha=${1:?usage: ci-gate.sh <sha> <plan|dispatch>}
mode=${2:?usage: ci-gate.sh <sha> <plan|dispatch>}
required="android patcher patch-api-guard rust"

# Latest run of each check name on the commit, as name, status and conclusion.
snapshot() {
  gh api --paginate "repos/$GH_REPO/commits/$sha/check-runs?per_page=100" \
    --jq '.check_runs[] | [.name, .status, (.conclusion // "-")] | @tsv' |
    awk -F'\t' '{ last[$1] = $2 "\t" $3 } END { for (n in last) print n "\t" last[n] }'
}

# One line per required check: name, then status and conclusion, or "missing".
report() {
  local snap name line
  snap=$(snapshot)
  for name in $required; do
    line=$(awk -F'\t' -v n="$name" '$1 == n { print $2 "\t" $3 }' <<<"$snap")
    if [ -n "$line" ]; then
      printf '%s\t%s\n' "$name" "$line"
    else
      printf '%s\tmissing\n' "$name"
    fi
  done
}

# Prints the report. Returns 0 when all checks succeeded, 1 when one failed, 2 while any is missing or running.
check_once() {
  local table name status conclusion result=0
  table=$(report)
  printf '%s\n' "$table"
  while IFS=$'\t' read -r name status conclusion; do
    if [ "$status" != completed ]; then
      if [ "$result" -eq 0 ]; then result=2; fi
    elif [ "$conclusion" != success ]; then
      result=1
    fi
  done <<<"$table"
  return "$result"
}

if [ "$mode" = plan ]; then
  if check_once; then
    echo "ci-gate: every required check passed on $sha"
    exit 0
  fi
  echo "ci-gate: required checks are not all green on $sha" >&2
  exit 1
fi

if [ "$mode" != dispatch ]; then
  echo "ci-gate: mode must be plan or dispatch, found '$mode'" >&2
  exit 2
fi

missing_android() {
  report | grep -q "^android	missing$"
}

if missing_android; then
  echo "ci-gate: no CI run for $sha, starting ci.yml on dev"
  gh workflow run ci.yml --ref dev
  for _ in $(seq 1 20); do
    sleep 30
    if ! missing_android; then break; fi
  done
  if missing_android; then
    echo "ci-gate: the CI run did not report on $sha; dev may have moved on" >&2
    exit 1
  fi
fi

for _ in $(seq 1 90); do
  set +e
  check_once
  rc=$?
  set -e
  case $rc in
    0) echo "ci-gate: every required check passed on $sha"; exit 0 ;;
    1) echo "ci-gate: a required check failed on $sha" >&2; exit 1 ;;
  esac
  sleep 60
done
echo "ci-gate: timed out waiting for the required checks on $sha" >&2
exit 1
