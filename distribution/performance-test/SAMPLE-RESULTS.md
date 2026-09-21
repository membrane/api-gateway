# Sample Results: How Fast Is Membrane?

This is a historical example run of scenarios described in [README.md](README.md), using the
current defaults baked into `provision.sh`/`run-scenario.sh`, included to show what the output
looks like and how to interpret it. **These exact numbers are not a certified benchmark** -- see
"Scope and limitations" below. For the full history of what else was tried (other VM sizes, JVM
versions, heap sizes, backlog values, concurrency levels) and why the current defaults were
chosen, see [TESTED-CONFIGURATIONS.md](TESTED-CONFIGURATIONS.md).

## Summary

| Scenario | Requests/sec | Gateway CPU busy | Backend CPU busy | Client CPU busy |
|---|---:|---:|---:|---:|
| 1. `shortcircuit` (best case, unrealistic) | **165,792** | 71.2% | -- (unused) | 43.9% |
| 2. `fullproxy` (realistic, but no gateway value-add) | **128,723** | 75.4% | 17.0% | 39.6% |
| 3. `openapi-validation` (real gateway task) | **89,197** | 85.9% | 11.2% | 26.5% |
| 5. `rate-limit-basic-auth-tls` (real gateway task, TLS both hops) | **109,542** | 94.8% | 14.5% | 46.9% |

All runs above completed with **0 errors** out of 1,000,000 requests each, at concurrency 175.
Scenarios 1 and 3 use the same fixed 33-byte JSON body; scenarios 2 and 5 use a more realistic
~1 KB JSON body (see "How this run was executed" below) -- so, unlike earlier rounds of this test,
these numbers are not a strict apples-to-apples comparison against each other, only against their
own prior same-body measurements in TESTED-CONFIGURATIONS.md. Scenario 4
(`rate-limit-basic-auth`, the plaintext sibling scenario 5 is built on) has no sample entry here
yet, so scenario 5's TLS overhead can't be isolated by subtraction in this document -- see
README.md for what each scenario adds on top of the last. Scenario 5 was also measured in a
separate run (different date/VM instances, same defaults) from scenarios 1-3, not one combined
session -- see its own "How this run was executed" note below.

- **Routing to a real backend costs real throughput** (165,792 → 128,723 RPS) compared to the
  gateway answering directly, even though the backend itself is barely busy (17.0%). Part of that
  gap is the extra network hop and thread hand-off; part is the larger, more realistic request
  body. This is the ceiling for what a gateway can do if it isn't doing any actual gateway work.
- **OpenAPI request validation costs a further chunk of throughput** (128,723 → 89,197 RPS,
  though note this step also drops back to the small 33-byte body). The gateway gets noticeably
  *more* CPU-busy (85.9% vs. 75.4%) -- this is real added CPU work (JSON Schema validation of
  every request body), not a shift in where the bottleneck sits. This is the cost of the gateway
  actually doing something a plain reverse proxy couldn't.
