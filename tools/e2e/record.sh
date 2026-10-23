#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 JoanComasFdz
# SPDX-License-Identifier: GPL-3.0-only
# Plays the e2e scenarios on the emulator and records each as a GIF and an MP4, for the PR (docs/hosting.md, "E2E
# videos on pull requests"). The e2e project is tests/BaconReader.E2E; its scenario classes live under its Scenarios/.
#   tools/e2e/record.sh [all | <domain folder> | <TestClass> ...] [--changed-since <git ref>] [--action-gap <seconds>]
#                       [--out <dir>] [--no-bundle] [--no-video]
# A domain folder is a folder under a project's Scenarios/ (Account, Comments/ShowTables); a class is a scenario class
# (SpoofClientTests). Nothing named, and no --changed-since: all of them.
# --changed-since <ref>: the scenario classes added or changed since the branch left <ref> (working tree included):
# "this PR's scenarios" with origin/main.
# --action-gap <seconds>: the least time between two steps of a recorded scenario (default 1).
# It needs the emulator's connection in its environment (E2E_APPIUM_URL, E2E_ADB_SERIAL, E2E_ARTIFACTS_DIR: run it in
# the shell that sourced the connection script, tests/BaconReader.E2E/README.md, "The emulator"). It builds the bundle
# first (./gradlew bundle; --no-bundle reuses the last one) and runs the chosen scenarios with E2E_VIDEO_DIR set: each
# one patches, installs and records itself (BaseE2E). Each run's video becomes <out>/<domain folder>/<gif name>.gif
# (15 fps, 360 px wide) and .mp4 (H.264 at the recording's 540×960), both holding their last frame 3 s, and
# <out>/scenarios.json lists them (the e2e tool's write-scenarios). Exits non-zero when a scenario failed (its GIF is
# still written).
# --no-video: the same scenarios, unrecorded and unpaced: the fast run while developing. It prints each run's result and
# time and leaves the last recording in <out> as it is.
# Right before a push that adds or changes scenarios (never between pushes): tools/e2e/record.sh --changed-since
# origin/main, then tools/e2e/push-gifs.sh right after the push.
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
out="$repo/out"
changed_since=""
action_gap=1
bundle=1
video=1
chosen=()

while [ $# -gt 0 ]; do
  case "$1" in
    --changed-since) changed_since="$2"; shift 2 ;;
    --action-gap) action_gap="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    --no-bundle) bundle=0; shift ;;
    --no-video) video=0; shift ;;
    -h|--help) sed -n '4,23p' "$0"; exit 0 ;;
    *) chosen+=("$1"); shift ;;
  esac
done
mkdir -p "$out"
out="$(cd "$out" && pwd)"
cd "$repo"

mapfile -t projects < <(find tests -mindepth 2 -maxdepth 2 -name '*.E2E.csproj' | sort)
# The project file whose Scenarios/ holds a scenario class, or nothing.
find_class_project() {
  local project
  for project in "${projects[@]}"; do
    if [ -n "$(find "$(dirname "$project")/Scenarios" -name "$1.cs" -print -quit 2> /dev/null)" ]; then echo "$project"; fi
  done
}

# The scenario classes added or changed since a ref, "<class>\tchanged" each (write-scenarios' selection.tsv).
list_changed_classes() {
  local base project scenarios
  base="$(git merge-base "$1" HEAD)"
  for project in "${projects[@]}"; do
    scenarios="$(dirname "$project")/Scenarios"
    [ -d "$scenarios" ] || continue
    { git diff --name-only --diff-filter=AMR "$base" -- "$scenarios"; git ls-files --others --exclude-standard -- "$scenarios"; } |
      sort -u | grep 'Tests\.cs$' | xargs -r -n1 basename | sed 's/\.cs$/\tchanged/'
  done
}

