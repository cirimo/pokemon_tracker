"""Attribute the pager's settle-frame work, from traces taken by trace-pager.sh.

    python settle_frame.py <mapping.txt> <trace.pftrace> [more traces...]

Needs the `perfetto` package (pip install perfetto, in a scratch virtualenv). mapping.txt is
R8's, from app/build/outputs/mapping/benchmarkRelease/, and must come from the same build as
the traces, or the library frames will not deobfuscate.

Two tables, pooled over every trace given:

1. The settle phases per swipe, from the Compose runtime's own trace sections: prefetching
   the next page (compose, apply -> onRemembered, measure) and deactivating the one that
   left (onForgotten), each summed per swipe. Median, p90 and worst, in ms.
2. For each phase, where the callstack samples inside it land, bucketed by the object that
   owns the work. A bucket's share of a phase's samples times the phase's median is an
   estimate of what removing that object would save. Confirm it with an ablation: sampling
   inflates every phase, and a share is not a saving until a build without the object
   measures it.

docs/architecture.md §8 records what this said and what was done about it.
"""

import collections
import re
import statistics
import sys

from perfetto.trace_processor import TraceProcessor

PKG = "net.pokedex.profiling"

PHASES = [
    # (label, slice name). All on the main thread. Rows overlap: onRemembered runs inside
    # apply, onForgotten inside deactivate.
    ("prefetch: compose", "compose:lazy:prefetch:compose"),
    ("prefetch: apply", "compose:lazy:prefetch:apply"),
    ("  onRemembered", "Compose:onRemembered"),
    ("prefetch: measure", "compose:lazy:prefetch:measure"),
    ("deactivate", "Compose:deactivate"),
    ("  onForgotten", "Compose:onForgotten"),
    # Present only when the prefetch did not fit the idle time between frames and the
    # scheduler ran it as a frame of its own: the late settle frame.
    ("over-budget idle_frame", "compose:lazy:prefetch:idle_frame"),
]

# A sample goes to the deepest OWNER frame in its stack: the object whose remember, forget,
# composition or measure it is. Generic runtime frames (snapshot reads, list copies,
# coroutine dispatch) are what an owner spends its time in, so they must not win just for
# being nearer the leaf. Only a stack with no owner falls through to STRUCTURE, and only
# one matching neither is "other".
OWNERS = [
    ("shared element (slotOrigin)", ("androidx.compose.animation.Shared", "SharedContentState")),
    ("coil AsyncImage", ("coil3.",)),
    ("BoxSlot press animations", ("animateValueAsState", "AnimateAsState", "collectIsPressedAsState")),
    ("text", ("TextStringSimpleNode", "TextLayout", "Paragraph", "StaticLayout", "BasicText")),
    ("clickable", ("Clickable", "Focusable", "Hoverable", "SuspendingPointerInput")),
]
STRUCTURE = [
    ("LaunchedEffect start/cancel", ("LaunchedEffectImpl", "JobSupport", "CoroutineStart")),
    ("border/background/clip/scale", ("Border", "Background", "Clip", "GraphicsLayer", "Scale")),
    ("semantics", ("Semantics",)),
    ("layout node attach/detach", ("LayoutNode.onDeactivate", "LayoutNode.onRelease", "LayoutNode.attach",
                                   "LayoutNode.detach", "NodeChain", "Modifier$Node")),
    ("measure (other)", ("measure", "Measure", "layout", "Layout")),
    ("composer (other)", ("androidx.compose.runtime.",)),
]


def load_mapping(path):
    classes, methods, cur = {}, {}, None
    for line in open(path, encoding="utf-8"):
        if line.startswith("#"):
            continue
        if not line.startswith(" "):
            m = re.match(r"(\S+) -> (\S+):", line)
            if m:
                cur = m.group(1)
                classes[m.group(2)] = cur
        else:
            m = re.match(r"\s+(?:\d+:\d+:)?\S+ ([^\s(]+)\(.*\)(?::\d+(?::\d+)?)? -> (\S+)", line)
            if m and cur:
                methods.setdefault((cur, m.group(2)), m.group(1))
    return classes, methods


