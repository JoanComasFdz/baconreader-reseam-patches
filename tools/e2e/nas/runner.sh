#!/bin/bash
# SPDX-FileCopyrightText: 2026 JoanComasFdz
# SPDX-License-Identifier: GPL-3.0-only
# The runner container's entry point (README.md here). Same file in both BaconReader repos.
#   runner.sh register <owner>/<repo> <registration token>  -> registers a runner for that repo, once, in the state volume
#   runner.sh                                               -> runs every registered runner, each restarted when it stops
# Each repo's runner is a copy of /opt/actions-runner in ~/state/runners/<repo>, named nas-<repo>, with the label
# baconreader-phone. It is persistent: registered once, it keeps its registration across restarts and image rebuilds,
# and updates itself.
set -euo pipefail

state="$HOME/state/runners"
mkdir -p "$state" "$HOME/state/cache"

if [ "${1:-}" = register ]; then
  slug="${2:-}" token="${3:-}"
  [[ $slug =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] && [ -n "$token" ] ||
    { echo "Usage: runner.sh register <owner>/<repo> <registration token>" >&2; exit 2; }
  dir="$state/${slug#*/}"
  if [ -f "$dir/.runner" ]; then
    echo "${slug#*/} already has a runner in $dir: remove it there first (./config.sh remove --token <removal token>)." >&2
    exit 1
  fi
  rm -rf "$dir"
  cp -a /opt/actions-runner "$dir"
  cd "$dir"
  ./config.sh --unattended --url "https://github.com/$slug" --token "$token" \
    --name "nas-${slug#*/}" --labels baconreader-phone --work _work --replace
  exit 0
fi

shopt -s nullglob
runners=("$state"/*/.runner)
[ ${#runners[@]} -gt 0 ] || { echo "No runner registered: runner.sh register <owner>/<repo> <token> (README.md)." >&2; exit 1; }
for runner in "${runners[@]}"; do
  dir="$(dirname "$runner")"
  (
    cd "$dir"
    while true; do
      ./run.sh || echo "$(basename "$dir")'s runner stopped ($?): starting it again in 30 s." >&2
      sleep 30
    done
  ) &
done
wait
