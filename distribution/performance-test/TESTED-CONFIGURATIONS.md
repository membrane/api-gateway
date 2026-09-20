# Tested Configurations: Full History and CPU Detail

A catalog of every hardware / JVM / heap / backlog / concurrency combination measured across all
rounds of this test, with full CPU-busy detail per role. Purpose: when you change something and
re-run the test, check here first for whether it's already been tried, and add your own result
here afterwards so the next run has something to compare against.

**All figures below are single runs, not averages**, unless stated otherwise -- cloud VM
performance varies run to run, and several rows below that differ only by noise-level amounts
(a few percent) are *not* evidence of a real effect. Treat relative ordering and rough magnitude
as reliable; treat exact RPS as indicative only. See [SAMPLE-RESULTS.md](SAMPLE-RESULTS.md) for
the current reproducible defaults and their full methodology (warmup, timing, CPU sampling).

Scenario shorthand used throughout: **1** = `shortcircuit`, **2** = `fullproxy`, **3** =
`openapi-validation`. Body: "33B" = `{"name":"Mangos","price":2.79}`; "1KB" = the padded JSON body
in `run-scenario.sh`. CPU columns are gateway/backend/client busy % (100% - idle%, averaged over
active seconds only); "--" means the role was idle/unused (scenario 1 has no backend).

These historical CPU figures used an activity threshold, which could include startup and warmup.
They have not been remeasured with the current scripts' measured-window CPU sampling and should
not be compared directly with new CPU results. See README.md for the corrected methodology.

## Current defaults

`Standard_FX16mds_v2` gateway, `Standard_F16as_v7` backend, `Standard_F16as_v6` client, Temurin
21, `-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC`, backlog 8192/somaxconn 65535
(scenario 2 only), concurrency 175. See [SAMPLE-RESULTS.md](SAMPLE-RESULTS.md) for the full
current numbers -- not repeated here, this page is about everything *else* that was tried.

## 1. Hardware comparisons

### 1a. Uniform VM family, early rounds (33B body, concurrency 125 unless noted)

| Hardware | JVM | Scenario | RPS | GW | BE | CL |
|---|---|---|---:|---:|---:|---:|
| `Standard_E16s_v5` (uniform) | Java 25 | 1 | 137,314 | 79.4% | -- | 47.0% |
| `Standard_E16s_v5` (uniform) | Java 25 | 2 | 96,544 | 84.3% | 20.1% | 33.5% |
| `Standard_E16s_v5` (uniform) | Java 25 | 3 | 69,254 | 84.6% | 12.3% | 25.1% |
| `Standard_F16as_v6` (uniform) | Java 21 | 1 | 148,404 | -- | -- | -- |
| `Standard_F16as_v6` (uniform) | Java 21 | 2 | 100,780 | -- | -- | -- |
| `Standard_F16as_v6` (uniform) | Java 21 | 3 | 76,090 | -- | -- | -- |

**Caveat**: `E16s_v5` vs `F16as_v6` conflates VM family *and* JVM version (25 vs 21) -- not a
controlled single-variable comparison. `F16as_v6` came out ahead in every scenario, consistent
with the JVM-comparison finding below (Java 21 beats 25 on its own) rather than proving the
hardware difference alone.

Concurrency sweep on `F16as_v6` uniform (Java 21, 33B body):

| Scenario | 125 | 150 | 200 |
|---|---:|---:|---:|
| 1 | 148,404 | **153,291** | 151,880 |
| 2 | 100,780 | **102,902** | 98,631 |
| 3 | 76,090 | **77,828** | 75,580 |

Flat plateau, 150 marginally ahead -- this is the sweep behind the original (now superseded)
concurrency-125 default and the numbers in the first published [WEBSITE-ARTICLE.md](WEBSITE-ARTICLE.md).

### 1b. Mixed hardware vs. uniform `F16as_v6`, single-variable-controlled

