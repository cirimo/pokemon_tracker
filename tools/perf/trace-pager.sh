#!/usr/bin/env bash
# A Perfetto trace of measure-pager.sh's 25-swipe run, for attributing the settle frame.
#
#   tools/perf/trace-pager.sh <serial> <out.pftrace> [sample-hz=2000]
#   NAMES=0 tools/perf/trace-pager.sh ...   # without composition tracing
#   NAMES=0 ... <out> 0                     # without sampling either: timings only
#
# Runs against net.pokedex.profiling ONLY: the benchmarkRelease build, installed with
# `./gradlew :app:installBenchmarkRelease`. It is the build that carries composition
# tracing, and it is not the install holding real catch records. docs/architecture.md §8.
#
# One trace carries three things, because none of them answers the question alone:
#   - atrace + SurfaceFlinger's frame timeline: which frames missed, and the Compose
#     runtime's own sections (onRemembered, onForgotten, prefetch, measureAndLayout);
#   - composition tracing (track_event): the name of every composable composed;
#   - callstack sampling (linux.perf): what runs inside onRemembered and onForgotten,
#     which happen after composition and so carry no composable name.
# Composition tracing writes a marker per composable, and those writes show up in the
# samples and inflate every composition slice. Name composables with one trace, then take
# timings and sample proportions from NAMES=0 traces.
#
# The kernel throttles sampling: 2000 Hz asked gave about 440 samples per second of main
# thread running time on the S21 Ultra. Pool several traces rather than trusting one.
# tools/perf/settle_frame.py reads the result.
set -euo pipefail

SERIAL="${1:?usage: trace-pager.sh <serial> <out.pftrace> [sample-hz]}"
OUT="${2:?usage: trace-pager.sh <serial> <out.pftrace> [sample-hz]}"
HZ="${3:-2000}"
PKG=net.pokedex.profiling
ACTIVITY="$PKG/net.pokedex.MainActivity"
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
DEVICE_TRACE=/data/misc/perfetto-traces/pager.pftrace
export MSYS_NO_PATHCONV=1

sh() { "$ADB" -s "$SERIAL" shell "$@" | tr -d '\r'; }

sh pm list packages "$PKG" | grep -qx "package:$PKG" || {
  echo "$PKG is not installed: ./gradlew :app:installBenchmarkRelease" >&2
  exit 1
}

current_box() {
  sh uiautomator dump /sdcard/pager-ui.xml > /dev/null
  sh cat /sdcard/pager-ui.xml | grep -oE 'Box [0-9]+ of [0-9]+' | head -1 | awk '{print $2}'
}

echo "== compile state"
sh dumpsys package dexopt | grep -A2 "\[$PKG\]" | grep -oE '\[status=[^]]+\] \[reason=[^]]+\]'

# Same start as measure-pager.sh: box 1, in a fresh process.
sh am force-stop "$PKG"
sh am start -W -n "$ACTIVITY" > /dev/null
sleep 2
for _ in 1 2 3; do
  box="$(current_box)"
  [ "$box" = "1" ] && break
  for _ in $(seq "${box:-52}"); do sh input swipe 162 960 918 960 120; sleep 0.25; done
  sleep 1
done
sleep 2
sh am force-stop "$PKG"
sh am start -W -n "$ACTIVITY" > /dev/null
sleep 2
[ "$(current_box)" = "1" ] || { echo "reopened away from box 1" >&2; exit 1; }

# Composition tracing is off until this broadcast. The shell holds DUMP, which the
# receiver requires; the binary is bundled in the APK, so no path extra is needed.
[ "${NAMES:-1}" = "0" ] || sh am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING \
  -n "$PKG/androidx.tracing.perfetto.TracingReceiver" | grep -o 'data=.*' || true

cat <<EOF | "$ADB" -s "$SERIAL" shell perfetto --txt -c - -o "$DEVICE_TRACE" --background
buffers { size_kb: 262144 fill_policy: DISCARD }
buffers { size_kb: 131072 fill_policy: DISCARD }
duration_ms: 32000
data_sources { config {
  name: "linux.ftrace" target_buffer: 0
  ftrace_config {
    ftrace_events: "sched/sched_switch"
    ftrace_events: "sched/sched_waking"
    ftrace_events: "power/cpu_frequency"
    atrace_categories: "gfx"
    atrace_categories: "view"
    atrace_categories: "input"
    atrace_categories: "dalvik"
    atrace_apps: "$PKG"
  }
} }
data_sources { config { name: "linux.process_stats" target_buffer: 0 } }
data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 0 } }
data_sources { config {
  name: "track_event" target_buffer: 0
  track_event_config { enabled_categories: "*" }
} }
$( [ "$HZ" = "0" ] || cat <<PERF
data_sources { config {
  name: "linux.perf" target_buffer: 1
  perf_event_config {
    timebase { frequency: $HZ }
    callstack_sampling { scope { target_cmdline: "$PKG" } }
  }
} }
PERF
)
EOF
sleep 2

sh dumpsys gfxinfo "$PKG" reset > /dev/null
for _ in $(seq 25); do
  sh input swipe 918 960 162 960 180
  sleep 0.5
done

# Let the trace run out its duration rather than killing it: a killed session can lose
# the final flush, and with it the last swipes.
while sh pidof perfetto > /dev/null; do sleep 1; done
"$ADB" -s "$SERIAL" pull "$DEVICE_TRACE" "$OUT"
sh dumpsys gfxinfo "$PKG" | grep -m5 -E '^(Janky frames|50th|90th|99th) '
