# Usage: LC_ALL=C awk -v tol=1 -f match-slow.awk gateway-gc.log client-gc.log slow-requests.txt
# Attributes each slow request (due epoch ms, latency ms) to a stop-the-world safepoint of the
# gateway or of the client that overlaps [due, due + latency] (+/- tol ms; timestamps are ms).
# Prints counts per latency bucket and cause, plus the 10 slowest requests with their cause.
function num(s,    sep, f) { sep = index(s, "."); if (!sep) sep = index(s, ","); if (!sep) return s + 0; f = substr(s, sep + 1); return substr(s, 1, sep - 1) + f / 10 ^ length(f) }
{ file = FILENAME == ARGV[1] ? 1 : FILENAME == ARGV[2] ? 2 : 3 }
file <= 2 && /Safepoint "/ && match($0, /Total: [0-9]+ ns/) {
    d = substr($0, RSTART + 7, RLENGTH - 10) / 1000000
    match($0, /\]\[[0-9]+ms\]/); t = substr($0, RSTART + 2, RLENGTH - 5) + 0
    n = ++sp[file]; spEnd[file, n] = t; spStart[file, n] = t - d; spDur[file, n] = d
    next
}
file == 3 {
    due = num($1); lat = num($2); cause = "other"; stw = 0
    for (f = 1; f <= 2 && cause == "other"; f++)
        for (i = 1; i <= sp[f]; i++)
            if (spStart[f, i] - tol <= due + lat && spEnd[f, i] + tol >= due && spDur[f, i] >= 0.2) {
                cause = f == 1 ? "gateway-pause" : "client-pause"; stw = spDur[f, i]; break
            }
    b = lat < 2 ? "1-2ms" : lat < 5 ? "2-5ms" : lat < 10 ? "5-10ms" : ">=10ms"
    cnt[b, cause]++; tot[cause]++; all++
    slowLat[all] = lat; slowCause[all] = cause; slowDue[all] = due; slowStw[all] = stw
}
END {
    printf "slow requests: %d (gateway safepoints %d, client safepoints %d)\n", all, sp[1], sp[2]
    split("1-2ms 2-5ms 5-10ms >=10ms", bs, " ")
    printf "%-8s %14s %13s %8s\n", "latency", "gateway-pause", "client-pause", "other"
    for (k = 1; k <= 4; k++)
        printf "%-8s %14d %13d %8d\n", bs[k], cnt[bs[k], "gateway-pause"], cnt[bs[k], "client-pause"], cnt[bs[k], "other"]
    printf "%-8s %14d %13d %8d\n", "total", tot["gateway-pause"], tot["client-pause"], tot["other"]
    for (r = 1; r <= 10 && r <= all; r++) {
        m = 0
        for (i = 1; i <= all; i++) if (!used[i] && (m == 0 || slowLat[i] > slowLat[m])) m = i
        used[m] = 1
        printf "  %.3f ms at %.0f: %s%s\n", slowLat[m], slowDue[m], slowCause[m], slowStw[m] ? sprintf(" (%.2f ms)", slowStw[m]) : ""
    }
}
