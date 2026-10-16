#!/usr/bin/env bash
# Runs a command up to N times with a growing pause, for steps that talk to a network service and can fail on a blip.
#
#   .github/scripts/retry.sh 4 sdkmanager --install ...
#
# The first argument is the number of attempts. The exit status is the last attempt's. Do not wrap commands that
# change state in a way that a second run would make wrong.
set -u

attempts=${1:?usage: retry.sh ATTEMPTS COMMAND [ARGS...]}
shift
[ "$#" -gt 0 ] || { echo "retry.sh: no command given" >&2; exit 2; }

pause=${RETRY_PAUSE:-10}
for n in $(seq 1 "$attempts"); do
  "$@"
  status=$?
  if [ "$status" -eq 0 ]; then exit 0; fi
  if [ "$n" -lt "$attempts" ]; then
    echo "retry.sh: attempt $n of $attempts failed with status $status, trying again in ${pause}s: $*" >&2
    sleep "$pause"
    pause=$((pause * 2))
  else
    echo "retry.sh: attempt $n of $attempts failed with status $status: $*" >&2
    exit "$status"
  fi
done
