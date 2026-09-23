#!/usr/bin/env bash
# The M2 on-device measurement, as a script, so every later run is comparable.
#
#   tools/perf/measure-pager.sh <serial> [pager-runs=3] [cold-starts=10]
#
# Get the serial from `adb devices`; over wireless adb it changes between sessions.
# The app must already be installed (./gradlew installRelease). This script never
# installs or compiles anything: the compile state IS the variable being measured, so
# it is printed first and every number below belongs to it. See docs/architecture.md §8.
#
# The swipe coordinates are for a 1080-wide portrait screen (Galaxy S21 Ultra at its
# default FHD+ resolution) and start mid-grid, so each swipe is one page.
set -euo pipefail

SERIAL="${1:?usage: measure-pager.sh <serial> [pager-runs] [cold-starts]}"
RUNS="${2:-3}"
STARTS="${3:-10}"
PKG=net.pokedex
ACTIVITY=net.pokedex/net.pokedex.MainActivity
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
# Git Bash rewrites anything that looks like an absolute path; device paths must survive.
export MSYS_NO_PATHCONV=1

sh() { "$ADB" -s "$SERIAL" shell "$@" | tr -d '\r'; }

current_box() {
  sh uiautomator dump /sdcard/pager-ui.xml > /dev/null
  sh cat /sdcard/pager-ui.xml | grep -oE 'Box [0-9]+ of [0-9]+' | head -1 | awk '{print $2}'
}

# The app reopens on the last box it showed (user_settings.lastBoxIndex), so a run that
# starts past box 27 spends its swipes against the end of the pager and records idle
# frames. Rewind to box 1 before every run; these swipes happen before the gfxinfo reset
# and are not measured.
rewind() {
  local box
  for _ in 1 2 3; do
    box="$(current_box)"
    [ "$box" = "1" ] && return 0
    for _ in $(seq "${box:-52}"); do
      sh input swipe 162 960 918 960 120
      sleep 0.25
    done
    sleep 1
  done
  echo "could not rewind to box 1 (at box $(current_box))" >&2
  exit 1
}

echo "== compile state"
sh dumpsys package dexopt | grep -A2 "\[$PKG\]" | grep -oE '\[status=[^]]+\] \[reason=[^]]+\]'

echo "== cold starts (TotalTime, ms)"
for _ in $(seq "$STARTS"); do
  sh am force-stop "$PKG"
  sleep 1
  sh am start -W -n "$ACTIVITY" | awk -F': ' '/TotalTime/ {printf "%s ", $2}'
  sleep 2
done
echo

echo "== pager: 25 swipes, 0.5 s apart"
echo "run  janky        p50   p90   p99   slowUI  slowDraw"
for run in $(seq "$RUNS"); do
  sh am force-stop "$PKG"
  sh am start -W -n "$ACTIVITY" > /dev/null
  sleep 2
  rewind
  # Rewinding pages through the same code the run measures, which would warm the JIT
  # and flatter an uncompiled install. Restart so the run begins in a fresh process,
  # reopened on box 1.
  sleep 2
  sh am force-stop "$PKG"
  sh am start -W -n "$ACTIVITY" > /dev/null
  sleep 2
  [ "$(current_box)" = "1" ] || { echo "reopened away from box 1" >&2; exit 1; }
  sh dumpsys gfxinfo "$PKG" reset > /dev/null
  for _ in $(seq 25); do
    sh input swipe 918 960 162 960 180
    sleep 0.5
  done
  # The first occurrence of each line is the process-wide aggregate; per-window
  # sections follow it.
  sh dumpsys gfxinfo "$PKG" | awk -v run="$run" '
    /^Janky frames:/ && !j        { j = $3 " " $4 }
    /^50th percentile:/ && !p50   { p50 = $3 }
    /^90th percentile:/ && !p90   { p90 = $3 }
    /^99th percentile:/ && !p99   { p99 = $3 }
    /^Number Slow UI thread:/ && !ui     { ui = $5 }
    /^Number Slow issue draw commands:/ && !dr { dr = $6 }
    END { printf "%-4s %-12s %-5s %-5s %-5s %-7s %s\n", run, j, p50, p90, p99, ui, dr }'
done
