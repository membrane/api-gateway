# Performance Test Results

Seven scenarios, each run 5 times with 10,000,000 measured requests per run, on three Azure VMs
(client, gateway, backend) connected over a real network. Measured on 2026-09-29 between 11:13 and
12:07 CEST. All 35 runs completed with **0 errors**.

| Scenario | What the gateway does | Concurrency | Mean RPS | CV | p50 ms | p95 ms | p99 ms |
|---|---|---:|---:|---:|---:|---:|---:|
| `shortcircuit` | Answers directly, no backend | 350 | **539,940** | 1.3 % | 0.41 | 1.24 | 6.38 |
| `fullproxy` | Forwards to the backend | 100 | **216,912** | 1.7 % | 0.31 | 1.57 | 3.07 |
| `openapi-validation` | Validates against an OpenAPI spec, then forwards | 100 | **167,211** | 1.2 % | 0.37 | 2.17 | 4.67 |
| `rate-limit-basic-auth` | Basic Auth + rate limiting, then forwards | 100 | **214,125** | 2.7 % | 0.32 | 1.49 | 3.00 |
| `rate-limit-basic-auth-tls` | Same, with TLS on both hops | 100 | **196,028** | 3.1 % | 0.34 | 1.61 | 3.20 |
| `wsdl2openapi` | JSON protection, 5 JSONPath headers, JSON to SOAP and back | 100 | **120,417** | 2.9 % | 0.58 | 2.01 | 7.04 |
| `soap-validation` | XML protection, WSDL validation of request and response | 100 | **97,884** | 1.4 % | 0.77 | 1.86 | 8.48 |

CV is the coefficient of variation of RPS over the 5 runs (standard deviation ÷ mean). Latencies
are the mean of the 5 runs' percentiles.

## Setup

### Topology

```
 ┌──────────┐        ┌──────────────┐        ┌──────────┐
 │  Client  │ ─────▶ │   Membrane   │ ─────▶ │ Backend  │
 │ lt-client│  :2000 │  lt-gateway  │  :2010 │lt-backend│
 │          │        │              │  :2011 │          │
 │          │        │              │  :2012 │          │
 └──────────┘        └──────────────┘        └──────────┘
```

Port 2010 is plain HTTP, 2011 is TLS, 2012 is the SOAP service. All three VMs are in one Azure
virtual network (`10.10.0.0/24`) and one proximity placement group in the **Sweden Central**
region, with accelerated networking enabled. Load travels over the VMs' private IPs only.

### Machines

| Role | Azure size | CPU | vCPUs | Threads per core | RAM | OS | Kernel |
|---|---|---|---:|---:|---:|---|---|
| Gateway | `Standard_F16as_v7` | AMD EPYC 9V45 (Turin) | 16 | 1 | 64 GiB | Ubuntu 24.04.4 LTS | 6.17.0-1022-azure |
| Backend | `Standard_F16as_v7` | AMD EPYC 9V45 (Turin) | 16 | 1 | 64 GiB | Ubuntu 24.04.4 LTS | 6.17.0-1022-azure |
| Client | `Standard_F16as_v7` | AMD EPYC 9V45 (Turin) | 16 | 1 | 64 GiB | Ubuntu 24.04.4 LTS | 6.17.0-1022-azure |

One thread per core: each vCPU is a full physical core, no SMT. `net.core.somaxconn` is 65535 on
all three VMs.

### Software

| Component | Details |
|---|---|
| JVM (all roles) | Eclipse Temurin 21.0.12.1+1 LTS |
| Gateway | Membrane API Gateway 7.6.3-SNAPSHOT distribution, started with `membrane.sh`. Built from master `11e25b79d` plus #3379 (not yet merged at the time): `HttpUtil.readLine` reads the start line and header lines in bulk from the connection's `BufferedInputStream` instead of byte by byte |
| Gateway JVM options | `-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC` (plus GC logging to `gc.log`) |
| Gateway router | `<router exchangeStore="forgetful">` with a `<forgetfulExchangeStore>`: exchanges are not kept for the admin console. `<transport backlog="1024"/>`, API on port 2000 |
| Backend | `java/LoadTesterBackend.java`: an embedded Membrane router. Ports 2010 (HTTP) and 2011 (TLS) answer every request with `200 OK` via a `return` interceptor; port 2012 answers every request with a fixed SOAP 1.1 envelope containing an empty `createPersonResponse`. Backlog 1024, default JVM options |
| Client | `java/LoadTesterClient.java` on AsyncHttpClient 3.0.12 (Netty 4.2.17), default JVM options. One virtual thread per unit of concurrency; each sends a request, waits for its response, then sends the next |

