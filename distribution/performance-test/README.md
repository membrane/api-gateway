# Membrane Performance Test (3-VM, real network)

Measures the Membrane API Gateway's own performance in isolation, using 3 separate machines
(client, gateway, backend) connected over a real network, instead of the loopback, single-JVM
setup used by `distribution/src/test/java/com/predic8/membrane/load/LoadTester.java`. Loopback
numbers conflate raw throughput with CPU contention between roles sharing one machine's cores;
running each role on its own machine isolates what the gateway itself adds to a request.

Seven scenarios are included. The first three are of increasing realism (`shortcircuit` -->
`fullproxy` --> `openapi-validation`); `rate-limit-basic-auth` is a sibling of
`openapi-validation`, built on `fullproxy`'s topology/body/backlog plus two added gateway tasks,
so its RPS can be subtracted directly against `fullproxy`'s to isolate those tasks' combined cost;
`rate-limit-basic-auth-tls` is in turn a sibling of `rate-limit-basic-auth`, adding TLS on both
hops with everything else unchanged, so its RPS can be subtracted directly against
`rate-limit-basic-auth`'s to isolate TLS's added cost; `wsdl2openapi` and `soap-validation`
stand apart and measure legacy SOAP integration:

1. **`shortcircuit`** -- the gateway answers directly, no backend at all. Gives the best possible
   number, but isn't a realistic deployment: nobody runs a gateway that talks to nothing.
2. **`fullproxy`** -- the gateway forwards every request to a real backend, unmodified. More
   realistic as a topology, but not a realistic *use* of an API gateway: if all it does is relay
   bytes, a plain reverse proxy would do the same job.
3. **`openapi-validation`** -- the gateway forwards to the backend *and* validates every request
   against an OpenAPI spec first (small fixed body, to satisfy the spec -- not directly
   RPS-comparable to `fullproxy`). The first scenario that gives the gateway an actual job: a
   plain reverse proxy structurally can't do this.
4. **`rate-limit-basic-auth`** -- `fullproxy`, but every request must first pass HTTP Basic
   credential verification (a constant-time byte compare against the configured plaintext
   password, no hashing) and then a per-client rate-limit check (kept far above the request
   volume actually sent, so nothing is ever rejected -- this measures the cost of the
   counting/lock work, not of returning 429s), stacking both checks the way a production API
   would.
5. **`rate-limit-basic-auth-tls`** -- `rate-limit-basic-auth`, but both hops are TLS instead of
   plaintext: the gateway terminates TLS on the client-facing side (self-signed cert) and connects
   to the backend over TLS too (trusting the backend's self-signed cert via a truststore).
   Credentials, rate limit, body, and backlog are all unchanged from `rate-limit-basic-auth`, so
   this isolates the cost of the two added TLS handshakes/hops on top of it.
6. **`wsdl2openapi`** -- legacy integration: the client calls a REST/JSON API
   (`POST /create-person`), the gateway checks the JSON body with `jsonProtection` (default
   limits), copies five of its fields into request headers with JSONPath `setHeader`s
   (`X-Name`, `X-Last-Name`, `X-Email`, `X-Customer-Number`, `X-City`), converts the JSON body
   into a SOAP request for the `createPerson` operation of `conf/person-service.wsdl` (a person
   with 10 scalar fields, one array and a nested 7-field address, ~450-byte JSON body), forwards
   it to the backend's SOAP listener on port 2012, and converts the SOAP response -- an empty
   `createPersonResponse` element -- back into JSON (`{}`). Both directions are converted on
   every request. Body and work differ from `fullproxy`, so its RPS is not directly comparable.
7. **`soap-validation`** -- the client sends the same person as a SOAP 1.1 `createPerson`
   request (`POST /person-service`, `text/xml`, ~760 bytes); the gateway checks it with
   `xmlProtection` (default limits), validates it against `conf/person-service.wsdl`, forwards it
   to the backend's SOAP listener on port 2012, and validates the SOAP response (the empty
   `createPersonResponse`) against the same WSDL on the way back. No format conversion, so it
   shows the cost of XML protection plus WSDL validation in both directions.

Every scenario config sets `<router exchangeStore="...">` to a `forgetfulExchangeStore`. The
default `LimitedMemoryExchangeStore` keeps recent exchanges for the admin console and makes
request threads wait on its lock; nothing in these scenarios reads the stored exchanges.

For `rate-limit-basic-auth` and `rate-limit-basic-auth-tls`, a run is only valid if it reports
`ERR=0` -- a nonzero `ERR` means requests were actually being rejected (401/429), and a
short-circuited rejection is *cheaper* than a proxied request, so a bad run reports an RPS number
that looks better, not worse. For `rate-limit-basic-auth-tls` specifically, also check the
client's CPU-busy figure: TLS adds real per-request work (handshake/encryption) to the client
side too, and if the client saturates, the RPS number measures the load generator, not the
gateway -- lower concurrency or a larger client VM before trusting the number.


