#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 JoanComasFdz
# SPDX-License-Identifier: GPL-3.0-only
# Runs the e2e suites of both BaconReader repos, one after the other, in a two-repo session: the clones of
# baconreader-reseam-patches and baconreader-morphe-patches side by side (/home/user). Same file in both repos.
#   tools/e2e/test-both-repos.sh [reseam|morphe ...]    (both by default, Reseam first)
# Build the bundles first (patcher-specific-build.md, "Two-repo sessions"): the tests patch with them, they don't build.
# For each repo it sources that repo's tools/e2e/connect.sh, so the suite gets its own .e2e/ and the lease is renewed,
# and runs `dotnet test BaconReader.E2E.slnx` with the repo's JDK (17 for Reseam, 21 for Morphe Desktop); at the end,
# connect.sh --down. Both suites take ~7 minutes, more than a foreground Bash call allows: run it in the background with
# its output in a file, and follow the file.
# Exits 0 when every suite passed, 1 when one failed, 2 when a repo, bundle or the emulator connection is missing.
set -o pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
repos=("$@")
[ ${#repos[@]} -gt 0 ] || repos=(reseam morphe)

status=0
connected=""
for name in "${repos[@]}"; do
  case "$name" in
    reseam) jdk=/opt/patch-tools/jdk17 bundle=build/reseam/baconreader-patches.reseam ;;
    morphe) jdk=/opt/patch-tools/jdk21 bundle="patches/build/libs/patches-*.mpp" ;;
    *) echo "test-both-repos: unknown repo $name (reseam or morphe)" >&2; status=2; break ;;
  esac
  repo="$root/baconreader-$name-patches"
  echo "== $name: $repo"
  cd "$repo" 2> /dev/null || { echo "test-both-repos: $repo is missing" >&2; status=2; break; }
  # shellcheck disable=SC2086 # the Morphe bundle is a glob
  ls $bundle > /dev/null 2>&1 || { echo "test-both-repos: no $name bundle ($bundle): build it first" >&2; status=2; break; }
  # Sourced here, not run, so its E2E_* exports reach dotnet test.
  # shellcheck source=/dev/null
  source tools/e2e/connect.sh || { echo "test-both-repos: connect.sh failed in $name" >&2; status=2; break; }
  connected="$repo"
  JAVA_HOME="$jdk" PATH="$jdk/bin:$PATH" ANDROID_HOME=/opt/android-sdk dotnet test BaconReader.E2E.slnx ||
    { echo "test-both-repos: $name e2e tests failed" >&2; status=1; }
done
[ -z "$connected" ] || bash "$connected/tools/e2e/connect.sh" --down
echo "== result: $([ $status -eq 0 ] && echo "all passed" || echo "exit $status")"
exit "$status"