- **The gateway is the bottleneck in every setup, but never fully saturates.** Its CPU
  utilization is the highest of the three roles throughout (71-86% busy) and stays below 100% in
  every scenario, while backend and client both have consistent headroom -- see
  TESTED-CONFIGURATIONS.md for what we found (and didn't find) when trying to push past that.
- **TLS on both hops (scenario 5) leaves the gateway more CPU-busy than any other scenario here**
  (94.8%), consistent with TLS adding real CPU-bound handshake/encryption work on top of
  everything else the gateway is already doing -- though its CPU figure isn't directly comparable
  to scenarios 1-3's (different measurement method, see "How this run was executed" below).
  Client CPU busy (46.9%) stayed well short of the gateway's, so the gateway -- not the load
  generator, despite it now also paying its own TLS handshake/encryption cost -- is still what
  this run measures.

## How this run was executed

- **Infrastructure**: 3 separate Azure VMs, one VNet, region Sweden Central. Gateway:
  `Standard_FX16mds_v2` (16 vCPUs, memory-optimized/EDA-class). Backend: `Standard_F16as_v7`
  (16 vCPUs, compute-optimized). Client: `Standard_F16as_v6` (16 vCPUs, compute-optimized) --
  a deliberately non-uniform mix, found to outperform a uniform choice for every role (see
  TESTED-CONFIGURATIONS.md, "Hardware comparison").
- **Software**: the real distribution zip via `membrane.sh -c <config>` -- the actual production
  startup path, not an embedded test router. Eclipse Temurin 21 (LTS) on all three machines --
  the fastest of the JVM versions/vendors we tried for this workload (Temurin 21/25/26 and
  GraalVM CE 21.0.2, see TESTED-CONFIGURATIONS.md, "JVM comparison").
- **JVM tuning**: a fixed, pre-touched 32 GB heap (`-Xms32g -Xmx32g -XX:+AlwaysPreTouch`) with
  `-XX:+UseParallelGC` on the gateway. Total GC pause time measured well under 100ms across a
  full 1,000,000-request run.
- **TCP accept-queue**: `<transport backlog="8192"/>` for `fullproxy` (kernel `somaxconn` raised
  to 65535 via `provision.sh`'s cloud-init to allow it) -- measured to make no throughput
  difference by itself, but kept so it never becomes a limiting factor if load/hardware changes.
- **Load generator** (`java/LoadTesterClient.java`): `POST /shop/v2/products`. `shortcircuit` and
  `openapi-validation` use a fixed minimal JSON body (`{"name":"Mangos","price":2.79}`, 33 bytes);
  `fullproxy` uses a padded ~1 KB JSON body -- a more realistic request size than a few-byte
  payload. 175 concurrent in-flight requests (found empirically to be this hardware's throughput
  plateau -- see TESTED-CONFIGURATIONS.md, "Concurrency sweep"); 10,000 untimed warmup requests
  discarded before the measured phase; 1,000,000 measured requests per scenario, timed wall-clock
  from first request submitted to last response received (RPS = 1,000,000 / elapsed seconds).
- **Historical CPU measurement**: `mpstat -P ALL 1` sampled every second, retaining samples
  with more than 1% activity. This filter could include startup, warmup, and unrelated activity;
  it did not isolate the measured phase. "CPU busy" = 100% - idle%. The current scripts use
  complete `/proc/stat` intervals inside the client's measured window instead. The CPU figures
  above have not been remeasured with that corrected method.
- **Scenario 5 (`rate-limit-basic-auth-tls`), measured separately** (2026-09-21, its own
  provision/deploy/teardown cycle, same VM sizes/JVM/JVM tuning/backlog/concurrency/body as
  above): the gateway terminates TLS on its client-facing `api` and connects to the backend over
  TLS too, both legs using self-signed certs generated fresh per deploy by `deploy-and-run.sh`
  (`keytool`, SAN set to each VM's actual private IP). Its CPU figures use the corrected
  measured-window method (complete `/proc/stat` intervals), not the historical `mpstat` method
  scenarios 1-3 used, so they are directly comparable to each other but not compared here against
  a same-method scenario 1-3 number.

## Scope and limitations

- Each number is a **single run**, not an average of repeated trials -- cloud VM performance has
  run-to-run variance, and several configurations in TESTED-CONFIGURATIONS.md show 2-7% swings
  between otherwise-identical runs. Treat the relative ordering and rough magnitude as reliable;
  treat exact RPS figures as indicative, not a certified benchmark result.
- The backend in scenarios 2, 3, and 5 is a trivial echo (`java/LoadTesterBackend.java`), not a
  real service with its own business logic or database -- it isolates gateway-side cost
  specifically, which is the point of this test, but it is not a realistic backend latency
  profile.
- Concurrency 175 and the current VM sizes were chosen together, from the sweeps recorded in
  TESTED-CONFIGURATIONS.md -- both are very unlikely to be optimal for different hardware.
  Re-sweep if you change `*_SIZE`.
- `fullproxy`'s body size (~1 KB) is not identical to `shortcircuit`'s and
  `openapi-validation`'s (33 B) -- the three scenarios' RPS numbers are not a clean
  apples-to-apples comparison against each other, only against their own prior same-body
  measurements.