def main():
    classes, methods = load_mapping(sys.argv[1])

    def deob(name):
        m = re.match(r"^([\w$.]+)\.([\w$<>]+)$", name or "")
        if m and m.group(1) in classes:
            c = classes[m.group(1)]
            return c + "." + methods.get((c, m.group(2)), m.group(2))
        return name or "?"

    durations = collections.defaultdict(list)
    buckets = {label: collections.Counter() for label, _ in PHASES}
    swipes = 0

    for path in sys.argv[2:]:
        tp = TraceProcessor(trace=path)
        q = lambda s: list(tp.query(s))  # noqa: E731
        main_utid = q(
            f"select utid from thread join process using(upid) "
            f"where process.name = '{PKG}' and is_main_thread"
        )[0].utid
        frames = {r.id: deob(r.name) for r in q("select id, name from stack_profile_frame")}
        callsites = {r.id: (r.parent_id, r.frame_id) for r in q(
            "select id, parent_id, frame_id from stack_profile_callsite")}
        stack_cache = {}

        def bucket_of(callsite):
            if callsite in stack_cache:
                return stack_cache[callsite]
            names, c = [], callsite
            while c is not None:
                parent, frame = callsites[c]
                names.append(frames.get(frame, "?"))
                c = parent
            found = "other"
            for rules in (OWNERS, STRUCTURE):
                hit = next((b for name in names for b, keys in rules if any(k in name for k in keys)), None)
                if hit:
                    found = hit
                    break
            stack_cache[callsite] = found
            return found

        samples = q(f"select ts, callsite_id from perf_sample where utid = {main_utid} and callsite_id is not null")
        # A swipe is the time from one ACTION_DOWN to the next. Each phase is summed per
        # swipe: paused composition resumes in chunks, and whether a phase nests inside an
        # idle_frame depends on whether it fitted the frame budget, so neither the slice
        # nor its parent is the unit.
        downs = [r.ts for r in q(
            "select ts from slice where name like 'dispatchInputEvent MotionEvent ACTION_DOWN%' order by ts")]
        downs = [t for i, t in enumerate(downs) if i == 0 or t - downs[i - 1] > 50e6]
        swipes += len(downs)
        for label, name in PHASES:
            spans = [(r.ts, r.ts + r.dur) for r in q(
                f"select s.ts, s.dur from slice s join thread_track tt on s.track_id = tt.id "
                f"where tt.utid = {main_utid} and s.name = '{name}' order by s.ts")]
            per_swipe = [0.0] * len(downs)
            for a, b in spans:
                k = next((j for j in range(len(downs) - 1, -1, -1) if downs[j] <= a), None)
                if k is not None:
                    per_swipe[k] += (b - a) / 1e6
            # The last swipe's page settles after the trace's input ends, and the first
            # swipe prefetches from a cold pool; both are kept, as measure-pager.sh keeps them.
            durations[label] += per_swipe
            i = 0
            for s in sorted(samples, key=lambda r: r.ts):
                while i < len(spans) and spans[i][1] <= s.ts:
                    i += 1
                if i < len(spans) and spans[i][0] <= s.ts:
                    buckets[label][bucket_of(s.callsite_id)] += 1
        tp.close()

    print(f"{len(sys.argv) - 2} trace(s), {swipes} swipes, ms per swipe\n")
    print(f"{'phase':22} {'n':>4} {'median':>7} {'p90':>6} {'max':>6}   ms")
    for label, _ in PHASES:
        d = sorted(durations[label])
        if d:
            p90 = d[min(len(d) - 1, int(0.9 * len(d)))]
            print(f"{label:22} {len(d):4d} {statistics.median(d):7.2f} {p90:6.2f} {d[-1]:6.2f}")

    for label, _ in PHASES:
        c = buckets[label]
        n = sum(c.values())
        if not n or not durations[label]:
            continue
        med = statistics.median(durations[label])
        print(f"\n{label.strip()}: {n} samples, median {med:.2f} ms")
        for b, v in c.most_common():
            print(f"  {100 * v / n:5.1f}%  ~{med * v / n:5.2f} ms  {b}")


if __name__ == "__main__":
    main()