# Each project's dotnet test filter ('|' is "or"); a project with none doesn't run.
declare -A filters=()
add_filter() {
  filters[$1]="${filters[$1]:+${filters[$1]}|}$2"
}
selection="$out/.selection.tsv"
: > "$selection"
if [ -n "$changed_since" ]; then
  list_changed_classes "$changed_since" | sort -u > "$selection"
  while IFS=$'\t' read -r class _; do
    for project in $(find_class_project "$class"); do add_filter "$project" "FullyQualifiedName~.$class."; done
  done < "$selection"
  if [ ${#filters[@]} -eq 0 ] && [ ${#chosen[@]} -eq 0 ]; then
    echo "No scenario changed since $changed_since."
    if [ $video -eq 1 ]; then echo "[]" > "$out/scenarios.json"; fi
    exit 0
  fi
fi
everything=0
for choice in "${chosen[@]}"; do
  choice="${choice%/}"
  found=0
  if [ "$choice" = all ]; then
    everything=1
    found=1
  fi
  for project in "${projects[@]}"; do
    if [ "$choice" != all ] && [ -d "$(dirname "$project")/Scenarios/$choice" ]; then
      add_filter "$project" "FullyQualifiedName~$(basename "$project" .csproj).Scenarios.${choice//\//.}."
      found=1
    fi
  done
  for project in $(find_class_project "$choice"); do
    add_filter "$project" "FullyQualifiedName~.$choice."
    found=1
  done
  if [ $found -eq 0 ]; then
    echo "No domain folder or scenario class named '$choice' under tests/*/Scenarios" >&2
    exit 2
  fi
done
if [ $everything -eq 1 ] || { [ -z "$changed_since" ] && [ ${#chosen[@]} -eq 0 ]; }; then
  for project in "${projects[@]}"; do filters[$project]="FullyQualifiedName~$(basename "$project" .csproj).Scenarios."; done
fi

if [ $bundle -eq 1 ]; then
  echo "Building the bundle (./gradlew bundle)…"
  ./gradlew bundle --no-daemon -q || { echo "The bundle didn't build." >&2; exit 1; }
fi

if [ -z "${E2E_APPIUM_URL:-}" ] || [ -z "${E2E_ADB_SERIAL:-}" ]; then
  echo "No emulator: E2E_APPIUM_URL and E2E_ADB_SERIAL aren't set. Source the emulator's connection script in this shell" \
       "first (tests/BaconReader.E2E/README.md, \"The emulator\")." >&2
  exit 1
fi

dotnet build tools/e2e/BaconReader.E2ETools -c Release --nologo -v q > /dev/null
e2e_tool() { dotnet "$repo/tools/e2e/BaconReader.E2ETools/bin/Release/net10.0/BaconReader.E2ETools.dll" "$@"; }

raw="$out/.raw"
results="$out/.results"
rm -rf "$raw" "$results"
failed=0
for project in "${!filters[@]}"; do
  echo "Playing $(basename "$project" .csproj): ${filters[$project]}"
  if [ $video -eq 1 ]; then
    E2E_VIDEO_DIR="$raw" E2E_ACTION_GAP="$action_gap" dotnet test "$project" --filter "${filters[$project]}" \
      --logger "trx;LogFileName=$(basename "$project" .csproj).trx" --results-directory "$results" || failed=1
  else
    env -u E2E_VIDEO_DIR dotnet test "$project" --filter "${filters[$project]}" \
      --logger "trx;LogFileName=$(basename "$project" .csproj).trx" --results-directory "$results" || failed=1
  fi
done

if [ $video -eq 0 ]; then
  e2e_tool report-runs "$results"/*.trx || failed=1
  exit $failed
fi

# screenrecord writes a frame only when the screen changes: fps= gives the GIF and the MP4 a steady rate.
hold_end="tpad=stop_mode=clone:stop_duration=3"
gif_filter="fps=15,$hold_end,scale=360:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle"
records=()
while IFS= read -r record; do
  video_file="${record%.json}.mp4"
  domain="$(dirname "${record#"$raw"/}")"
  name="$(basename "$record" .json)"
  mkdir -p "$out/$domain"
  if [ -f "$video_file" ]; then
    ffmpeg -nostdin -loglevel error -y -i "$video_file" -vf "$gif_filter" "$out/$domain/$name.gif"
    ffmpeg -nostdin -loglevel error -y -i "$video_file" -vf "fps=30,$hold_end" -an \
      -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p -movflags +faststart "$out/$domain/$name.mp4"
  else
    echo "No video for $domain/$name: its post says \"No recording\"."
  fi
  records+=("$record")
done < <(find "$raw" -name '*.json' 2> /dev/null | sort)

e2e_tool write-scenarios "$out" "$selection" "${records[@]}"
if grep -q '"passed": false' "$out/scenarios.json"; then failed=1; fi
exit $failed