Same JVM (Temurin 21), same heap tuning (32GB, ParallelGC, `AlwaysPreTouch`), same concurrency
(175), same backlog settings -- only hardware differs:

| Hardware | Scenario | RPS | GW | BE | CL |
|---|---|---:|---:|---:|---:|
| Uniform `F16as_v6`×3 | 1 | 154,528.5 | 63.1% | -- | 40.2% |
| Uniform `F16as_v6`×3 | 2 | 104,779.4 | 77.9% | 22.7% | 34.2% |
| Uniform `F16as_v6`×3 | 3 | 85,312.0 | 79.5% | 13.8% | 26.9% |
| **Mixed (current defaults)** | 1 | **165,792.0** | 71.2% | -- | 43.9% |
| **Mixed (current defaults)** | 2 | **128,723.4** | 75.4% | 17.0% | 39.6% |
| **Mixed (current defaults)** | 3 | **89,196.6** | 85.9% | 11.2% | 26.5% |

Mixed hardware wins every scenario: +7.3% (1), **+22.9% (2)**, +4.6% (3). This is the single
strongest, most reproducible-looking lever found in this whole test -- bigger than any JVM or
heap-tuning effect below.

### 1c. Step-by-step migration path from uniform baseline to mixed hardware

All rows use scenario 2 (fullproxy, 1KB body) as the probe, one variable changed per row:

| Step | Gateway | Backend | Client | JVM | Concurrency | RPS | GW busy |
|---|---|---|---|---|---:|---:|---:|
| Baseline | F16as_v6 | F16as_v6 | F16as_v6 | Temurin 21 | 125 | 102,830 | 78.9% |
| Backend swap | F16as_v6 | **F16as_v7** | F16as_v6 | Temurin 21 | 125 | 97,735 | 79.3% |
| Gateway swap | **FX16mds_v2** | F16as_v7 | F16as_v6 | Temurin 21 | 125 | 106,502 | 78.3% |
| JVM swap | FX16mds_v2 | F16as_v7 | F16as_v6 | **GraalVM CE 21.0.2** | 125 | 103,998 | 68.9% |
| Concurrency swap | FX16mds_v2 | F16as_v7 | F16as_v6 | GraalVM CE 21.0.2 | **160** | 118,708 | 81.0% |

The lone backend-VM swap (v6→v7) made things slightly *slower* (-5.0%) despite the backend never
being the bottleneck either way -- most plausibly noise, not repeated to confirm. The gateway
swap (v6→`FX16mds_v2`, a memory-optimized/EDA-class family not typically marketed for this kind
of workload) gave the first real, sizeable win.

## 2. JVM comparison

Same hardware and heap settings within each block; only the JVM changes.

### 2a. On uniform `F16as_v6`, scenario 2, near-each-JVM's-own-best concurrency

| JVM | Concurrency | RPS | GW busy |
|---|---:|---:|---:|
| Temurin 21 | 125 | 102,830 | 78.9% |
| Temurin 25 | 150 | 93,039 | ~68% |
| Temurin 26 | 150 | 96,403 | 68.6% |

Temurin 25/26 both show a **"lower CPU busy, lower RPS"** signature relative to 21 -- suggestive
of a change in the JDK's networking/IO path across major versions, not simply raw compute speed.
Never root-caused (would need JFR/async-profiler comparison, not attempted).

### 2b. On mixed hardware + 32GB pre-touched heap + ParallelGC, scenario 2, concurrency 175

| JVM | RPS | GW busy | BE busy | CL busy |
|---|---:|---:|---:|---:|
| Temurin 21 | 128,723.4 | 75.4% | 17.0% | 39.6% |
| GraalVM CE 21.0.2 | 123,728.2 | 79.8% | 14.7% | 35.6% |
| Temurin 25 | 123,654.5 | 78.9% | 17.7% | 39.2% |

Same pattern as 2a, now confirmed to persist even with GC/heap fully controlled for -- so it
isn't a GC artifact. GraalVM CE 21.0.2 is also a relatively old patch build (Jan 2024) vs.
Temurin 21.0.12.1, adding a minor version-recency confound to that specific comparison.

