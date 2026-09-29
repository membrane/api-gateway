# Performance Test Results

Seven scenarios, each run 5 times with 10,000,000 measured requests per run, on three Azure VMs
(client, gateway, backend) connected over a real network. Measured on 2026-09-26 between 14:01 and
14:54 CEST. All 35 runs completed with **0 errors**.

| Scenario | What the gateway does | Concurrency | Mean RPS | CV | p50 ms | p95 ms | p99 ms |
|---|---|---:|---:|---:|---:|---:|---:|
| `shortcircuit` | Answers directly, no backend | 350 | **558,640** | 1.4 % | 0.34 | 1.80 | 5.55 |
| `fullproxy` | Forwards to the backend | 100 | **208,758** | 1.0 % | 0.31 | 1.63 | 3.33 |
| `openapi-validation` | Validates against an OpenAPI spec, then forwards | 100 | **172,366** | 1.4 % | 0.37 | 1.97 | 4.54 |
| `rate-limit-basic-auth` | Basic Auth + rate limiting, then forwards | 100 | **204,728** | 1.8 % | 0.32 | 1.61 | 3.31 |
| `rate-limit-basic-auth-tls` | Same, with TLS on both hops | 100 | **188,589** | 1.5 % | 0.36 | 1.67 | 3.44 |
| `wsdl2openapi` | JSON protection, 5 JSONPath headers, JSON to SOAP and back | 100 | **118,182** | 1.6 % | 0.55 | 2.48 | 7.70 |
| `soap-validation` | XML protection, WSDL validation of request and response | 100 | **95,513** | 4.2 % | 0.76 | 2.13 | 9.31 |

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
| Gateway | Membrane API Gateway 7.6.3-SNAPSHOT distribution, started with `membrane.sh`. Built from master `d3f9bbb31` plus uncommitted working-tree changes: Basic Authentication and `Header`; `Json2SoapTransformer`/`Wsdl2OpenapiInterceptor` resolving the WSDL once and reusing XML factories (#3357); `JsonpathExchangeExpression` compiling its JSONPath once; the XML Schema, WSDL and Schematron validators using a non-blocking validator pool with schemas compiled once |
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
  inside the measured phase count (16 to 111 intervals per run). Busy = 100 % minus idle. Steal time
  was 0 on all VMs over the whole test.

## Results

| Scenario | Mean RPS | Median | Min | Max | Std dev | CV | p50 ms | p95 ms | p99 ms | Max ms | CPU gw | CPU be | CPU cl |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `shortcircuit` | **558,640** | 553,905 | 552,826 | 571,005 | 7,889 | 1.4 % | 0.34 | 1.80 | 5.55 | 48.5 | 93.0 % | 0.0 % | 90.5 % |
| `fullproxy` | **208,758** | 208,646 | 206,404 | 211,463 | 2,039 | 1.0 % | 0.31 | 1.63 | 3.33 | 14.4 | 85.6 % | 41.5 % | 56.9 % |
| `openapi-validation` | **172,366** | 173,076 | 168,919 | 174,893 | 2,353 | 1.4 % | 0.37 | 1.97 | 4.54 | 17.8 | 88.6 % | 27.1 % | 41.9 % |
| `rate-limit-basic-auth` | **204,728** | 204,975 | 200,799 | 209,285 | 3,611 | 1.8 % | 0.32 | 1.61 | 3.31 | 19.2 | 85.9 % | 40.2 % | 57.1 % |
| `rate-limit-basic-auth-tls` | **188,589** | 188,602 | 186,159 | 193,002 | 2,784 | 1.5 % | 0.36 | 1.67 | 3.44 | 21.5 | 87.3 % | 38.9 % | 56.9 % |
| `wsdl2openapi` | **118,182** | 117,753 | 116,361 | 120,807 | 1,837 | 1.6 % | 0.55 | 2.48 | 7.70 | 37.4 | 92.2 % | 20.9 % | 29.7 % |
| `soap-validation` | **95,513** | 97,578 | 88,498 | 97,936 | 4,038 | 4.2 % | 0.76 | 2.13 | 9.31 | 49.1 | 94.5 % | 14.3 % | 25.2 % |

Max ms is the highest single latency over all 5 runs; CPU columns are the mean over the 5 runs.
Per-run figures are listed under [Per-run results](#per-run-results).

### Comparisons

`rate-limit-basic-auth` and `rate-limit-basic-auth-tls` use the same body as `fullproxy`, so their
RPS can be compared directly:

| Added on top of | Added | Mean RPS | Change |
|---|---|---|---:|
| `fullproxy` | Basic Auth + rate limiting | 208,758 → 204,728 | −1.9 % |
| `rate-limit-basic-auth` | TLS on both hops | 204,728 → 188,589 | −7.9 % |
| `fullproxy` | Basic Auth + rate limiting + TLS | 208,758 → 188,589 | −9.7 % |

The −1.9 % for Basic Auth + rate limiting is within the run-to-run spread of the two scenarios (CV
1.0 and 1.8 %; their ranges overlap).

Gateway CPU time per request (gateway busy × 16 cores ÷ RPS; includes kernel network processing):

| Scenario | Gateway CPU per request |
|---|---:|
| `shortcircuit` | 27 µs |
| `fullproxy` | 66 µs |
| `rate-limit-basic-auth` | 67 µs |
| `rate-limit-basic-auth-tls` | 74 µs |
| `openapi-validation` | 82 µs |
| `wsdl2openapi` | 125 µs |
| `soap-validation` | 158 µs |

`openapi-validation` sends a 30-byte body instead of 1,066 bytes, and the two SOAP scenarios send
different bodies to a different backend service, so none of them is directly comparable to
`fullproxy`.

### Observations

- **The gateway does most of the work.** It runs at 86 to 95 % CPU in every scenario, while the
  backend stays at 14 to 42 %. The client stays at 25 to 57 %, except in `shortcircuit`, where it
  runs at 91 %: that scenario is close to the limit of both VMs.
- **The proxying scenarios stop short of full gateway CPU.** `fullproxy`, `openapi-validation` and
  both rate-limit scenarios level off at 86 to 89 % gateway CPU; the two SOAP scenarios reach 92 to
  95 %.
- **Tail latency.** p99 is 3.3 to 4.5 ms in the proxying scenarios, 5.6 ms in `shortcircuit`, and
  7.7 and 9.3 ms in the two SOAP scenarios. No single request took longer than 50 ms.
- **Run-to-run spread.** The CV is 1.0 to 1.8 % in six scenarios. `soap-validation` has 4.2 %
  because of one slower run (88,498 RPS in round 3); the other four runs are within 2.4 % of each
  other.
- **TLS.** Connections are kept alive, so the TLS scenario mostly measures encrypting and
  decrypting on established connections, not a handshake per request.

## Per-run results

### `shortcircuit`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 562,099 | 10,000,000 | 0 | 17.8 s | 0.345 | 1.709 | 5.534 | 37.2 | 93.4 % (34.9/39.5/19.0) | 0.0 % | 90.2 % |
| 2 | 552,826 | 10,000,000 | 0 | 18.1 s | 0.359 | 1.754 | 5.486 | 34.8 | 93.2 % (34.4/36.5/22.3) | 0.0 % | 90.7 % |
| 3 | 553,364 | 10,000,000 | 0 | 18.1 s | 0.341 | 1.807 | 5.839 | 43.9 | 92.6 % (33.1/36.9/22.6) | 0.1 % | 90.7 % |
| 4 | 571,005 | 10,000,000 | 0 | 17.5 s | 0.363 | 1.565 | 4.929 | 48.5 | 93.7 % (32.9/37.2/23.6) | 0.0 % | 91.3 % |
| 5 | 553,905 | 10,000,000 | 0 | 18.1 s | 0.314 | 2.155 | 5.947 | 38.9 | 92.1 % (34.4/38.8/18.9) | 0.0 % | 89.5 % |

### `fullproxy`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 208,646 | 10,000,000 | 0 | 47.9 s | 0.311 | 1.631 | 3.334 | 12.1 | 85.3 % (33.7/31.2/20.4) | 40.5 % | 58.1 % |
| 2 | 207,271 | 10,000,000 | 0 | 48.2 s | 0.311 | 1.677 | 3.373 | 14.0 | 85.3 % (34.2/31.1/19.9) | 41.7 % | 57.3 % |
| 3 | 206,404 | 10,000,000 | 0 | 48.4 s | 0.304 | 1.753 | 3.510 | 13.1 | 84.7 % (33.8/30.9/20.0) | 41.0 % | 55.9 % |
| 4 | 211,463 | 10,000,000 | 0 | 47.3 s | 0.317 | 1.523 | 3.189 | 11.9 | 86.7 % (35.7/31.9/19.2) | 41.6 % | 58.4 % |
| 5 | 210,005 | 10,000,000 | 0 | 47.6 s | 0.315 | 1.571 | 3.266 | 14.4 | 85.9 % (34.4/31.6/20.0) | 42.9 % | 54.9 % |

### `openapi-validation`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 171,176 | 10,000,000 | 0 | 58.4 s | 0.361 | 2.057 | 4.656 | 14.9 | 88.3 % (46.8/24.4/17.1) | 27.2 % | 41.7 % |
| 2 | 174,893 | 10,000,000 | 0 | 57.2 s | 0.370 | 1.873 | 4.358 | 15.9 | 88.9 % (47.4/24.8/16.6) | 30.5 % | 42.2 % |
| 3 | 173,764 | 10,000,000 | 0 | 57.5 s | 0.373 | 1.877 | 4.400 | 16.4 | 89.0 % (47.8/24.6/16.5) | 23.0 % | 42.9 % |
| 4 | 173,076 | 10,000,000 | 0 | 57.8 s | 0.363 | 1.988 | 4.589 | 17.8 | 88.8 % (48.1/24.3/16.4) | 27.4 % | 41.5 % |
| 5 | 168,919 | 10,000,000 | 0 | 59.2 s | 0.367 | 2.062 | 4.692 | 13.7 | 88.2 % (48.2/24.0/16.0) | 27.4 % | 41.3 % |

### `rate-limit-basic-auth`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 200,799 | 10,000,000 | 0 | 49.8 s | 0.320 | 1.693 | 3.450 | 13.3 | 85.1 % (35.5/29.3/20.4) | 38.1 % | 57.0 % |
| 2 | 204,975 | 10,000,000 | 0 | 48.8 s | 0.327 | 1.555 | 3.238 | 13.3 | 86.4 % (36.5/30.3/19.6) | 40.7 % | 58.5 % |
| 3 | 209,285 | 10,000,000 | 0 | 47.8 s | 0.326 | 1.483 | 3.100 | 11.2 | 86.5 % (35.5/31.1/19.9) | 42.0 % | 57.1 % |
| 4 | 207,076 | 10,000,000 | 0 | 48.3 s | 0.326 | 1.500 | 3.177 | 12.1 | 86.3 % (35.9/30.8/19.6) | 40.6 % | 57.4 % |
| 5 | 201,505 | 10,000,000 | 0 | 49.6 s | 0.312 | 1.798 | 3.579 | 19.2 | 85.3 % (36.1/30.0/19.2) | 39.7 % | 55.6 % |

### `rate-limit-basic-auth-tls`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 186,159 | 10,000,000 | 0 | 53.7 s | 0.352 | 1.756 | 3.544 | 13.0 | 86.7 % (41.3/27.1/18.2) | 38.6 % | 57.3 % |
| 2 | 188,940 | 10,000,000 | 0 | 52.9 s | 0.356 | 1.660 | 3.421 | 12.1 | 87.9 % (41.8/27.9/18.2) | 33.5 % | 53.4 % |
| 3 | 186,242 | 10,000,000 | 0 | 53.7 s | 0.351 | 1.754 | 3.622 | 13.9 | 87.0 % (42.0/27.8/17.2) | 40.2 % | 57.1 % |
| 4 | 193,002 | 10,000,000 | 0 | 51.8 s | 0.359 | 1.540 | 3.183 | 21.5 | 87.8 % (40.9/28.2/18.7) | 41.0 % | 58.9 % |
| 5 | 188,602 | 10,000,000 | 0 | 53.0 s | 0.359 | 1.623 | 3.444 | 18.8 | 87.3 % (41.4/27.6/18.3) | 41.4 % | 57.7 % |

### `wsdl2openapi`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 120,807 | 10,000,000 | 0 | 82.8 s | 0.527 | 2.538 | 7.743 | 26.0 | 91.9 % (61.2/18.0/12.7) | 21.4 % | 29.2 % |
| 2 | 119,225 | 10,000,000 | 0 | 83.9 s | 0.573 | 2.192 | 7.283 | 32.0 | 93.0 % (62.3/18.1/12.6) | 21.5 % | 28.5 % |
| 3 | 116,361 | 10,000,000 | 0 | 85.9 s | 0.549 | 2.671 | 7.924 | 29.6 | 91.9 % (61.7/17.7/12.5) | 20.3 % | 29.9 % |
| 4 | 117,753 | 10,000,000 | 0 | 84.9 s | 0.558 | 2.430 | 7.678 | 37.4 | 92.4 % (61.8/17.9/12.7) | 20.4 % | 30.4 % |
| 5 | 116,761 | 10,000,000 | 0 | 85.6 s | 0.554 | 2.578 | 7.848 | 29.8 | 92.0 % (61.7/17.8/12.6) | 21.1 % | 30.4 % |

### `soap-validation`

| Round | RPS | OK | ERR | Measured window | p50 ms | p95 ms | p99 ms | Max ms | CPU gw (usr/sys/soft) | CPU be | CPU cl |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 1 | 97,936 | 10,000,000 | 0 | 102.1 s | 0.724 | 2.234 | 9.199 | 35.6 | 94.0 % (68.9/14.6/10.5) | 14.8 % | 25.6 % |
| 2 | 97,931 | 10,000,000 | 0 | 102.1 s | 0.762 | 1.952 | 8.578 | 37.6 | 95.0 % (69.5/14.6/10.9) | 14.7 % | 26.4 % |
| 3 | 88,498 | 10,000,000 | 0 | 113.0 s | 0.817 | 2.172 | 10.304 | 49.1 | 94.9 % (72.7/12.9/9.3) | 13.2 % | 23.2 % |
| 4 | 97,578 | 10,000,000 | 0 | 102.5 s | 0.736 | 2.115 | 9.160 | 34.5 | 94.2 % (69.2/14.7/10.3) | 14.1 % | 25.8 % |
| 5 | 95,623 | 10,000,000 | 0 | 104.6 s | 0.752 | 2.168 | 9.326 | 39.3 | 94.4 % (69.4/14.7/10.3) | 14.7 % | 24.9 % |

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