## Requirements

- An Azure subscription and the [`az` CLI](https://learn.microsoft.com/cli/azure/install-azure-cli),
  logged in (`az login`) with the right subscription selected (`az account set --subscription <id>`).
- A local Membrane build environment (this repo, Maven) to build the distribution zip.
- `ssh`/`scp` on your machine; VMs are created with `--generate-ssh-keys` (uses/creates
  `~/.ssh/id_rsa` if you don't already have a key).

This test costs real money while the VMs are running (three `Standard_F16as_v7` VMs by
default) --
**always run `teardown.sh` when done.** A full run of all scenarios takes well under an
hour end to end.

## Quickstart

```sh
cd distribution/performance-test

./request-quotas.sh     # checks vCPU quota for the 3 VM sizes, requests increases if needed
./provision.sh          # creates the resource group, VNet, NSG, and 3 VMs
./deploy-and-run.sh      # builds the distribution zip, ships everything, starts backend

./run-scenario.sh shortcircuit
./run-scenario.sh fullproxy
./run-scenario.sh openapi-validation
./run-scenario.sh rate-limit-basic-auth
./run-scenario.sh rate-limit-basic-auth-tls
./run-scenario.sh wsdl2openapi
./run-scenario.sh soap-validation

./teardown.sh            # deletes everything -- do this when you're done
```

Each `run-scenario.sh` invocation restarts the gateway with that scenario's config, samples CPU
on all three VMs during the run, and prints RPS/OK/ERR, latency percentiles
(`LATENCY_MS p50=... p95=... p99=... max=... p99.9=... p99.99=...`), plus a CPU-busy summary per role. Re-run
it as many times as you like (e.g. at different concurrency levels: `./run-scenario.sh fullproxy
150`) without re-running `deploy-and-run.sh` in between. Without that argument each scenario runs
at its own default concurrency (`DEFAULT_CONCURRENCY` in `run-scenario.sh`'s scenario list): 350
for `shortcircuit`, 100 for all others.

After updating these scripts, rerun `deploy-and-run.sh` to install the client and CPU sampler
and record the deployed gateway version. Redeploys stop the old backend and replace the client's
JAR directory; previous JARs are retained under `~/client-libs-backup.*` on the client VM.

Latency is measured per request on the client, from handing the request to the HTTP client until
its response (or failure) arrives, over every request of the measured phase including errors, and
reported as nearest-rank percentiles. The client is closed-loop (it only sends a new request once
one completes), so the percentiles are not corrected for coordinated omission: a gateway stall
delays the requests that would have been sent during it rather than recording them as slow. At
fixed concurrency, mean latency is roughly `concurrency / RPS`, so latency is only comparable
between runs at the same concurrency.

CPU sampling reads Linux `/proc/stat` once per second and reports only complete intervals inside
the client's measured phase, including idle intervals. Startup and warmup are excluded. This
requires synchronized clocks on all three VMs (check `timedatectl status` before benchmarking).
Runs too short to contain a full sampling interval report no CPU result. The historical CPU
figures in the results documents used the older activity-filtered method and are not directly
comparable to new runs.

## GC logs and per-run results

The gateway always writes a GC and safepoint log (`-Xlog:gc*,safepoint`, epoch-millisecond
timestamps, no rotation). After each run, `run-scenario.sh` fetches it and saves everything about
the run under `results/<YYYYMMDD-HHMMSS>-<scenario>-<RUN_LABEL>/` (gitignored):

| File | Content |
|---|---|
| `run.txt` | Scenario, label, concurrency, request counts and rate, `GC_OPTS`, gateway JDK |
| `client.out` | Client output: RPS, OK/ERR, latency percentiles, measured window |
| `cpu-summary.txt` | CPU summary per VM |
| `gc.log` | The gateway's GC and safepoint log |
| `gc-summary.txt` | `gc-summary.awk` output for the measured window |
| `gateway.log` | The gateway's console output |
| `jvm-flags.txt` | The effective JVM flags (`-XX:+PrintCommandLineFlags`) |
| `proc-cpu.log` | CPU time of the gateway JVM and of its GC threads, once per second (`proc-cpu-sample.sh`) |
| `proc-cpu-summary.txt` | Gateway JVM and GC thread CPU in the measured window |
| `summary.txt` | The run's `SUMMARY` line: errors, latency, GC collections and pauses, JVM and GC CPU |
| `slow-requests.txt` | Fixed-rate runs: every request slower than 1 ms, with its scheduled time (epoch ms) and latency (ms) |
| `client-gc.log` | The client's GC and safepoint log |

To find out which slow requests coincided with a stop-the-world pause of the gateway or of the client:

```sh
LC_ALL=C awk -v tol=1 -f match-slow.awk results/<run>/gc.log results/<run>/client-gc.log results/<run>/slow-requests.txt
```

`gc-summary.awk` reads Parallel, G1, generational ZGC and Shenandoah logs of JDK 21 and 25 and
prints the pauses inside the client's measured window (count, full GCs, degenerated Shenandoah
cycles, total, max, p99, share of the window, GC CPU, heap after GC), the number of collections and
their concurrent time, the longest safepoint including the time to reach it, ZGC allocation stalls
if there were any,
and the pauses before the window (startup, warmup, or earlier runs). To re-run it on a saved log:

```sh
LC_ALL=C awk -v start=<epoch ms> -v end=<epoch ms> -f gc-summary.awk results/<run>/gc.log
```

with `start` and `end` from the `MEASURED_WINDOW` line in `client.out`.

`run-scenario.sh` also reads:

- `GC_OPTS`: the gateway's collector and heap flags, default
  `-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC`. `GC_OPTS=''` runs the JVM's defaults.
  GC logging is added independently of it.
- `RUN_LABEL`: names the results directory, default `tuned`.
- `GATEWAY_RESTART=false`: keeps the running gateway JVM instead of restarting it, for soak tests.
  The gateway must have been started by `run-scenario.sh` for the same scenario; the script
  refuses otherwise. For example, one hour of `fullproxy` against a 2 GB G1 heap:

  ```sh
  GC_OPTS='-Xms2g -Xmx2g' LOAD_TOTAL=10000000 RUN_LABEL=soak-g1-2g ./run-scenario.sh fullproxy
  end=$(( $(date +%s) + 3600 ))
  while (( $(date +%s) < end )); do
    LOAD_TOTAL=10000000 GATEWAY_RESTART=false RUN_LABEL=soak-g1-2g ./run-scenario.sh fullproxy
  done
  ```
- `LOAD_RATE`: sends a fixed number of requests per second instead of as many as the concurrency
  allows, to look at the gateway under typical rather than maximum load. Requests are sent on
  schedule whether or not earlier ones have completed, and latency is timed from each request's
  scheduled time, so a gateway stall shows up as latency. The concurrency then only caps the
  client's connections (default 1000). `client.out` adds a `SEND_RATE` line with the largest lag
  behind the schedule; a large lag means the client limited the rate.
- `GATEWAY_JAVA_HOME`: the JDK the gateway runs on, e.g. `/usr/lib/jvm/temurin-25-jdk-amd64`.
  Default: the gateway VM's `java` (Temurin 21).

## GC under a fixed load

`run-gc-matrix.sh [scenario]` runs a scenario (default `fullproxy`) at a fixed rate once for each
combination of JDK, collector and heap size and prints one `SUMMARY` line per run. It installs
missing Temurin versions on the gateway VM. Defaults: `RATE=1000`, `WARMUP_SECONDS=10`,
`MEASURE_SECONDS=60`, `JDKS="21 25"`, `GCS="g1 zgc shen"` (generational ZGC; Shenandoah in its
generational mode from JDK 25 on), `HEAPS="default 100m 250m 4g"` (`-Xmx`). A full matrix takes about
75 minutes; the results of 2026-10-21 are in [gc-typical-load/RESULTS.md](gc-typical-load/RESULTS.md).

## Configuration

All scripts read sensible defaults but can be overridden via environment variables, e.g.:

```sh
RG=my-rg LOCATION=westeurope BACKEND_SIZE=Standard_D8s_v5 ./provision.sh
```

See the top of each script for the variables it accepts.

## Notes on reproducing results

- VM performance on shared cloud hardware varies run to run; don't expect to reproduce exact RPS
  figures, only the relative ordering and rough magnitude.
- The per-scenario concurrency defaults and the JVM heap/GC flags (`GC_OPTS` in `run-scenario.sh`) were
  found empirically to work best for the current default VM sizes -- they are very unlikely to be
  optimal for different sizes. If you change `*_SIZE`, re-sweep concurrency (e.g.
  64/100/150/220/300) before trusting a single number.
- `client-libs/`, `conf/resolved/*.xml`, and `certs/` are regenerated by `deploy-and-run.sh` on
  every run and are gitignored -- nothing under this directory vendors a library or bakes in an
  IP address (the self-signed TLS certs in `certs/` are the one exception, needed so the
  gateway's target-side TLS can validate the backend's SAN; they're still never committed).