### 2c. On mixed hardware + 32GB heap, concurrency 175, **all three scenarios**

| JVM | Scenario | RPS | GW | BE | CL |
|---|---|---:|---:|---:|---:|
| Temurin 21 | 1 | 165,792.0 | 71.2% | -- | 43.9% |
| **GraalVM CE 21.0.2** | 1 | **177,997.2** | 76.3% | -- | 40.8% |
| Temurin 21 | 2 | 128,723.4 | 75.4% | 17.0% | 39.6% |
| GraalVM CE 21.0.2 | 2 | 125,800.2 | 77.3% | 17.4% | 36.6% |
| Temurin 21 | 3 | 89,196.6 | 85.9% | 11.2% | 26.5% |
| GraalVM CE 21.0.2 | 3 | 88,968.7 | 88.8% | 11.2% | 25.7% |

Breaks the pattern from 2a/2b: **GraalVM wins short-circuit by +7.4%** (using slightly *more* CPU,
the opposite signature), while scenarios 2 and 3 stay within noise of Temurin 21. Short-circuit is
the one scenario with no backend hop and no body work -- pure gateway dispatch -- so GraalVM's
JIT may genuinely win on tight, allocation-light code while losing (or tying) once a network hop
or JSON validation dilutes that. Single runs each; not repeated.

### 2d. On mixed hardware, GraalVM, con 175, scenario 2 -- heap size doesn't change the JVM finding

| Heap | RPS | GW busy |
|---|---:|---:|
| 16GB | 112,924.3 | 79.0% |
| 32GB | 125,800.2 | 77.3% |
| 128GB | 123,728.2 | 79.8% |

## 3. Heap size and GC tuning

All rows: scenario 2, mixed hardware, `-XX:+UseParallelGC -XX:+AlwaysPreTouch`, concurrency 125
unless noted. "Untuned" = JVM default heap sizing and default GC (G1).

| Heap | JVM | Concurrency | RPS | GW busy | GC pauses | Total GC pause |
|---|---|---:|---:|---:|---:|---:|
| Untuned (default) | Temurin 21 | 125 | 107,773.8 | 74.0% | -- | -- |
| 8GB | Temurin 21 | 125 | 108,910.5 | 69.0% | 60 | ~54ms |
| 128GB | Temurin 21 | 125 | 109,080.0 | 70.6% | 4 | ~14.7ms |
| 128GB | Temurin 21 | 175 | 128,065.0 | 83.2% | -- | -- |
| 128GB | Temurin 21 | 220 | 129,146.7 | 75.8% | -- | -- |
| 128GB | Temurin 21 | 300 | 128,559.1 | 76.4% | -- | -- |
| 32GB | Temurin 25 | 175 | 130,196.6 | 80.9% | -- | -- |
| 127GB | Temurin 25 | 175 | **132,371.3** | 73.2% | -- | -- |
| 128GB | Temurin 25 | 175 | 123,654.5 | 78.9% | -- | -- |

**Findings**:
- Fixed/pre-touched heap + ParallelGC uses noticeably less gateway CPU than the untuned default
  (69-71% vs. 74%) for the same or slightly better throughput at concurrency 125 -- a real,
  mechanistically-understood win (fewer/cheaper GC pauses: total pause time stayed under 100ms
  across a full 1,000,000-request run at every heap size tried, vs. G1's default adaptive
  behavior). It shows up as CPU headroom, not extra RPS, until concurrency is also raised (see
  the 125→175 jump: +17.4%).
