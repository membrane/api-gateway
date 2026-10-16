# Summarizes a gateway gc.log written with -Xlog:gc*,safepoint:...:timemillis,uptime,level,tags
# for the client's measured window. Pass -v start=<epoch ms> -v end=<epoch ms> (MEASURED_WINDOW).
# Handles Parallel, G1 and generational ZGC logs. Each event is assigned by the epoch-ms decorator
# of its log line, which the JVM writes when the pause ends. Events before the window come from
# warmup (or from earlier runs when the gateway was not restarted) and are reported separately;
# events after it are ignored. Decimal commas (JVM running under e.g. a German locale) are accepted.
# Kept POSIX: runs under BSD awk (macOS) and mawk (Ubuntu). Run it with LC_ALL=C so the output
# uses decimal points.

# Parses "1.234" or "1,234" without relying on the awk's locale-dependent string-to-number
# conversion.
function num(s,    sep, fraction) {
    sep = index(s, ".")
    if (sep == 0) sep = index(s, ",")
    if (sep == 0) return s + 0
    fraction = substr(s, sep + 1)
    return substr(s, 1, sep - 1) + fraction / 10 ^ length(fraction)
}

function mb(s,    unit, value) {
    unit = substr(s, length(s), 1)
    value = substr(s, 1, length(s) - 1) + 0
    if (unit == "K") return value / 1024
    if (unit == "G") return value * 1024
    return value
}

# Shell sort, ascending, of a[1..n].
function sortn(a, n,    gap, i, j, v) {
    for (gap = int(n / 2); gap > 0; gap = int(gap / 2))
        for (i = gap + 1; i <= n; i++) {
            v = a[i]
            for (j = i; j > gap && a[j - gap] > v; j -= gap) a[j] = a[j - gap]
            a[j] = v
        }
}

{
    if (!match($0, /\]\[[0-9]+ms\]/)) next
    t = substr($0, RSTART + 2, RLENGTH - 5) + 0
    if (t > end) next
    inWindow = t >= start
}

# Pause lines end with their duration ("... Pause Young (Normal) (G1 Evacuation Pause) 120M->20M(256M) 1.234ms",
# "... Y: Pause Mark Start 0.012ms"). The gc,start line announcing the same pause has no duration.
/ Pause / && $NF ~ /^[0-9]+([.,][0-9]+)?ms$/ && $0 !~ /\[gc,start *\]/ {
    d = num(substr($NF, 1, length($NF) - 2))
    if (inWindow) {
        pauses[++n] = d
        total += d
        if (d > max) { max = d; maxAt = t }
        if ($0 ~ / Pause Full /) full++
    } else {
        preN++
        if (d > preMax) preMax = d
    }
}

# Heap after a collection, from the [gc] summary line: "120M->20M(256M)" (Parallel, G1) or
# "196M(77%)->44M(17%)" (ZGC). The last value is a point on a sawtooth; the minimum is the lower
# envelope, which is what grows across soak runs if memory leaks.
/\[gc *\]/ && /->/ && inWindow {
    after = substr($0, index($0, "->") + 2)
    if (match(after, /^[0-9]+[KMG]/)) {
        heapAfter = mb(substr(after, 1, RLENGTH))
        if (heapMin == "" || heapAfter < heapMin) heapMin = heapAfter
    }
}

# CPU per collection: "User=0.01s Sys=0.00s Real=0.00s". ZGC does not log these.
/\[gc,cpu *\]/ && inWindow {
    for (i = 1; i <= NF; i++)
        if ($i ~ /^(User|Sys)=/) {
            v = substr($i, index($i, "=") + 1)
            gcCpu += num(substr(v, 1, length(v) - 1))
            haveCpu = 1
        }
}

# ZGC: a request thread that allocates faster than the collector frees memory blocks until it can,
# "Allocation Stall (router /10.0.0.1:40030) 1.893ms". No safepoint is involved, so this shows up
# neither as a pause nor as a safepoint. Each line is one thread; several stall at the same time.
/Allocation Stall \(/ && $NF ~ /^[0-9]+([.,][0-9]+)?ms$/ && inWindow {
    d = num(substr($NF, 1, length($NF) - 2))
    stallN++
    stallTotal += d
    if (d > stallMax) { stallMax = d; stallAt = t }
}

# Every safepoint, GC or not: 'Safepoint "G1CollectForAllocation", ..., Total: 950833 ns'. Total
# includes the time to reach the safepoint, which the GC's own pause figure leaves out.
/Safepoint "/ && $NF == "ns" {
    d = $(NF - 1) / 1000000
    if (inWindow) {
        spN++
        spTotal += d
        if (d > spMax) {
            spMax = d
            spAt = t
            spName = substr($0, index($0, "Safepoint \"") + 11)
            spName = substr(spName, 1, index(spName, "\"") - 1)
        }
    } else if (d > preSpMax) preSpMax = d
}

END {
    p99 = 0
    if (n > 0) {
        sortn(pauses, n)
        p99 = pauses[int((99 * n + 99) / 100)]
    }
    share = 0
    if (end > start) share = 100 * total / (end - start)
    cpu = "n/a"
    if (haveCpu) cpu = sprintf("%.2fs", gcCpu)
    heap = "n/a"
    if (heapAfter != "") heap = sprintf("%.0fMB heapAfterMin=%.0fMB", heapAfter, heapMin)
    if (spName == "") spName = "-"
    printf "GC pauses=%d full=%d total=%.1fms max=%.2fms p99=%.2fms share=%.3f%% gcCpu=%s heapAfterLast=%s maxAt=%.0f\n", \
        n, full, total, max, p99, share, cpu, heap, maxAt
    printf "Safepoints n=%d total=%.1fms max=%.2fms op=%s maxAt=%.0f\n", \
        spN, spTotal, spMax, spName, spAt
    if (stallN > 0)
        printf "ZGC allocation stalls n=%d threadTotal=%.1fms max=%.2fms maxAt=%.0f\n", \
            stallN, stallTotal, stallMax, stallAt
    printf "Pre-window (warmup or earlier runs) GC pauses=%d max=%.2fms safepointMax=%.2fms\n", \
        preN, preMax, preSpMax
}