### Load

| Parameter | Value |
|---|---|
| Concurrency | Per scenario (see tables): that many requests in flight at all times (closed loop); at most that many connections, kept alive and reused |
| Warmup | 1,000,000 untimed requests before the measured phase of every run |
| Measured requests | 10,000,000 per run |
| Runs | 5 rounds; each round runs the 7 scenarios in the order of the tables |
| Gateway restart | The gateway is restarted with the scenario's configuration before every run |

### Scenarios

| Scenario | Gateway configuration (`conf/`) | Request |
|---|---|---|
| `shortcircuit` | `loadtest-shortcircuit.xml`: `<return statusCode="200"/>`, no backend | `POST /shop/v2/products`, `{"name":"Mangos","price":2.79}` (30 bytes) |
| `fullproxy` | `loadtest-fullproxy.xml`: `<target>` to backend port 2010 | `POST /shop/v2/products`, JSON, 1,066 bytes |
| `openapi-validation` | `loadtest-openapi-validation.xml`: `<openapi location="fruitshop-v2-2-0.oas.yml" validateRequests="yes" validateResponses="no"/>`, then backend port 2010 | `POST /shop/v2/products`, `{"name":"Mangos","price":2.79}` (30 bytes), valid against the `Product` schema |
| `rate-limit-basic-auth` | `loadtest-rate-limit-basic-auth.xml`: `basicAuthentication` (one static user), `rateLimiter` (`requestLimit="100000000"`, `PT1H`, keyed by client IP), then backend port 2010 | `POST /shop/v2/products`, JSON, 1,066 bytes |
| `rate-limit-basic-auth-tls` | `loadtest-rate-limit-basic-auth-tls.xml`: same as `rate-limit-basic-auth`, plus TLS on the client side (self-signed RSA 2048 keystore) and TLS to backend port 2011 (truststore with the backend's self-signed RSA 2048 certificate) | `POST /shop/v2/products`, JSON, 1,066 bytes |
| `wsdl2openapi` | `loadtest-wsdl2openapi.xml`: `<jsonProtection/>` (default limits); five `<setHeader>` with JSONPath values (`X-Name`, `X-Last-Name`, `X-Email`, `X-Customer-Number`, `X-City`); `<wsdl2openapi>` for `person-service.wsdl`, which turns the JSON into a SOAP `createPerson` request and the SOAP response back into JSON (`{}`); target backend port 2012 | `POST /create-person`, JSON, 414 bytes: a person with 10 scalar fields, a 3-element array and a 7-field address |
| `soap-validation` | `loadtest-soap-validation.xml`: `<xmlProtection/>` (default limits); `<validator wsdl="person-service.wsdl"/>`, which validates the SOAP request and the SOAP response; then backend port 2012 | `POST /person-service`, `text/xml`, 763 bytes: the same person as a SOAP 1.1 `createPerson` request |

`person-service.wsdl` is a synthetic document/literal SOAP 1.1 service with a single operation,
`createPerson`, whose response element is empty. In both rate-limit scenarios every request carries
valid credentials. The rate limit is far above the requests sent per run (11,000,000 including
warmup), so no request is rejected.

### What is measured

- **RPS**: measured requests divided by the wall-clock duration of the measured phase, as seen by
  the client.
- **OK / ERR**: responses with status below 400 count as OK; responses of 400 or above and
  transport failures count as ERR.
- **Latency**: per request, from handing it to the HTTP client until its response or failure
  arrives, over all 10,000,000 measured requests of a run, as nearest-rank percentiles. The client
  is closed loop, so latencies are not corrected for coordinated omission.
- **CPU**: sampled from `/proc/stat` once per second on each VM. Only complete sampling intervals
  inside the measured phase count (17 to 102 intervals per run). Busy = 100 % minus idle. Steal time
  was 0 on all VMs over the whole test.

## Results

| Scenario | Mean RPS | Median | Min | Max | Std dev | CV | p50 ms | p95 ms | p99 ms | Max ms | CPU gw | CPU be | CPU cl |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `shortcircuit` | **539,940** | 540,602 | 530,480 | 549,776 | 7,119 | 1.3 % | 0.41 | 1.24 | 6.38 | 68.4 | 94.9 % | 0.1 % | 90.8 % |
| `fullproxy` | **216,912** | 216,757 | 212,322 | 222,629 | 3,703 | 1.7 % | 0.31 | 1.57 | 3.07 | 11.6 | 89.4 % | 44.4 % | 60.5 % |
| `openapi-validation` | **167,211** | 167,065 | 164,438 | 169,868 | 2,019 | 1.2 % | 0.37 | 2.17 | 4.67 | 22.7 | 90.5 % | 28.6 % | 41.0 % |
| `rate-limit-basic-auth` | **214,125** | 212,882 | 207,392 | 220,142 | 5,775 | 2.7 % | 0.32 | 1.49 | 3.00 | 13.4 | 89.2 % | 44.6 % | 59.1 % |
| `rate-limit-basic-auth-tls` | **196,028** | 196,628 | 186,398 | 203,433 | 6,121 | 3.1 % | 0.34 | 1.61 | 3.20 | 1417.4 | 89.6 % | 44.0 % | 58.6 % |
| `wsdl2openapi` | **120,417** | 120,595 | 114,933 | 124,144 | 3,548 | 2.9 % | 0.58 | 2.01 | 7.04 | 32.8 | 94.5 % | 23.4 % | 30.5 % |
| `soap-validation` | **97,884** | 97,844 | 96,344 | 99,544 | 1,349 | 1.4 % | 0.77 | 1.86 | 8.48 | 46.1 | 95.9 % | 15.5 % | 24.8 % |

Max ms is the highest single latency over all 5 runs; CPU columns are the mean over the 5 runs.
Per-run figures are listed under [Per-run results](#per-run-results).

### Comparisons

`rate-limit-basic-auth` and `rate-limit-basic-auth-tls` use the same body as `fullproxy`, so their
RPS can be compared directly:

| Added on top of | Added | Mean RPS | Change |
|---|---|---|---:|
| `fullproxy` | Basic Auth + rate limiting | 216,912 → 214,125 | −1.3 % |
| `rate-limit-basic-auth` | TLS on both hops | 214,125 → 196,028 | −8.5 % |
| `fullproxy` | Basic Auth + rate limiting + TLS | 216,912 → 196,028 | −9.6 % |

The −1.3 % for Basic Auth + rate limiting is within the run-to-run spread of the two scenarios (CV
1.7 and 2.7 %; their ranges overlap).

Gateway CPU time per request (gateway busy × 16 cores ÷ RPS; includes kernel network processing):

| Scenario | Gateway CPU per request | Of which user space |
|---|---:|---:|
| `shortcircuit` | 28 µs | 9 µs |
| `fullproxy` | 66 µs | 25 µs |
| `rate-limit-basic-auth` | 67 µs | 26 µs |
| `rate-limit-basic-auth-tls` | 73 µs | 33 µs |
| `openapi-validation` | 87 µs | 45 µs |
| `wsdl2openapi` | 126 µs | 82 µs |
| `soap-validation` | 157 µs | 114 µs |

User space is the gateway's `usr` CPU share, converted the same way: the time spent in Membrane and
the JVM rather than in the kernel's network stack.

`openapi-validation` sends a 30-byte body instead of 1,066 bytes, and the two SOAP scenarios send
different bodies to a different backend service, so none of them is directly comparable to
`fullproxy`.

### Observations

- **The gateway does most of the work.** It runs at 89 to 96 % CPU in every scenario, while the
  backend stays at 15 to 45 %. The client stays at 25 to 61 %, except in `shortcircuit`, where it
  runs at 91 %: that scenario is close to the limit of both VMs.
- **The proxying scenarios stop short of full gateway CPU.** `fullproxy`, `openapi-validation` and
  both rate-limit scenarios level off at 89 to 91 % gateway CPU; the two SOAP scenarios reach 94 to
  96 %.
- **Tail latency.** p99 is 3.0 to 4.7 ms in the proxying scenarios, 6.4 ms in `shortcircuit`, and
  7.0 and 8.5 ms in the two SOAP scenarios. One request took 1,417 ms (`rate-limit-basic-auth-tls`,
  round 2; that run's p99 is 3.5 ms); every other request finished within 69 ms.
- **Run-to-run spread.** The CV is 1.2 to 3.1 %. The highest are `rate-limit-basic-auth-tls`
  (3.1 %, round 2 at 186,398 RPS), `wsdl2openapi` (2.9 %, round 3 at 114,933 RPS) and
  `rate-limit-basic-auth` (2.7 %).
- **TLS.** Connections are kept alive, so the TLS scenario mostly measures encrypting and
  decrypting on established connections, not a handshake per request.

### Compared with the previous results

The previous results were measured on 2026-09-26 with the same sizes, OS, kernel, JVM, scripts and
load, but on other VMs (the resource group was deleted and provisioned again) and with older code
(master `d3f9bbb31` plus the changes since merged into master):

| Scenario | 2026-09-26 | 2026-09-29 | Change |
|---|---:|---:|---:|
| `shortcircuit` | 558,640 | 539,940 | −3.3 % |
| `fullproxy` | 208,758 | 216,912 | +3.9 % |
| `openapi-validation` | 172,366 | 167,211 | −3.0 % |
| `rate-limit-basic-auth` | 204,728 | 214,125 | +4.6 % |
| `rate-limit-basic-auth-tls` | 188,589 | 196,028 | +3.9 % |
| `wsdl2openapi` | 118,182 | 120,417 | +1.9 % |
| `soap-validation` | 95,513 | 97,884 | +2.5 % |

Both the VMs and the code changed, so the differences can't be attributed to either. The proxying
scenarios ran at 2 to 4 percentage points more gateway CPU than before. Their CPU time per request
stayed the same, except for `openapi-validation`, which rose from 82 to 87 µs.

## Garbage collection

Measured on 2026-10-16 on newly provisioned VMs (same sizes, OS and region as above; JVM Temurin
21.0.12.1; code: master `21e7c28e7`), with the gateway's GC log fetched after every run and
summarized by `gc-summary.awk` for the client's measured window. All 144 runs completed with
**0 errors**.

Pause figures are the JVM's own stop-the-world pause times. The safepoint figure is the longest
time any safepoint held the application threads, including the time it took to reach the safepoint;
it is the stall a request can actually see.

### Tuned configuration

One run per scenario with the configuration used for all results above
(`-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC`), 10,000,000 measured requests each:

| Scenario | RPS | vs. 2026-09-29 | GC pauses | Longest pause | Longest safepoint | Time in GC pauses | Full GCs under load | Allocated per request |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `shortcircuit` | 586,951 | +8.7 % | 19 | 1.7 ms | 6.4 ms | 0.09 % | 0 | 21 KB |
| `fullproxy` | 220,946 | +1.9 % | 45 | 1.2 ms | 1.8 ms | 0.05 % | 0 | 50 KB |
| `openapi-validation` | 173,047 | +3.5 % | 97 | 0.9 ms | 1.9 ms | 0.09 % | 0 | 108 KB |
| `rate-limit-basic-auth` | 213,503 | −0.3 % | 46 | 1.7 ms | 2.2 ms | 0.05 % | 0 | 51 KB |
| `rate-limit-basic-auth-tls` | 195,777 | −0.1 % | 55 | 1.6 ms | 2.8 ms | 0.06 % | 0 | 61 KB |
| `wsdl2openapi` | 122,508 | +1.7 % | 221 | 2.8 ms | 3.3 ms | 0.15 % | 0 | 247 KB |
| `soap-validation` | 96,704 | −1.2 % | 416 | 6.1 ms | 7.3 ms | 0.35 % | 0 | 464 KB |

Allocated per request is the heap filled between collections (heap before each GC minus heap after
the previous one) divided by the requests. The pauses during startup and warmup (up to 22 ms, while
classes load and the JIT compiles) are excluded. Over all 144 runs the gateway had 69 full GCs, all
within its first 8.3 seconds and all triggered by class metadata (`Metadata GC Threshold`), taking
up to 22.4 ms; none happened later.

### GC and heap settings

Three rounds of each scenario under five JVM configurations, the order of the configurations
rotated per round. `default` passes no GC or heap flags: the JVM picks G1 and a maximum heap of a
quarter of the VM's 64 GiB.

| Label | JVM flags |
|---|---|
| `tuned` | `-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC` |
| `default` | none |
| `g1-2g` | `-Xms2g -Xmx2g` |
| `g1-512m` | `-Xms512m -Xmx512m` |
| `zgc-2g` | `-Xms2g -Xmx2g -XX:+UseZGC -XX:+ZGenerational` |

Mean of the 3 runs; longest pause, longest safepoint and max latency are the worst of the 3 runs.

| Scenario | Config | Mean RPS | vs. `tuned` | CV | p99 ms | Max ms | Longest pause | Longest safepoint | Time in GC pauses | Gateway user CPU per request |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `fullproxy` | `tuned` | 218,294 | | 1.4 % | 2.74 | 11.7 | 2.2 ms | 2.4 ms | 0.05 % | 25.6 µs |
| `fullproxy` | `default` | 202,289 | −7.3 % | 6.0 % | 3.32 | 13.5 | 2.5 ms | 4.6 ms | 0.97 % | 28.6 µs |
| `fullproxy` | `g1-2g` | 201,334 | −7.8 % | 4.4 % | 3.37 | 13.8 | 4.0 ms | 5.7 ms | 0.82 % | 29.2 µs |
| `fullproxy` | `g1-512m` | 191,968 | −12.1 % | 2.7 % | 3.66 | 13.2 | 4.2 ms | 4.4 ms | 2.43 % | 30.1 µs |
| `fullproxy` | `zgc-2g` | 213,048 | −2.4 % | 0.7 % | 2.71 | 17.2 | 0.04 ms | 2.4 ms | 0.02 % | 27.6 µs |
| `openapi-validation` | `tuned` | 173,586 | | 2.9 % | 4.18 | 22.9 | 2.1 ms | 2.9 ms | 0.09 % | 45.1 µs |
| `openapi-validation` | `default` | 157,283 | −9.4 % | 1.8 % | 5.20 | 16.2 | 5.2 ms | 5.6 ms | 1.15 % | 49.5 µs |
| `openapi-validation` | `g1-2g` | 159,960 | −7.8 % | 5.8 % | 4.90 | 19.2 | 5.6 ms | 6.5 ms | 1.45 % | 49.7 µs |
| `openapi-validation` | `g1-512m` | 153,605 | −11.5 % | 1.6 % | 4.91 | 51.6 | 40.9 ms | 41.1 ms | 4.40 % | 50.4 µs |
| `openapi-validation` | `zgc-2g` | 159,999 | −7.8 % | 1.2 % | 4.59 | 21.0 | 0.07 ms | 6.8 ms | 0.05 % | 50.9 µs |

- **No full GCs under load** in any of the 45 runs (only the startup ones described above).
- **Untuned costs throughput, not latency.** With the JVM's defaults or a 2 GB G1 heap the gateway
  delivers 7 to 9 % fewer requests per second than `tuned`, and each request costs 3 to 5 µs more
  user CPU. The longest GC pause stays at 6 ms or less, and no request took longer than 20 ms.
- **512 MB is the only setting with long pauses.** The 40.9 ms pause (and the long pauses in the
  allocation-heavy scenario below) is not GC work: the pause's phases add up to about 1 ms, and the
  GC threads used 0.01 s of CPU in 0.04 s of wall-clock time. The VM had no steal time and about
  15 % idle CPU at the time, so it was not starved of CPU either; the cause was not found. All
  pauses over 20 ms in the whole matrix were in `g1-512m`.
- **ZGC** keeps its pauses below 0.1 ms but does not reduce the longest safepoint (the time to bring
  all threads to a stop still counts), and costs 2 to 6 µs more user CPU per request than `tuned`.

**Allocation-heavy workloads.** `soap-validation` allocates about 464 KB per request, nine times as
much as `fullproxy` (about 45 GB/s at full load), and the GC settings matter more there: `default`
−13 %, `g1-2g` −17 %, `g1-512m` −30 % (pauses up to 103 ms, of the same kind) and `zgc-2g`
−27 % against `tuned`. With `zgc-2g`, request threads stalled 75,000 to 92,000 times per run, for
up to 193 ms each, because a 2 GB heap gives ZGC too little room to keep up with that allocation rate.
ZGC logs these allocation stalls separately; they are neither pauses nor safepoints. Heavy XML
processing needs a correspondingly larger heap.

### Soak

`fullproxy` for 60 minutes against one gateway JVM: one run that starts the gateway, then
back-to-back 10,000,000-request runs without restarting it (`GATEWAY_RESTART=false`).

| Config | Runs | Requests | Errors | Full GCs | Longest pause | Longest safepoint | RPS first → last third | p99 first → last third |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `g1-2g` | 44 | 484,000,000 | 0 | 0 | 23.1 ms | 23.4 ms | 202,004 → 197,498 | 3.35 → 3.55 ms |
| `tuned` | 48 | 528,000,000 | 0 | 0 | 3.8 ms | 4.1 ms | 213,320 → 213,950 | 2.99 → 2.97 ms |

Request counts include each run's 1,000,000 warmup requests. The other figures cover the measured
windows; over the whole hour, from the first run's measured start to the last run's measured end
(including the warmups in between), the longest pause and safepoint are the same and there was no
full GC.

- **No memory leak.** The lowest heap after GC grew over the hour (`tuned`: 30 → 89 MB), because
  objects promoted to the old generation pile up until an old-generation collection runs. A full GC
  forced at the end of the `tuned` soak shrank the old generation from 102 MB to 30 MB, the level of
  the soak's first runs.
- **`g1-2g` lost 3 % RPS over the hour**, with user CPU per request rising from 28.9 to 29.7 µs while
  the time in GC pauses stayed flat (0.82 → 0.79 %). The drift is smaller than the 4.4 % run-to-run
  CV of `g1-2g` in the matrix; `tuned` showed none.
- **The 23.1 ms `g1-2g` pause** is the same pattern as in `g1-512m`: its phases add up to 1 ms and
  the GC threads used 0.01 s of CPU in 0.02 s. Every other pause in that soak was at most 6.4 ms.
- **One request took 203 ms** (`tuned`, run 11). In that run the gateway's longest GC pause was
  0.9 ms and its longest safepoint 2.9 ms, so the gateway's JVM did not stall: the delay came from
  outside it (network, client or backend). The 1,417 ms request of 2026-09-29 was measured before
  GC logs were kept, so it can't be checked the same way.

### Typical load

Measured on 2026-10-21 on newly provisioned VMs (same sizes as above), `fullproxy` at a fixed
1,000 requests per second instead of full load (`LOAD_RATE=1000`): requests are sent on schedule
and latency is timed from each request's scheduled time, so a gateway stall shows up as latency.
The client also logs every request slower than 1 ms and writes its own GC log; `match-slow.awk`
matches the slow requests against the stop-the-world safepoints of the gateway and of the client.
JDK 21 (Temurin 21.0.12.1), 250 MB heap, 5 minutes (300,000 requests) per collector, 0 errors:

| | G1 | ZGC | Shenandoah |
|---|---:|---:|---:|
| p50 / p99 | 0.27 / 0.30 ms | 0.27 / 0.30 ms | 0.27 / 0.30 ms |
| p99.9 | 0.97 ms | 0.62 ms | 0.63 ms |
| p99.99 | 2.0 ms | 2.2 ms | 1.9 ms |
| Max | 17.3 ms | 13.6 ms | 19.2 ms |
| Requests over 1 ms | 279 | 68 | 110 |
| … during a gateway pause | 199 | 0 | 0 |
| … during a client pause | 19 | 7 | 18 |
| Gateway pauses (longest) | 215 (2.8 ms) | 1,123 (0.02 ms) | 187 (0.27 ms) |
| GC thread CPU: workers + control | 0.52 + 0.01 s | 4.88 + 0.83 s | 0.78 + 1.73 s |
| Gateway JVM CPU | 11.5 s | 17.2 s | 14.6 s |

- **Up to p99 the collector makes no difference.** G1's pauses, one every 1.4 s for 1 to 3 ms,
  delay about 200 of 300,000 requests (0.07 %) by about a millisecond: p99.9 is 0.97 ms with G1 and
  0.62 ms with ZGC or Shenandoah, whose slow requests never coincide with a gateway pause.
- **From p99.99 on the collectors are equal** (1.9 to 2.2 ms). The outliers of 5 to 19 ms occur with
  every collector and are not caused by the gateway's GC. The match of G1's 17 ms request with a
  1.25 ms pause is an overlap, not a cause.
- **GC costs little CPU at this load.** The whole gateway JVM uses about 0.04 to 0.06 cores; G1's GC
  threads 0.2 % of one core, ZGC's about 2 %. Most of Shenandoah's GC CPU comes from its control and
  regulator threads, which poll whether to collect regardless of load.

A wider matrix of 1-minute runs (JDK 21 and 25; G1, ZGC and Shenandoah; heaps of 100 MB, 250 MB,
4 GB and the default) is in [gc-typical-load/RESULTS.md](gc-typical-load/RESULTS.md): no run had an
error or a full GC, G1 never paused longer than 5.7 ms (under 0.1 % of the time), ZGC and Shenandoah
never longer than 0.4 ms, and GC used at most about 2 % of one core.

## Per-run results

### `shortcircuit`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 549,776 | 10,000,000 | 0 | 18.2 s | 0.423 | 1.271 | 5.127 | 53.0 | 95.1 % (29.9/43.1/22.1) | 0.0 % | 91.4 % |
| 2 | 536,636 | 10,000,000 | 0 | 18.6 s | 0.405 | 1.209 | 6.965 | 61.5 | 94.3 % (30.6/42.1/21.6) | 0.1 % | 91.7 % |
| 3 | 530,480 | 10,000,000 | 0 | 18.9 s | 0.416 | 1.260 | 6.463 | 65.5 | 95.4 % (30.3/42.2/22.9) | 0.1 % | 88.5 % |
| 4 | 542,203 | 10,000,000 | 0 | 18.4 s | 0.403 | 1.203 | 6.838 | 68.4 | 94.6 % (31.4/43.2/20.0) | 0.0 % | 91.5 % |
| 5 | 540,602 | 10,000,000 | 0 | 18.5 s | 0.408 | 1.243 | 6.516 | 56.8 | 95.0 % (30.7/42.4/21.9) | 0.1 % | 91.1 % |

### `fullproxy`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 212,322 | 10,000,000 | 0 | 47.1 s | 0.305 | 1.713 | 3.287 | 11.6 | 88.9 % (33.6/34.5/20.8) | 41.9 % | 58.9 % |
| 2 | 215,870 | 10,000,000 | 0 | 46.3 s | 0.304 | 1.630 | 3.129 | 11.1 | 88.9 % (33.1/34.5/21.2) | 43.7 % | 60.4 % |
| 3 | 216,981 | 10,000,000 | 0 | 46.1 s | 0.307 | 1.586 | 3.093 | 10.3 | 89.2 % (33.3/34.4/21.5) | 45.0 % | 60.3 % |
| 4 | 222,629 | 10,000,000 | 0 | 44.9 s | 0.314 | 1.395 | 2.813 | 11.2 | 90.1 % (35.2/37.5/17.4) | 45.9 % | 62.4 % |
| 5 | 216,757 | 10,000,000 | 0 | 46.1 s | 0.312 | 1.525 | 3.033 | 11.1 | 89.8 % (33.7/34.6/21.5) | 45.7 % | 60.4 % |

### `openapi-validation`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 168,205 | 10,000,000 | 0 | 59.5 s | 0.372 | 2.091 | 4.578 | 22.7 | 90.7 % (47.0/27.4/16.4) | 29.3 % | 41.2 % |
| 2 | 166,477 | 10,000,000 | 0 | 60.1 s | 0.364 | 2.227 | 4.826 | 13.4 | 90.2 % (46.9/27.2/16.1) | 27.1 % | 42.5 % |
| 3 | 164,438 | 10,000,000 | 0 | 60.8 s | 0.364 | 2.284 | 4.864 | 15.5 | 89.9 % (45.9/27.2/16.7) | 27.5 % | 40.2 % |
| 4 | 169,868 | 10,000,000 | 0 | 58.9 s | 0.375 | 2.041 | 4.438 | 17.0 | 91.4 % (49.6/28.7/13.1) | 29.7 % | 41.3 % |
| 5 | 167,065 | 10,000,000 | 0 | 59.9 s | 0.365 | 2.200 | 4.638 | 13.0 | 90.4 % (47.0/27.2/16.1) | 29.3 % | 39.9 % |

### `rate-limit-basic-auth`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 212,882 | 10,000,000 | 0 | 47.0 s | 0.319 | 1.515 | 3.080 | 11.7 | 89.9 % (35.1/33.8/21.0) | 44.3 % | 59.9 % |
| 2 | 220,023 | 10,000,000 | 0 | 45.5 s | 0.325 | 1.329 | 2.769 | 9.7 | 89.8 % (34.0/34.3/21.6) | 46.1 % | 57.1 % |
| 3 | 220,142 | 10,000,000 | 0 | 45.4 s | 0.324 | 1.331 | 2.773 | 9.9 | 90.1 % (35.5/35.8/18.7) | 46.1 % | 61.5 % |
| 4 | 210,185 | 10,000,000 | 0 | 47.6 s | 0.320 | 1.543 | 3.136 | 12.2 | 88.8 % (33.2/34.0/21.6) | 42.8 % | 58.1 % |
| 5 | 207,392 | 10,000,000 | 0 | 48.2 s | 0.311 | 1.712 | 3.230 | 13.4 | 87.3 % (33.5/33.3/20.5) | 43.7 % | 58.8 % |

### `rate-limit-basic-auth-tls`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 197,404 | 10,000,000 | 0 | 50.7 s | 0.337 | 1.673 | 3.349 | 11.5 | 90.3 % (40.3/30.2/19.8) | 45.3 % | 57.6 % |
| 2 | 186,398 | 10,000,000 | 0 | 53.6 s | 0.338 | 1.807 | 3.496 | 1417.4 | 87.0 % (39.5/28.6/18.5) | 41.3 % | 56.7 % |
| 3 | 196,276 | 10,000,000 | 0 | 50.9 s | 0.345 | 1.617 | 3.265 | 11.8 | 89.6 % (42.0/31.0/16.6) | 44.2 % | 60.2 % |
| 4 | 196,628 | 10,000,000 | 0 | 50.9 s | 0.347 | 1.606 | 3.126 | 10.8 | 90.2 % (40.0/29.7/20.5) | 42.4 % | 56.7 % |
| 5 | 203,433 | 10,000,000 | 0 | 49.2 s | 0.357 | 1.372 | 2.778 | 33.8 | 91.0 % (40.0/30.4/20.5) | 46.7 % | 61.9 % |

### `wsdl2openapi`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 124,144 | 10,000,000 | 0 | 80.6 s | 0.587 | 1.701 | 6.544 | 23.6 | 94.9 % (61.7/19.8/13.4) | 22.9 % | 31.0 % |
| 2 | 120,595 | 10,000,000 | 0 | 82.9 s | 0.596 | 1.856 | 6.851 | 26.7 | 94.9 % (61.8/19.5/13.5) | 23.9 % | 30.7 % |
| 3 | 114,933 | 10,000,000 | 0 | 87.0 s | 0.566 | 2.620 | 7.969 | 32.8 | 93.3 % (60.9/19.1/13.3) | 22.6 % | 29.4 % |
| 4 | 122,804 | 10,000,000 | 0 | 81.4 s | 0.576 | 1.963 | 6.811 | 28.8 | 94.6 % (61.8/19.5/13.3) | 23.9 % | 31.0 % |
| 5 | 119,607 | 10,000,000 | 0 | 83.6 s | 0.593 | 1.911 | 7.045 | 26.4 | 94.8 % (62.0/19.5/13.3) | 23.6 % | 30.3 % |

### `soap-validation`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 97,844 | 10,000,000 | 0 | 102.2 s | 0.771 | 1.925 | 8.435 | 37.3 | 95.9 % (69.5/15.9/10.6) | 15.6 % | 24.8 % |
| 2 | 96,808 | 10,000,000 | 0 | 103.3 s | 0.792 | 1.834 | 8.363 | 40.8 | 96.2 % (69.6/15.9/10.7) | 15.2 % | 25.5 % |
| 3 | 96,344 | 10,000,000 | 0 | 103.8 s | 0.763 | 1.947 | 9.149 | 35.1 | 95.5 % (69.7/15.5/10.4) | 15.0 % | 24.4 % |
| 4 | 99,544 | 10,000,000 | 0 | 100.5 s | 0.770 | 1.779 | 8.176 | 46.1 | 96.0 % (69.4/15.9/10.8) | 15.9 % | 25.5 % |
| 5 | 98,878 | 10,000,000 | 0 | 101.1 s | 0.772 | 1.804 | 8.295 | 35.5 | 96.0 % (69.7/15.7/10.7) | 15.7 % | 23.6 % |

## Reproducing

```sh
cd distribution/performance-test
./request-quotas.sh
./provision.sh
./deploy-and-run.sh
for round in 1 2 3 4 5; do
  for scenario in shortcircuit fullproxy openapi-validation rate-limit-basic-auth rate-limit-basic-auth-tls wsdl2openapi soap-validation; do
    LOAD_TOTAL=10000000 ./run-scenario.sh "$scenario"
  done
done
./teardown.sh
```

`run-scenario.sh` uses each scenario's default concurrency (350 for `shortcircuit`, 100 for the
others) and a 1,000,000-request warmup. Cloud VMs land on different physical hosts, so expect the
same order of magnitude when repeating the test, not identical figures.