- Heap size *itself* (8GB through 128GB) makes no consistent difference once GC is already tuned
  -- bigger heaps just mean fewer, individually-larger GC pauses, not faster requests. RPS across
  32GB/127GB/128GB at concurrency 175 spans 123.7k-132.4k with no monotonic trend, which reads as
  noise, not a real heap-size effect -- **except that 128GB is the lowest value on 3 separate
  occasions** (Temurin 21 and Temurin 25 heap sweeps, and the GraalVM heap sweep in 2d) despite
  being the largest heap tried each time. Worth a repeat run and/or a `numactl --hardware` check
  on the gateway VM before trusting this as a real effect (untested as of this writing) -- 128 is
  a suspiciously round number that could be brushing a NUMA-node-size or similar machine-specific
  boundary.

## 4. TCP accept-queue (`backlog`) experiment

Scenario 2, mixed hardware, Temurin 21, 128GB heap + ParallelGC. Kernel `net.core.somaxconn`
raised to 65535 on the gateway VM to actually allow backlog values above the kernel default
(1024/4096 depending on image) before this test.

| Backlog | Concurrency | RPS | GW busy |
|---|---:|---:|---:|
| 1024 | 175 | 128,065.0 | 83.2% |
| 8192 | 175 | 125,457.4 | 84.3% |
| 1024 | 300 | 128,559.1 | 76.4% |
| 8192 | 300 | 127,757.7 | 77.5% |

**No measurable effect either way** (differences under 2%, within noise) -- 0 errors at every
backlog value tried, meaning the accept queue never actually overflowed, so raising its ceiling
changed nothing. `backlog` only matters once connections are actually queuing up waiting to be
accepted; this workload's bottleneck is elsewhere. Kept at 8192 in the shipped config anyway as a
no-cost safety margin, not because it measurably helps.

## 5. Concurrency sweep (mixed hardware, 128GB heap, Temurin 21, scenario 2)

| Concurrency | RPS | GW busy | BE busy | CL busy |
|---:|---:|---:|---:|---:|
| 125 | 109,080.0 | 70.6% | 12.3% | 35.6% |
| **175** | **128,065.0** | 83.2% | 18.1% | 41.4% |
| 220 | 129,146.7 | 75.8% | 16.2% | 40.1% |
| 300 | 128,559.1 | 76.4% | 19.1% | 41.5% |

Plateau reached at 175; 220 and 300 are flat (within noise) despite gateway CPU actually *dropping*
a few points past 175 -- none of gateway (~76-83%), backend (~12-19%), or client (~35-42%) is
saturated at any point tried, so the ~128-129k ceiling isn't a raw CPU limit on any one machine.
The backlog experiment above rules out the accept queue as the explanation. Most likely
explanation not yet tested: per-connection/per-request I/O-path overhead (thread scheduling,
socket syscalls) rather than a queue or memory limit -- would need profiling (async-profiler/JFR)
to confirm.

## 6. Payload size sensitivity (uniform `F16as_v6`, Java 21, scenario 2)

| Body size | Concurrency | RPS |
|---|---:|---:|
| 33 B (small) | 150 | 102,902 |
| ~1 KB | 150 | 99,518 |
| ~1 KB | 125 | 102,830 |

The bigger body costs a few percent at a fixed concurrency, recovered by dropping concurrency
slightly -- the plateau's sweet spot shifts a little with payload size. This is the origin of
`fullproxy` using the 1KB body by default while `shortcircuit`/`openapi-validation` keep the
33-byte body.

## Open questions (not root-caused)

- **Why do Java 25/26 and GraalVM CE consistently show "less gateway CPU, same-or-less
  throughput" vs. Temurin 21** on scenarios 2 and 3, while GraalVM *wins* on scenario 1? (Sections
  2a/2b/2c.) Needs JFR/async-profiler comparison between JVMs on the same request path.
- **Is the 128GB-heap dip in section 3 real, or noise?** Seen 3 times across 2 JVMs at slightly
  different concurrencies/backlogs, always the *lowest* of the heap sizes tried despite being the
  largest. No repeat runs or NUMA topology check done yet.
- **What actually caps throughput at ~128-129k RPS on the current default hardware** (section 5),
  given no role's CPU is saturated and the backlog isn't the limit? Needs profiling, not another
  black-box parameter sweep.
