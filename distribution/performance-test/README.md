# Membrane Performance Test (3-VM, real network)

Measures the Membrane API Gateway's own performance in isolation, using 3 separate machines
(client, gateway, backend) connected over a real network, instead of the loopback, single-JVM
setup used by `distribution/src/test/java/com/predic8/membrane/load/LoadTester.java`. Loopback
numbers conflate raw throughput with CPU contention between roles sharing one machine's cores;
running each role on its own machine isolates what the gateway itself adds to a request.

Four scenarios are included. The first three are of increasing realism (`shortcircuit` -->
`fullproxy` --> `openapi-validation`); `rate-limit-basic-auth` is a sibling of
`openapi-validation`, built on `fullproxy`'s topology/body/backlog plus two added gateway tasks,
so its RPS can be subtracted directly against `fullproxy`'s to isolate those tasks' combined cost:

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

For `rate-limit-basic-auth`, a run is only valid if it reports `ERR=0` -- a nonzero `ERR` means
requests were actually being rejected (401/429), and a short-circuited rejection is *cheaper* than
a proxied request, so a bad run reports an RPS number that looks better, not worse.

See [SAMPLE-RESULTS.md](SAMPLE-RESULTS.md) for an example run and how to interpret the numbers,
and [TESTED-CONFIGURATIONS.md](TESTED-CONFIGURATIONS.md) for the full catalog of every hardware/
JVM/heap/concurrency combination measured so far.

## Requirements

- An Azure subscription and the [`az` CLI](https://learn.microsoft.com/cli/azure/install-azure-cli),
  logged in (`az login`) with the right subscription selected (`az account set --subscription <id>`).
- A local Membrane build environment (this repo, Maven) to build the distribution zip.
- `ssh`/`scp` on your machine; VMs are created with `--generate-ssh-keys` (uses/creates
  `~/.ssh/id_rsa` if you don't already have a key).

This test costs real money while the VMs are running (`Standard_FX16mds_v2` gateway,
`Standard_F16as_v7` backend, `Standard_F16as_v6` client by default -- see
[TESTED-CONFIGURATIONS.md](TESTED-CONFIGURATIONS.md) for why these particular sizes) --
**always run `teardown.sh` when done.** A full run of all scenarios takes well under an
hour end to end.

## Quickstart

```sh
cd distribution/performance-test

./provision.sh          # creates the resource group, VNet, NSG, and 3 VMs
./deploy-and-run.sh      # builds the distribution zip, ships everything, starts backend

./run-scenario.sh shortcircuit
./run-scenario.sh fullproxy
./run-scenario.sh openapi-validation
./run-scenario.sh rate-limit-basic-auth

./teardown.sh            # deletes everything -- do this when you're done
```

Each `run-scenario.sh` invocation restarts the gateway with that scenario's config, samples CPU
on all three VMs during the run, and prints RPS/OK/ERR plus a CPU-busy summary per role. Re-run
it as many times as you like (e.g. at different concurrency levels: `./run-scenario.sh fullproxy
150`) without re-running `deploy-and-run.sh` in between.

After updating these scripts, rerun `deploy-and-run.sh` to install the client and CPU sampler
and record the deployed gateway version. Redeploys stop the old backend and replace the client's
JAR directory; previous JARs are retained under `~/client-libs-backup.*` on the client VM.

CPU sampling reads Linux `/proc/stat` once per second and reports only complete intervals inside
the client's measured phase, including idle intervals. Startup and warmup are excluded. This
requires synchronized clocks on all three VMs (check `timedatectl status` before benchmarking).
Runs too short to contain a full sampling interval report no CPU result. The historical CPU
figures in the results documents used the older activity-filtered method and are not directly
comparable to new runs.

## Configuration

All scripts read sensible defaults but can be overridden via environment variables, e.g.:

```sh
RG=my-rg LOCATION=westeurope BACKEND_SIZE=Standard_D8s_v5 ./provision.sh
```

See the top of each script for the variables it accepts.

## Notes on reproducing results

- VM performance on shared cloud hardware varies run to run; don't expect to reproduce exact RPS
  figures, only the relative ordering and rough magnitude.
- The concurrency default (175) and the JVM heap/GC flags (`JAVA_OPTS` in `run-scenario.sh`) were
  found empirically to work best for the current default VM sizes -- they are very unlikely to be
  optimal for different sizes. If you change `*_SIZE`, re-sweep concurrency (e.g.
  50/125/175/220/300) before trusting a single number.
- `client-libs/` and `conf/resolved/*.xml` are regenerated by `deploy-and-run.sh` on every run
  and are gitignored -- nothing under this directory vendors a library or bakes in an IP address.
- [TESTED-CONFIGURATIONS.md](TESTED-CONFIGURATIONS.md) catalogs every hardware/JVM/heap/backlog/
  concurrency combination measured so far, with full CPU-busy detail per role -- check it before
  re-running a sweep that may already have been done, and add to it when you try something new so
  future runs have something to compare against.
