# GC under typical load: fullproxy at 1000 req/s (2026-10-21)

Question: how often and for how long does GC run in the gateway under a typical load (1000 req/s),
rather than at full throughput?

## Setup

- Azure, 3 VMs (client, gateway, backend), Standard_F16as_v7, set up with `../provision.sh`.
- Gateway: master of about 2026-10-16, scenario `fullproxy` (1 KB JSON POST, forwarded to the
  backend).
- Client: open loop at a fixed 1000 req/s (latency timed from each request's due time), 10 s
  warmup, 60 s measured (60,000 requests). Gateway restarted for every run.
- JDKs: Temurin 21.0.12.1 and 25.0.4.1. Heap set with `-Xmx` only; "16 GB" is the JVM default
  (1/4 of RAM).
- Collectors: G1 (default), generational ZGC (`-XX:+UseZGC`, plus `-XX:+ZGenerational` on 21),
  Shenandoah (`-XX:+UseShenandoahGC`; on 25 with `-XX:ShenandoahGCMode=generational`).
- One run per configuration.

## Results

| Config | Heap | Collections (interval) | Pauses | Longest pause | Total pause | Concurrent GC | JVM CPU | GC CPU (% of JVM) | Latency p99 | Latency max |
|---|---|---|---|---|---|---|---|---|---|---|
| 21 G1 | 16 GB | 39 (1.5 s) | 39 | 2.90 ms | 56.6 ms | – | n/a¹ | n/a¹ | 0.33 ms | 5.5 ms |
| 21 G1 | 100 MB | 56 (1.1 s) | 56 | 1.11 ms | 48.5 ms | – | 3.14 s | 0.17 s² (5 %) | 0.34 ms | 3.4 ms |
| 21 G1 | 250 MB | 43 (1.4 s) | 43 | 1.27 ms | 38.6 ms | – | 3.27 s | 0.15 s (5 %) | 0.33 ms | 4.5 ms |
| 21 G1 | 4 GB | 54 (1.1 s) | 54 | 2.44 ms | 51.8 ms | – | 3.21 s | 0.20 s (6 %) | 0.33 ms | 4.2 ms |
| 21 ZGC | 16 GB | 1 (60 s) | 5 | 0.03 ms | 0.1 ms | 29 ms | 3.90 s | 0.17 s (4 %) | 0.87 ms | 5.7 ms |
| 21 ZGC | 100 MB | 62 (1.0 s) | 238 | 0.02 ms | 1.3 ms | 1174 ms | 4.20 s | 1.20 s (29 %) | 0.33 ms | 8.0 ms³ |
| 21 ZGC | 250 MB | 28 (2.1 s) | 136 | 0.02 ms | 0.7 ms | 805 ms | 3.92 s | 1.04 s (27 %) | 0.32 ms | 6.5 ms |
| 21 ZGC | 4 GB | 5 (12 s) | 25 | 0.03 ms | 0.2 ms | 206 ms | 3.47 s | 0.37 s (11 %) | 0.33 ms | 4.3 ms |
| 21 Shenandoah | 16 GB | 0 | 0 | – | 0 ms | 0 ms | 4.42 s | 0.39 s (9 %)⁴ | 0.34 ms | 6.9 ms |
| 21 Shenandoah | 100 MB | 36 (1.7 s) | 109 | 0.36 ms | 6.7 ms | 144 ms | 4.02 s | 0.93 s (23 %)⁴ | 0.31 ms | 4.4 ms |
| 21 Shenandoah | 250 MB | 12 (5 s) | 37 | 0.20 ms | 2.4 ms | 62 ms | 3.63 s | 0.62 s (17 %)⁴ | 0.32 ms | 6.9 ms |
| 21 Shenandoah | 4 GB | 3 (20 s) | 11 | 0.22 ms | 0.9 ms | 69 ms | 3.80 s | 0.64 s (17 %)⁴ | 0.33 ms | 6.0 ms |
| 25 G1 | 16 GB | 27 (2.2 s) | 27 | 2.44 ms | 31.2 ms | – | 3.10 s | 0.14 s (5 %) | 0.29 ms | 7.0 ms |
| 25 G1 | 100 MB | 50 (1.2 s) | 50 | 5.74 ms | 48.1 ms | – | 2.98 s | 0.18 s (6 %) | 0.33 ms | 7.1 ms |
| 25 G1 | 250 MB | 43 (1.4 s) | 43 | 1.22 ms | 34.5 ms | – | 2.93 s | 0.17 s (6 %) | 0.32 ms | 13.0 ms |
| 25 G1 | 4 GB | 39 (1.5 s) | 39 | 1.28 ms | 35.5 ms | – | 2.89 s | 0.16 s (6 %) | 0.32 ms | 4.9 ms |
| 25 ZGC | 16 GB | 1 (60 s) | 5 | 0.01 ms | 0.0 ms | 18 ms | 3.81 s | 0.13 s (3 %) | 0.84 ms | 10.5 ms |
| 25 ZGC | 100 MB | 62 (1.0 s) | 288 | 0.01 ms | 1.1 ms | 1052 ms | 4.15 s | 1.09 s (26 %) | 0.33 ms | 11.3 ms |
| 25 ZGC | 250 MB | 42 (1.4 s) | 208 | 0.01 ms | 0.7 ms | 915 ms | 4.10 s | 0.99 s (24 %) | 0.37 ms | 17.4 ms |
| 25 ZGC | 4 GB | 4 (15 s) | 20 | 0.03 ms | 0.2 ms | 117 ms | 3.40 s | 0.23 s (7 %) | 0.33 ms | 5.9 ms |
| 25 Shenandoah gen. | 16 GB | 1 (60 s) | 2 | 0.29 ms | 0.4 ms | 23 ms | 4.74 s | 0.85 s (18 %)⁴ | 0.34 ms | 6.4 ms |
| 25 Shenandoah gen. | 100 MB | 36 (1.7 s) | 144 | 0.24 ms | 9.7 ms | 108 ms | 3.99 s | 1.11 s (28 %)⁴ | 0.32 ms | 6.2 ms |
| 25 Shenandoah gen. | 250 MB | 12 (5 s) | 48 | 0.28 ms | 3.8 ms | 48 ms | 3.91 s | 0.98 s (25 %)⁴ | 0.34 ms | 4.8 ms |
| 25 Shenandoah gen. | 4 GB | 3 (20 s) | 10 | 0.25 ms | 0.9 ms | 26 ms | 3.56 s | 0.80 s (22 %)⁴ | 0.31 ms | 3.7 ms |

No run had client errors, full GCs or degenerated Shenandoah cycles.

1. The process CPU sampler stopped after 8 s in this run.
2. Counted with coarse 10 ms ticks (later runs use per-thread `schedstat` in ns); approximate.
3. The only run with ZGC allocation stalls: 7, longest 0.29 ms.
4. Mostly fixed overhead. A 10 s per-thread spot check on 25 Shenandoah 250 MB: control thread
   77 ms + regulator thread 71 ms, the 8 GC worker threads only 28 ms, i.e. ~85 % is the threads
   that poll whether to collect (~1.5 % of one core, independent of load). Not split per run.

Column notes: "Collections" are distinct `GC(N)` ids (ZGC/Shenandoah pause 2–5 times per
collection, G1 once). "Concurrent GC" is the wall time of concurrent phases (G1 concurrent mark
cycles, Shenandoah concurrent phases, whole ZGC collections). "GC CPU" is the CPU time of the GC
threads (`GC Thread`, `G1 *`, `Z*`, `Shenandoah *`; the VM Thread is not counted).

## Findings

- Only G1's pauses are in the millisecond range: one every 1–2 s, 1–3 ms (max 5.7 ms), 30–57 ms
  per minute, under 0.1 % of wall time. ZGC and Shenandoah never paused longer than 0.4 ms.
- GC CPU is small in absolute terms: the whole JVM uses 3–4.7 s CPU per minute (0.05–0.08 cores);
  the most expensive GC (ZGC 100 MB, 1.2 s/min) is about 2 % of one core.
- Relative to the JVM, G1 is the cheapest at ~5 %, independent of heap size. ZGC needs heap:
  26–29 % at 100 MB, 7–11 % at 4 GB, 3–4 % at the default heap. Shenandoah's 17–28 % is
  dominated by its polling threads (note 4).
- ZGC with the 16 GB default heap shows p99 0.84–0.87 ms (instead of ~0.33 ms) on both JDKs.
  Unconfirmed hypothesis: first-touch page faults on fresh heap memory (would need minflt
  counting, or `-XX:+AlwaysPreTouch`).
- Latency max values of 10–17 ms are not gateway GC: the longest pause and safepoint in those
  runs were at most ~1.3 ms. Likely the client JVM or the network (client GC is not logged).
- JDK 21 vs 25: no difference beyond the noise of single 1-minute runs.

## Influence on tail latency (5-minute runs)

Same setup, JDK 21, 250 MB heap, 5 minutes measured (300,000 requests). The client also reports
p99.9/p99.99, logs every request slower than 1 ms with its due time, and writes its own GC log.
`../match-slow.awk` matches each slow request against the stop-the-world safepoints of the gateway
and of the client that overlap it (±1 ms, safepoints of at least 0.2 ms).

| | G1 | ZGC | Shenandoah |
|---|---|---|---|
| p50 / p99 | 0.27 / 0.30 ms | 0.27 / 0.30 ms | 0.27 / 0.30 ms |
| p99.9 | 0.97 ms | 0.62 ms | 0.63 ms |
| p99.99 | 2.0 ms | 2.2 ms | 1.9 ms |
| max | 17.3 ms | 13.6 ms | 19.2 ms |
| Requests > 1 ms | 279 | 68 | 110 |
| … during a gateway pause | 199 | 0 | 0 |
| … during a client pause | 19 | 7 | 18 |
| Gateway pauses (longest) | 215 (2.8 ms) | 1123 (0.02 ms) | 187 (0.27 ms) |
| GC CPU (workers + control threads) | 0.52 + 0.01 s | 4.88 + 0.83 s | 0.78 + 1.73 s |
| JVM CPU | 11.5 s | 17.2 s | 14.6 s |

- Up to p99 the collector makes no difference. G1's 1–3 ms pauses delay about 200 of 300,000
  requests (0.07 %) by about a millisecond: p99.9 is 0.97 ms with G1 and 0.62 ms with ZGC or
  Shenandoah, whose slow requests never coincide with a gateway pause.
- From p99.99 on the collectors are equal (1.9–2.2 ms). The outliers of 5–19 ms occur with every
  collector and are not explained by gateway GC; the "during a gateway pause" match for G1's 17 ms
  request is an overlap with a 1.25 ms pause, which cannot account for it.
- ZGC buys its short pauses with about 10 times G1's GC CPU (5.7 s in 5 minutes, about 2 % of one
  core). Shenandoah's GC CPU is mostly its control and regulator threads (1.73 s), which poll
  regardless of load; its collecting workers used 0.78 s.
